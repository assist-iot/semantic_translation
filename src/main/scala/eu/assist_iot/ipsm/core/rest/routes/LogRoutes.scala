package eu.assist_iot.ipsm.core.rest.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, pathEndOrSingleSlash}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import ch.qos.logback.classic.LoggerContext
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.datamodel._
import eu.assist_iot.ipsm.core.rest.{JsonSupport, XMLSupport}
import org.slf4j.LoggerFactory

trait LogRoutes extends JsonSupport with XMLSupport with LazyLogging {

  def loggingRoutes: Route = {
    path("logging" / Segment) { level =>
      post {
        setLoggingLevel(level)
      }
    } ~
      path("logging") {
        get {
          getLoggingLevel
        }
      }
  }

  def getLoggingLevel: Route = pathEndOrSingleSlash {
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val level = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getEffectiveLevel
    complete(StatusCodes.OK, LoggingResponse(
      "IPSM root logging level retrieved",
      s"$level"
    ))
  }

  def setLoggingLevel(level: String): Route = {
    import ch.qos.logback.classic.{Level, LoggerContext}
    import org.slf4j.LoggerFactory
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val newLevel = level.toLowerCase match {
      case "all" => Level.ALL
      case "trace" => Level.TRACE
      case "debug" => Level.DEBUG
      case "info" => Level.INFO
      case "warn" => Level.WARN
      case "error" => Level.ERROR
      case _ => Level.OFF
    }
    val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    rootLogger.setLevel(newLevel)
    complete(StatusCodes.OK, LoggingResponse(
      "IPSM root logging level set",
      s"${rootLogger.getEffectiveLevel}"
    ))
  }

}
