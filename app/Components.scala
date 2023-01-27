import controllers._
import frontsapi.model.UpdateActions
import story_packages.metrics.CloudWatch
import play.api.ApplicationLoader.Context
import play.api.{BuiltInComponentsFromContext, Mode}
import play.api.routing.Router
import play.filters.cors.CORSFilter
import story_packages.services._
import story_packages.tools.FaciaApiIO
import story_packages.updates.{AuditingUpdates, KinesisEventSender, Reindex, UpdatesStream}
import router.Routes
import conf.{ApplicationConfiguration, Responses}
import play.api.libs.ws.ahc.AhcWSComponents
import play.filters.gzip.GzipFilter

class AppComponents(context: Context) extends BuiltInComponentsFromContext(context) with AhcWSComponents with AssetsComponents {
  val isTest = context.environment.mode == Mode.Test
  val isDev = context.environment.mode == Mode.Dev
  val config = new ApplicationConfiguration(configuration, context.environment.mode)
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

  val defaults = new DefaultsController(config, wsClient)
  val faciaProxy = new FaciaContentApiProxy(config, wsClient)
  val faciaTool = new FaciaToolController(config, frontsApi, updateActions, database, updatesStream, wsClient)
  val pandaAuth = new PandaAuthController(config, wsClient)
  val status = new StatusController
  val storyPackages = new StoryPackagesController(config, database, updatesStream, frontsApi, reindex, wsClient)
  val vanity = new VanityRedirects(config, wsClient)
  val views = new ViewsController(config, assetsManager, wsClient)

  val customGzipFilter = new GzipFilter(shouldGzip = (header, _) => !Responses.isImage(header))

  override lazy val httpFilters = Seq(
    customGzipFilter,
    new CORSFilter
  )

  val router: Router = new Routes(httpErrorHandler, status, pandaAuth, views, faciaTool, defaults, storyPackages, faciaProxy, vanity, assets)
}
