package eu.assist_iot.ipsm.core.alignments

import java.io.{ByteArrayInputStream, InputStream}
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants

import eu.assist_iot.ipsm.core.config.Prefixes

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex
import scala.xml._
import org.apache.jena.rdf.model._

object AlignmentValidatorXML {

  def apply(xmlStr: String): List[String] = {
    val av = new AlignmentValidatorXML(xmlStr)
    Try(validateWithXSD(xmlStr)) match {
      case Success(_) =>
        av.validateCells() ++
        av.validateEntity2Variables() ++
        av.validateParamVariables() ++
        List(
            av.validateSteps()
        ).flatMap(_.toList)
      case Failure(exc) =>
        List(s"Validation against the Alignment Schema FAILED: ${exc.getMessage}")
    }
  }

  private val xsdFile = "schema/alignment.xsd"

  def validateWithXSD(xmlStr: String): Boolean = {
    val is :InputStream = new ByteArrayInputStream(xmlStr.getBytes)
    val xmlSource : BufferedSource = Source.fromInputStream(is)
    val xsdReader = Source.fromResource(xsdFile).reader()
    val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val schema = factory.newSchema(new StreamSource(xsdReader))
    val validator = schema.newValidator()
    validator.validate(new StreamSource(xmlSource.reader()))
    true
  }

}

class AlignmentValidatorXML(xmlStr: String) {

  private val sripas = Prefixes("sripas")
  private val nodeRe = s"$sripas(node_.*)".r
  private val predRe = s"$sripas(pred_.*)".r
  private val alignXML = XML.loadString(xmlStr)

  /**
    * Checks if all cells have "sripas:id" attributes and application steps use existing cell IDs
    * @return  None if the condition is satisfied, Some(error message) otherwise
    */
  private[alignments] def validateSteps(): Option[String] = {
    val steps = alignXML \ "steps"
    //TODO: schema has to ensure that there is EXACTLY ONE <steps> element in the alignment
    val stepSeq = steps.head \ "step"

    //TODO: schema has to ensure that each <step> element has a "sripas:cell" attribute
    val attsSet = {
      for (step <- stepSeq) yield {
        step.attributes.get("cell").get.head.text
      }
    }.toSet
    //TODO: schema has to ensure that EVERY <Cell> element has a "sripas:id" attribute
    val cells = alignXML \ "map" \ "Cell"
    val cellIdsSeq = {
      for (cell <- cells) yield {
        cell.attributes.get("id").get.head.text
      }
    }
    val foundIds = cellIdsSeq.toSet
    if (cellIdsSeq.size != foundIds.size) {
      Some("Cells' \"ID\" attributes have non-unique values")
    } else {
      if (!attsSet.subsetOf(foundIds)) {
        Some("One or more of the alignment steps use non-existent cell IDs")
      } else {
        None
      }
    }
  }

  /**
    * Check if variable names in ENTITY2 are in sum of variable name sets for ENTITY1 and transformation RETURNs
    *
    * @return None if the condition is satisfied, Some(error message) otherwise
    */
  private[alignments] def validateEntity2Variables() : List[String] = {
    val cells = alignXML \ "map" \ "Cell"
    val errMsgs = for (cell <- cells) yield {
      val cellId = cell.attributes.get("id").get.head.text
      val e1Vars = entityVars(cell, "entity1")
      val e2Vars = entityVars(cell, "entity2")
      val resVars = nodeSeqVars(cell \ "transformation" \\ "function" \ "return")
      if (!e2Vars.subsetOf(e1Vars ++ resVars)) {
        Some(s"""Unbound variables in "$cellId" – ${e2Vars -- (e1Vars ++ resVars)}.""")
      } else {
        None
      }
    }
    errMsgs.flatMap(_.toList).toList
  }

