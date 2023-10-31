package eu.assist_iot.ipsm.core.datamodel

import eu.assist_iot.ipsm.core.md5Hash

case class AlignmentID(name: String, version: String) {
  def toIndex: String = {
    md5Hash(s"${md5Hash(name)}:${md5Hash(version)}")
  }
}
object AlignmentID {
  val identityAlignmentID: AlignmentID = AlignmentID("","")
}
//noinspection DuplicatedCode
object AlignmentDAO {
  import IPSMModel.ctx._

  def findAlignmentById(id: AlignmentID) : Option[AlignmentData] = {
    val q = quote {
      query[AlignmentData]
        .filter{ algn => {algn.name == lift(id).name && algn.version == lift(id).version} }
    }
    run(q).headOption
  }

  def deleteAlignmentById(id: AlignmentID) : Boolean = {
    val q = quote {
      query[AlignmentData]
        .filter{ algn => {algn.name == lift(id).name && algn.version == lift(id).version} }
        .delete
    }
    run(q) match {
      case n if n > 0 => true
      case _ => false
    }
  }

  def addAlignment(alignData: AlignmentData): AlignmentData = {
    val q = quote {
      query[AlignmentData].insertValue(lift(alignData)).returningGenerated(_.id)
    }
    alignData.copy(id = run(q))
  }

  def getAlignments: Seq[AlignmentData] = {
    val q = quote {
      query[AlignmentData]
    }
    run(q)
  }

}
