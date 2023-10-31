package eu.assist_iot.ipsm.core.alignments

import java.io.ByteArrayInputStream
import org.apache.jena.rdf.model.{Model, ModelFactory}
import eu.assist_iot.ipsm.core.config.Prefixes
import scala.io.Source
import scala.util.{Failure, Success, Try}
import scala.util.matching.Regex
import scala.xml._

import org.topbraid.jenax.util.JenaUtil
import org.apache.jena.rdf.model.Resource
import org.apache.jena.util.FileUtils
import org.topbraid.shacl.validation.ValidationUtil
import org.topbraid.shacl.util.ModelPrinter
import org.topbraid.shacl.vocabulary.SH
import scala.jdk.CollectionConverters._

import eu.assist_iot.ipsm.core.util.DosProneXmlLoader

object AlignmentValidator {

  def apply(xmlStr: String): List[String] = {
    val av = new AlignmentValidator(xmlStr)
    Try(validateWithJena(xmlStr)) match {
      case Success(_) =>
        av.validateEntity2Variables() ++
        av.validateParamVariables() ++
        List(
            av.validateSteps()
        ).flatMap(_.toList)
      case Failure(exc) =>
        List(s"Validation of alignment structure with SHACL FAILED: ${exc.getMessage}")
    }
  }

  private val shapesFile = "data/shapes_graph/shapes.ttl"

  def validateWithJena(xmlStr: String): Option[String] = {

    val dataModel = ModelFactory.createDefaultModel()
    dataModel.read(new ByteArrayInputStream(xmlStr.getBytes()), Prefixes("sripas"))

    //check with SHACL shapes graph
    val shapesModel : Model = JenaUtil.createMemoryModel()
    val shapesStr = Source.fromResource(shapesFile).getLines().mkString("\n")

    shapesModel.read(new ByteArrayInputStream(shapesStr.getBytes()), Prefixes("sripas"), FileUtils.langTurtle)

    val report : Resource = ValidationUtil.validateModel(dataModel, shapesModel, true)

    for ( s <- report.getModel.listStatements().asScala.toList ) {
      if (s.getPredicate == SH.conforms && s.getObject.asLiteral().getString.contains("false")) {
          throw new Exception(ModelPrinter.get().print(report.getModel))
      }
    }

    //check that all alignment cells are valid RDF
    Try(new Alignment(xmlStr)) match {
      case Success(_) => None
      case Failure(exc) => Some(s"Parsing alignment cells FAILED: ${exc.getMessage}")
    }

  }

}

class AlignmentValidator(xmlStr: String) {

  private val sripas = Prefixes("sripas")
  private val nodeRe = s"$sripas(node_.*)".r
  private val predRe = s"$sripas(pred_.*)".r
//  private val alignXML = XML.loadString(xmlStr)
  private val alignXML = DosProneXmlLoader.loadString(xmlStr)
  /**
    * Checks if all cells have "sripas:id" attributes and application steps use existing cell IDs
    * @return  None if the condition is satisfied, Some(error message) otherwise
    */
  private[alignments] def validateSteps(): Option[String] = {
    val steps = (alignXML \ "Alignment" \ "steps").filter(_.namespace == Prefixes("sripas"))
    //TODO: schema has to ensure that there is EXACTLY ONE <steps> element in the alignment
    val stepSeq = steps.head \ "step"

    //TODO: schema has to ensure that each <step> element has a "sripas:cell" attribute
    var attsSet = {
      for (step <- stepSeq) yield {
        (step \ ("@{" + Prefixes("sripas") + "}cell")).text
      }
    }.toSet

    if (attsSet.isEmpty || (attsSet.size==1 && attsSet.toSeq.head == "")) {
      attsSet = {
        for (step <- stepSeq) yield {
          step.attribute("cell").get.text
        }
      }.toSet
    }

    //TODO: schema has to ensure that EVERY <Cell> element has a "sripas:id" attribute
    val cells = (alignXML \ "Alignment" \ "map" \ "Cell").filter(_.namespace == Prefixes("align"))

    //TODO: This will fail, if other prefixes are used. We should use RDF parsing, not XML parsing
    val cellIdsSeq = {
      for (cell <- cells) yield {
        (cell \ ("@{" + Prefixes("rdf") + "}about")).text
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
    val cells = (alignXML \ "Alignment" \ "map" \ "Cell").filter(_.namespace == Prefixes("align"))
    val errMsgs = for (cell <- cells) yield {
      val cellId = (cell \ ("@{" + Prefixes("rdf") + "}about")).text
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
    val cells = (alignXML \ "Alignment" \ "map" \ "Cell").filter(_.namespace == Prefixes("align"))
    val errMsgs = for (cell <- cells) yield {
      val cellId = (cell \ ("@{" + Prefixes("rdf") + "}about")).text
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

  private def entityVars(cell: Node, entity: String): Set[String] = {
    (cell \ entity \\ "_").filter(_.namespace == Prefixes("align")).collect{
      case el if nodeRe.findFirstIn(el.namespace + el.label).nonEmpty =>
        el.label
      case el if predRe.findFirstIn(el.namespace + el.label).nonEmpty =>
        el.label
    }.toSet
  }

  private def nodeSeqVars(nodeSeq: NodeSeq): Set[String] = {

    def matches(el: Node, reg: Regex): Boolean = {
      val opt = el \ ("@{" + Prefixes("rdf") + "}about")
      opt.nonEmpty && reg.findFirstIn(opt.text).nonEmpty
    }

    nodeSeq.collect {
      case el if matches(el, nodeRe) =>
        (el \ ("@{" + Prefixes("rdf") + "}about")).text match {
          case nodeRe(v) => v
        }
      case el if matches(el, predRe) =>
        (el \ ("@{" + Prefixes("rdf") + "}about")).text match {
          case predRe(v) => v
        }
    }.toSet

  }

}
