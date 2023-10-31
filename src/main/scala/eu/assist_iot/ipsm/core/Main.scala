package eu.assist_iot.ipsm.core

import akka.http.scaladsl.Http
import akka.actor.{ActorSystem, Props}
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.alignments.AlignmentsManager
import eu.assist_iot.ipsm.core.channels.ChannelManager
import eu.assist_iot.ipsm.core.config.AppConfig
import eu.assist_iot.ipsm.core.datamodel.IPSMModel
import eu.assist_iot.ipsm.core.rest.RestApi

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContextExecutor

object Main extends App with RestApi with LazyLogging {
  implicit val system: ActorSystem = ActorSystem("system")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  if (!AppConfig.valid()) {
    logger.error("Shutting down IPSM")
    system.terminate()
  } else {
    IPSMModel.createDbConnection onComplete {
      case Success(dbCtx) =>
        logger.info(s"Database connection successful: $dbCtx")
        ChannelManager.loadPredefChannels(system)
        AlignmentsManager.loadPredefAlignments()
        Http().newServerAt(interface = AppConfig.httpHost, port = AppConfig.httpPort).bind(api) onComplete {
          case Success(binding) =>
            logger.info(s"REST interface bound to http:/${binding.localAddress}")
            system.actorOf(Props(new Reaper(IPSMModel.ctx, binding)), name = "reaper")
          case Failure(exc) =>
            logger.error(s"REST interface could not bind to ${AppConfig.httpHost}:${AppConfig.httpPort} -> ${exc.getMessage}")
            logger.error("Shutting down SemTrans")
            system.terminate()
        }
      case Failure(ex) =>
        logger.error(s"Configuration database connection failed: ${ex.getMessage}")
        logger.error("Shutting down SemTrans")
        system.terminate()
    }
  }

}
