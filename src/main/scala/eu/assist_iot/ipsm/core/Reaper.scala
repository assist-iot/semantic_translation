package eu.assist_iot.ipsm.core

import akka.actor.Actor
import akka.actor.typed.Terminated
import akka.http.scaladsl.Http.ServerBinding
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.datamodel.IPSMModel

import scala.concurrent.ExecutionContext.Implicits.global

private object Reaper {
  case object AllDone
}
private class Reaper(ctx: IPSMModel.DbContext, binding: ServerBinding) extends Actor with LazyLogging {

  import Reaper._
  import scala.util.{Success, Failure}
  def receive: Receive = {
    case AllDone =>
      Thread.sleep(5000L)
      logger.info(s"Shutting down SemTrans HTTP interface")
      binding
        .unbind()
        .onComplete {
          case Success(_) =>
            context.system.terminate() onComplete  {
              case Success(_) =>
                logger.info(s"SemTrans shutdown procedure completed successfully")
              case Failure(exc) =>
                logger.error(s"Problem while shutting down SemTrans: ${exc.getMessage}")
            }
          case Failure(exc) =>
            logger.error(s"Problem while shutting down SemTrans: ${exc.getMessage}")
        }
  }

}
