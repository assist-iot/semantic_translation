package eu.assist_iot.ipsm.core.channels

import akka.stream.Materializer
import akka.{actor => classic}
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.datamodel.{Channel, ChannelConfig, ChannelDAO}

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait ChannelManagementOps extends LazyLogging {
  import collection.mutable.{Map => MMap}
  import scala.concurrent.ExecutionContext.Implicits.global

  val channels: MMap[Int, (Materializer, Channel)] = MMap()

  def loadPredefChannels(system: classic.ActorSystem): Unit = {
    val channels: Seq[Channel] = ChannelDAO.getChannels
    channels.foreach {
      chan => loadPredef(chan, system)
    }
    val noOfChannels = channels.length
    logger.info(s"Loaded $noOfChannels channel${if (noOfChannels != 1) "s" else ""} from the database.")
  }

  private def loadPredef(chanData: Channel, system: classic.ActorSystem): Unit = {
    val cgroup = chanData.consumerGroup.strip()
    val chanConf = ChannelConfig(
      chanData.chanType,
      chanData.source,
      chanData.inpAlignmentName,
      chanData.inpAlignmentVersion,
      chanData.outAlignmentName,
      chanData.outAlignmentVersion,
      chanData.sink,
      if (cgroup.isEmpty) None else Some(cgroup),
      Some(chanData.parallelism)
    )
    val saved: Future[Materializer] = ChannelManager.materializeChannel(chanData.uuid, chanConf, system)
    saved.andThen {
      case Success(done) =>
        logger.info(s"Channel “${chanData.id}” of type “${chanData.chanType}” configured and materialized successfully.")
        channels(chanData.id) = (done, chanData)
      case Failure(exc) =>
        logger.error(s"Configuring/materializing channel ”${chanData.id}” failed: ${exc.getMessage}.")
    }
  }

  def checkIfExists(sink: String): Boolean = {
    channels.values.exists(conf => conf._2.sink == sink)
  }

  def createChannel(mat: Materializer, chanConf: ChannelConfig, chanUUID: String): Channel = {
    val chan = ChannelDAO.addChannel(chanConf, chanUUID)
    channels(chan.id) = (mat, chan)
    chan
  }

  def deleteChannel(id: Int): Boolean = {
    val res = ChannelDAO.deleteChannelById(id)
    if (res) {
      channels(id)._1.shutdown()
      if (channels(id)._1.isShutdown) {
        channels.remove(id)
      }
    }
    res
  }

}
