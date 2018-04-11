import controllers._
import frontsapi.model.UpdateActions
import story_packages.metrics.CloudWatch
import play.api.ApplicationLoader.Context
import play.api.inject.{Injector, NewInstanceInjector, SimpleInjector}
import play.api.{BuiltInComponentsFromContext, Mode}
import play.api.libs.ws.ning.NingWSComponents
import play.api.routing.Router
import play.filters.cors.CORSFilter
import story_packages.services._
import story_packages.tools.FaciaApiIO
import story_packages.updates.{AuditingUpdates, KinesisEventSender, Reindex, UpdatesStream}
import router.Routes
import conf.{ApplicationConfiguration, CustomGzipFilter}

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
  val reindex = new Reindex(dynamoReindexJobs, database, frontsApi, kinesisEventSender, actorSystem.scheduler)
  val cloudwatch = new CloudWatch(config, awsEndpoints)
  var assetsManager = new AssetsManager(config, isDev)

  val defaults = new DefaultsController(config)
  val faciaProxy = new FaciaContentApiProxy(wsApi, config)
  val faciaTool = new FaciaToolController(config, frontsApi, updateActions, database, updatesStream)
  val pandaAuth = new PandaAuthController(config)
  val status = new StatusController
  val storyPackages = new StoryPackagesController(config, database, updatesStream, frontsApi, reindex, wsApi)
  val uncachedAssets = new UncachedAssets
  val vanity = new VanityRedirects(config)
  val views = new ViewsController(config, assetsManager, isDev)

  override lazy val injector: Injector =
    new SimpleInjector(NewInstanceInjector) + router + crypto + httpConfiguration + tempFileCreator + wsApi + wsClient

  override lazy val httpFilters = Seq(
    new CustomGzipFilter,
    new CORSFilter
  )

  val router: Router = new Routes(httpErrorHandler, status, pandaAuth, uncachedAssets, views, faciaTool, defaults, storyPackages, faciaProxy, vanity)
}
