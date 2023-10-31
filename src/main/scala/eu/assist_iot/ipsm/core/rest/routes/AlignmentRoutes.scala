package eu.assist_iot.ipsm.core.rest.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core._
import eu.assist_iot.ipsm.core.alignments._
import eu.assist_iot.ipsm.core.datamodel._
import eu.assist_iot.ipsm.core.rest.{JsonSupport, XMLSupport}
import spray.json.DefaultJsonProtocol._

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.XML


trait AlignmentRoutes extends JsonSupport with XMLSupport with LazyLogging {

  implicit val system: ActorSystem
  implicit val executionContext: ExecutionContext

  def alignmentRoutes: Route = {
    path("alignments" ~ Slash.?) {
      get {
        listAlignments()
      } ~
        post {
          entity(as[AlignmentConfig]) { conf =>
            addAlignment(conf)
          }
        }
    } ~
      path("validate" ~ Slash.?) {
        post {
          entity(as[AlignmentConfig]) { conf =>
            validateAlignment(conf)
          }
        }
      } ~
      path("convert" ~ Slash.?) {
        post {
          entity(as[AlignmentConfig]) { conf =>
            convertAlignment(conf)
          }
        }
      } ~
      //TODO: Use Segment, instead of "TTL" if we ever offer conversion to cell formats other than TTL
      path("convert" / "TTL") {
        post {
          entity(as[AlignmentConfig]) { conf =>
            convertAlignmentCellsToTTL(conf)
          }
        }
      } ~
      path("alignments" / Segment / Segment) { (name, version) =>
        get {
          getAlignment(AlignmentID(name, version))
        } ~
          delete {
            deleteAlignment(AlignmentID(name, version))
          }
      } ~
      path("translation" ~ Slash.?) {
        post {
          entity(as[TranslationData]) { data =>
            translate(data)
          }
        }
      }
  }

  private def listAlignments(): Route = {
    val aligns: Seq[AlignmentData] = AlignmentsManager.getAlignmentsData
    complete(StatusCodes.OK, for (alignData <- aligns) yield {
      AlignmentInfo(
        alignData.id,
        alignData.date,
        alignData.name,
        alignData.sourceOntologyURI,
        alignData.targetOntologyURI,
        alignData.version,
        alignData.creator,
        alignData.description,
        s"[${alignData.name}] [${alignData.version}] : ${alignData.sourceOntologyURI} -> ${alignData.targetOntologyURI}"
      )
    })
  }

  private def alignmentDefinitionsMatch(xSrc: String, ySrc: String): Boolean = {
    //TODO: eventually some kind of "Jena-based RDF source normalization" should be done here
    md5Hash(XML.loadString(xSrc).toString) == md5Hash(XML.loadString(ySrc).toString)
  }

  private def addAlignment(alignConf: AlignmentConfig): Route = {
    logger.debug(s"Request to add alignment with with ${alignConf.alignmentID} received.")
    val errList = AlignmentValidator(alignConf.xmlSource)
    if (errList.nonEmpty) {
      validationError(alignConf, errList)
    } else {
      // new alignment is syntactically valid, we need to see if it's not a "duplicate"
      val duplicateCheckOpt = AlignmentsManager.findAlignmentDataById(alignConf.alignmentID)
      duplicateCheckOpt match {
        case None =>
          val newAlignmentData = AlignmentData(
            -1,
            Instant.now(),
            alignConf.name,
            alignConf.sourceOntologyURI,
            alignConf.targetOntologyURI,
            alignConf.version,
            alignConf.creator,
            alignConf.description,
            alignConf.xmlSource
          )
          val inserted = AlignmentsManager.addAlignment(newAlignmentData)
          complete(
            StatusCodes.Created,
            SuccessResponse(message = s"Alignment with ${inserted.alignmentID} uploaded successfully.")
          )
        case Some(alignData) =>
          // there exists an alignment with the same AlignmentID(name, version)
          val eSrc = alignData.xmlSource
          val nSrc = alignConf.xmlSource
          if (alignmentDefinitionsMatch(eSrc, nSrc)) {
            complete(
              StatusCodes.AlreadyReported,
              SuccessResponse(message = s"Alignment with ${alignConf.alignmentID} already exists.")
            )
          } else {
            complete(
              StatusCodes.Conflict,
              ErrorResponse(message = s"Alignment with ${alignConf.alignmentID} already exists but has a different content.")
            )
          }
      }
    }
  }

  private def deleteAlignment(id: AlignmentID): Route = {
    val dependentChannels = ChannelDAO.countChannelsByAlignmentId(id.name, id.version)
    if (dependentChannels == 0) {
      val res: Option[AlignmentData] = AlignmentsManager.findAlignmentDataById(id)
      res match {
        case Some(_) =>
          val rowDeleted = AlignmentsManager.deleteAlignmentById(id)
          if (rowDeleted) {
            complete(
              StatusCodes.OK,
              SuccessResponse(s"Alignment with $id successfully deleted")
            )
          } else {
            complete(
              StatusCodes.BadRequest,
              ErrorResponse(s"Alignment with $id not found")
            )
          }
        case None =>
          complete(
            StatusCodes.BadRequest,
            ErrorResponse(s"Alignment with $id not found")
          )
      }
    } else {
      complete(
        StatusCodes.BadRequest,
        ErrorResponse(s"Alignment with $id is used by an active channel")
      )
    }
  }

