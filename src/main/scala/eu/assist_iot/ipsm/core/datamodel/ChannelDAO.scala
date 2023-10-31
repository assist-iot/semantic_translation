package eu.assist_iot.ipsm.core.datamodel

import com.typesafe.scalalogging.LazyLogging

object ChannelDAO extends LazyLogging {
  import IPSMModel.ctx._

  def findChannelById(id: Int): Option[Channel] = {
    val q = quote {
      query[Channel].filter{ _.id == lift(id) }
    }
    run(q).headOption
  }

  def countChannelsByAlignmentId(name: String, version: String) : Int = {
    val q = quote {
      query[Channel].filter { chan =>
        (
          (chan.inpAlignmentName == lift(name) && chan.inpAlignmentVersion == lift(version))
          ||
          (chan.outAlignmentName == lift(name) && chan.outAlignmentVersion == lift(version))
        )
      }
    }
    run(q).distinct.length
  }

  def deleteChannelById(id: Int) : Boolean = {
    val q = quote {
      query[Channel].filter{ _.id == lift(id) }.delete
    }
    run(q) match {
      case n if n > 0 => true
      case _ => false
    }
  }

  def addChannel(channelConf: ChannelConfig, chanUUID: String): Channel = {
    val newChannel = Channel(
      0,
      channelConf.chanType,
      channelConf.source,
      channelConf.inpAlignmentName,
      channelConf.inpAlignmentVersion,
      channelConf.outAlignmentName,
      channelConf.outAlignmentVersion,
      channelConf.sink,
      channelConf.optConsumerGroup.getOrElse("").strip(),
      channelConf.optParallelism.getOrElse(1),
      chanUUID
    )
    val q = quote {
      query[Channel].insertValue(lift(newChannel)).returningGenerated(_.id)
    }
    val genId = run(q)
    newChannel.copy(id = genId)
  }

  def getChannels: Seq[Channel] = {
    run(quote(query[Channel]))
  }

}
