package eu.assist_iot.ipsm.core.alignments

import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.datamodel.{AlignmentDAO, AlignmentData, AlignmentID, ChannelConfig}

object AlignmentsManager extends LazyLogging {

  import collection.mutable.{Map => MMap}

  private val alignments = MMap[String, Alignment]()

  def getAlignment(id: AlignmentID): Option[Alignment] = {
    val alignmentOpt = alignments.get(id.toIndex)
    if (alignmentOpt.isDefined) {
      alignmentOpt
    } else {
      val alignmentOpt = AlignmentDAO.findAlignmentById(id)
      alignmentOpt match {
        case Some(alignData) =>
          val index = alignData.alignmentID.toIndex
          alignments += (index -> new Alignment(alignData.xmlSource))
          alignments.get(index)
        case _ =>
            logger.error(s"Alignment with $id not found")
            None
      }
    }
  }

  def addAlignment(align: AlignmentData): AlignmentData = {
    val res = AlignmentDAO.addAlignment(align)
    alignments += (res.alignmentID.toIndex ->  new Alignment(res.xmlSource))
    res
  }

  def getAlignmentsData: Seq[AlignmentData] = {
    AlignmentDAO.getAlignments
  }

  def findAlignmentDataById(id: AlignmentID): Option[AlignmentData] = {
    AlignmentDAO.findAlignmentById(id)
  }

  def deleteAlignmentById(id: AlignmentID): Boolean = {
    val res = AlignmentDAO.deleteAlignmentById(id)
    if (res) {
        alignments.remove(id.toIndex)
    }
    res
  }

  def loadPredefAlignments(): Unit = {
    val als = AlignmentDAO.getAlignments
    for (align <- als) {
      alignments += (align.alignmentID.toIndex -> new Alignment(align.xmlSource))
    }
    val alsNo = als.length
    logger.info(s"Loaded $alsNo alignment${if (alsNo != 1) "s" else ""} from the database.")
  }

  def inputAlignment(chanConf: ChannelConfig): Option[Alignment] = {
    if (chanConf.inpAlignmentId == AlignmentID.identityAlignmentID) {
      None
    } else {
      Some(AlignmentsManager
        .getAlignment(chanConf.inpAlignmentId)
        .getOrElse(throw new Exception("Input alignment instantiation FAILED"))
      )
    }
  }

  def outputAlignment(chanConf: ChannelConfig): Option[Alignment] = {
    if (chanConf.outAlignmentId == AlignmentID.identityAlignmentID) {
      None
    } else {
      Some(AlignmentsManager
        .getAlignment(chanConf.outAlignmentId)
        .getOrElse(throw new Exception("Output alignment instantiation FAILED"))
      )
    }
  }

}