  private def convertAlignment(conf: AlignmentConfig): Route = {
    logger.debug("Request to convert alignment to RDF/XML received")
    val errList = AlignmentValidatorXML(conf.xmlSource)
    if (errList.nonEmpty) {
      complete(
        StatusCodes.BadRequest,
        ErrorResponse(s"Error validating XML alignment with ${errList.mkString("\n\t- ")}")
      )
    } else {
      val f: Future[String] = Future {
        AlignmentConverter.convertFormatToRDFXML(conf.xmlSource)
      }
      onComplete(f) {
        case Success(rdfXmlStr) =>
          val ad = AlignmentData(-1, null, "", "", "", "", "", "", rdfXmlStr) // scalastyle:ignore
          complete(StatusCodes.Created, ad)
        case Failure(exc) =>
          complete(
            StatusCodes.InternalServerError,
            ErrorResponse(s"Converting alignment format to RDF/XML FAILED: ${exc.getMessage}")
          )
      }
    }
  }

  private def convertAlignmentCellsToTTL(conf: AlignmentConfig): Route = {
    val f: Future[String] = Future {
      AlignmentConverter.convertCellFormat(
        new Alignment(conf.xmlSource),
        CellFormat.Turtle)
    }
    onComplete(f) {
      case Success(rdfXmlStr) =>
        val ad = AlignmentData(-1, null, "", "", "", "", "", "", rdfXmlStr) // scalastyle:ignore
        complete(StatusCodes.Created, ad)
      case Failure(exc) =>
        complete(
          StatusCodes.InternalServerError,
          ErrorResponse(s"Converting alignment cell format to TTL FAILED: ${exc.getMessage}")
        )
    }
  }

  private def getAlignment(id: AlignmentID): Route = {
    val res: Option[AlignmentData] = AlignmentsManager.findAlignmentDataById(id)
    res match {
      case Some(align) =>
        complete(align)
      case _ =>
        complete(
          StatusCodes.BadRequest,
          ErrorResponse(s"Alignment with $id not found")
        )
    }
  }

  private def translate(data: TranslationData): Route = {
    val alignOpts = data.alignIDs.map(id => AlignmentsManager.getAlignment(id))
    logger.debug(s"Alignment option list: $alignOpts")
    if (alignOpts.contains(None)) {
      val errId = data.alignIDs.zip(alignOpts).collectFirst({ case n if n._2.isEmpty => n._1 }).get
      completeWithError(s"Cannot instantiate alignment with $errId")
    } else {
      Try(new Message(data.graphStr)) match {
        case Success(message) =>
          Try(message.getPayload) match {
            case Success(inpGraph) =>
              Try(alignOpts.foldLeft(inpGraph)((graph, alOpt) => {
                alOpt.get.execute(graph)
              })) match {
                case Success(resGraph) =>
                  message.setPayload(resGraph)
                  complete(
                    StatusCodes.OK,
                    TranslationResponse(
                      "Message translation successful",
                      message.serialize
                    )
                  )
                case Failure(exc) =>
                  completeWithError("Exception applying alignment", exc)
              }
            case Failure(exc) => exc match {
              case _: EmptyPayloadException =>
                logger.trace("Message with empty payload received")
                complete(
                  StatusCodes.OK,
                  TranslationResponse(
                    "Message with empty payload received - no translation applied",
                    data.graphStr
                  )
                )
              case _ =>
                completeWithError("Exception extracting message payload", exc)
            }
          }
        case Failure(exc) =>
          completeWithError("Exception parsing message", exc)
      }

    }
  }

  private def completeWithError(msg: String, exc: Throwable = null): Route = { // scalastyle:ignore
    val errMsg = if (exc != null) {
      s"$msg: ${exc.getMessage}"
    } else {
      msg
    }
    logger.error(errMsg)
    complete(
      StatusCodes.BadRequest,
      TranslationResponse(errMsg, "")
    )
  }

  private def validateAlignment(alignConf: AlignmentConfig): Route = {
    logger.debug(s"Request to validate alignment with with ${alignConf.alignmentID} received")
    val errList = AlignmentValidator(alignConf.xmlSource)
    if (errList.nonEmpty) {
      validationError(alignConf, errList)
    } else {
      complete(
        StatusCodes.Created,
        SuccessResponse(message = s"Alignment with ${alignConf.alignmentID} validated successfully")
      )
    }
  }

  private def validationError(alignConf: AlignmentConfig, errList: List[String]): Route = {
    val s = "\n\t- "
    complete(
      StatusCodes.BadRequest,
      ErrorResponse(s"Error validating alignment with ${alignConf.alignmentID}:$s ${errList.mkString(s)}")
    )
  }

}
