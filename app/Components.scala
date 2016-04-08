import conf.{ApplicationConfiguration, CustomGzipFilter}
import controllers._
import frontsapi.model.UpdateActions
import metrics.CloudWatch
import play.api.ApplicationLoader.Context
import play.api.inject.{Injector, NewInstanceInjector, SimpleInjector}
import play.api.{BuiltInComponentsFromContext, Mode}
import play.api.libs.ws.ning.NingWSComponents
import play.api.routing.Router
import play.filters.cors.CORSFilter
import services._
import tools.FaciaApiIO
import updates.{AuditingUpdates, KinesisEventSender, Reindex, UpdatesStream}
import router.Routes

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with NingWSComponents {
  val isTest = context.environment.mode == Mode.Test
  val isProd = context.environment.mode == Mode.Prod
  val isDev = context.environment.mode == Mode.Dev
  val config = new ApplicationConfiguration(configuration, isProd)
  val awsEndpoints = new AwsEndpoints(config)

  val auditingUpdates = new AuditingUpdates(config)
  val kinesisEventSender = new KinesisEventSender(config)
  val frontsApi = new FrontsApi(config, awsEndpoints)
  val s3FrontsApi = new S3FrontsApi(config, isTest, awsEndpoints)
  val faciaApiIo = new FaciaApiIO(frontsApi, s3FrontsApi)
  val updateActions = new UpdateActions(faciaApiIo, frontsApi, config)
  val database = new Database(config, awsEndpoints)
  val updatesStream = new UpdatesStream(auditingUpdates, kinesisEventSender)
  val dynamoReindexJobs = new DynamoReindexJobs(config, awsEndpoints)
  val reindex = new Reindex(dynamoReindexJobs, database, frontsApi, kinesisEventSender)
  val cloudwatch = new CloudWatch(config, awsEndpoints)

  val defaults = new DefaultsController(config)
  val faciaProxy = new FaciaContentApiProxy(wsApi, config)
  val faciaTool = new FaciaToolController(config, isDev, frontsApi, updateActions, database, updatesStream)
  val pandaAuth = new PandaAuthController(config)
  val status = new StatusController
  val storyPackages = new StoryPackagesController(config, database, updatesStream, frontsApi, reindex)
  val uncachedAssets = new UncachedAssets
  val vanity = new VanityRedirects(config)

  override lazy val injector: Injector =
    new SimpleInjector(NewInstanceInjector) + router + crypto + httpConfiguration + tempFileCreator + wsApi + wsClient

  override lazy val httpFilters = Seq(
    new CustomGzipFilter,
    new CORSFilter
  )

  val router: Router = new Routes(httpErrorHandler, status, pandaAuth, uncachedAssets, faciaTool, defaults, storyPackages, faciaProxy, vanity)
}