  /**
    * Check if variable names in transformation PARAMs are contained in ENTITY1 or RETURNs of other functions
    * @return None if the condition is satisfied, Some(error message) otherwise
    */
  private[alignments] def validateParamVariables() : List[String] = {
    val cells = alignXML \ "map" \ "Cell"
    val errMsgs = for (cell <- cells) yield {
      val cellId = cell.attributes.get("id").get.head.text
      val e1Vars = entityVars(cell, "entity1")
      val returnVars = nodeSeqVars(cell \ "transformation" \\ "function" \ "return")
      val paramVars = nodeSeqVars(cell \ "transformation" \\ "function" \ "param")
      if (!paramVars.subsetOf(e1Vars ++ returnVars)) {
        Some(s"""Unbound transformation parameters in "$cellId" – ${paramVars -- e1Vars}.""")
      } else {
        None
      }
    }

    errMsgs.flatMap(_.toList).toList
  }

  /**
    * Check if content of ENTITY1 and ENTITY2 are valid RDF
    *
    * @return None if the condition is satisfied, Some(error message) otherwise
    */
  private[alignments] def validateCells() : List[String] = {

    val cellNodes = alignXML \ "map" \ "Cell"

    val errMsgs = for (c <- cellNodes) yield {
      val cellId = c.attributes.get("id").get.head.text

      val entity1 = (c \ "entity1").head
      val entity2 = (c \ "entity2").head

      val rdfXml1 = s"""|<?xml version="1.0"?>
                        |<!DOCTYPE rdf:RDF [
                        |    <!ENTITY sripas "http://www.inter-iot.eu/sripas#">
                        |    <!ENTITY rdfs "http://www.w3.org/2000/01/rdf-schema#" >
                        |    <!ENTITY rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#" >
                        |    <!ENTITY base "http://www.inter-iot.eu/sripas#" >
                        |]>
                        |<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                        |${(entity1 \ "_").map(e => e.toString).mkString(" ")}

                        |</rdf:RDF>""".stripMargin

      val rdfXml2 = s"""|<?xml version="1.0"?>
                        |<!DOCTYPE rdf:RDF [
                        |    <!ENTITY sripas "http://www.inter-iot.eu/sripas#">
                        |    <!ENTITY rdfs "http://www.w3.org/2000/01/rdf-schema#" >
                        |    <!ENTITY rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#" >
                        |    <!ENTITY base "http://www.inter-iot.eu/sripas#" >
                        |]>
                        |<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                        |${(entity2 \ "_").map(e => e.toString).mkString(" ")}
                        |</rdf:RDF>""".stripMargin

      val model = ModelFactory.createDefaultModel()
      List(
        Try(model.read(new ByteArrayInputStream(rdfXml1.getBytes()), "http://www.inter-iot.eu/sripas#")) match {
          case Success(_) => None
          case Failure(exp) => Some(s"""Invalid RDF in "$cellId" entity1: $exp""")
        },
        Try(model.read(new ByteArrayInputStream(rdfXml2.getBytes()), "http://www.inter-iot.eu/sripas#")) match {
          case Success(_) => None
          case Failure(exp) => Some(s"""Invalid RDF in "$cellId" entity2: $exp""")
        }
      ).flatMap(_.toList)
    }

    errMsgs.flatten.toList
  }

  private def entityVars(cell: Node, entity: String): Set[String] = {
    (cell \ entity \\ "_").collect{
      case el if nodeRe.findFirstIn(el.namespace + el.label).nonEmpty =>
        el.label
      case el if predRe.findFirstIn(el.namespace + el.label).nonEmpty =>
        el.label
    }.toSet
  }

  private def nodeSeqVars(nodeSeq: NodeSeq): Set[String] = {

    def matches(el: Node, reg: Regex): Boolean = {
      val opt = el.attribute("about")
      opt.nonEmpty && reg.findFirstIn(opt.get.text).nonEmpty
    }

    nodeSeq.collect {
      case el if matches(el, nodeRe) =>
        el.attribute("about").get.text match {
          case nodeRe(v) => v
        }
      case el if matches(el, predRe) =>
        el.attribute("about").get.text match {
          case predRe(v) => v
        }
    }.toSet

  }

}
