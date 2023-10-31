package eu.assist_iot.ipsm.core.alignments

import eu.assist_iot.ipsm.core.config.Prefixes
import eu.assist_iot.ipsm.core.util.DosProneXmlLoader

import java.io.ByteArrayInputStream
import eu.assist_iot.ipsm.core.alignments.CellFormat.CellFormat
import org.apache.jena.query.{QueryExecutionFactory, QueryFactory, QuerySolution}
import org.apache.jena.rdf.model.{Model, ModelFactory, Resource}

import scala.jdk.CollectionConverters._
import scala.xml.Node


class Alignment(val xmlSource: String) {

  private[alignments] val (xmlCells, stepSeq) = getXmlAlignmentData(xmlSource)
  private[alignments] val (rdfCells, cellFormat, prefixMap) = getRdfAlignmentData(xmlSource)

  private[alignments] val alignmentCells = {
    val cells = {
      for (id <- xmlCells.keys) yield {
        id -> new AlignmentCell(xmlCells(id), rdfCells(id), id, cellFormat, prefixMap)
      }
    }.toMap
    cells
  }

  override def toString: String = {
    val cellUpdates = for (key <- alignmentCells.keys) yield {
      s"\n${alignmentCells(key).toString}\n"
    }
    cellUpdates.mkString("\n") + outputSep +
      s"""\nAlignment steps:\n$outputSep\n${stepSeq.mkString("\n")}\n$outputSep\n"""
  }

  def execute(model: Model): Model = {
    var result = model
    for (step <- stepSeq) {
      result = alignmentCells(step).applyCell(result)
    }
    result
  }

  private def getXmlAlignmentData(alignmentSrc: String): (Map[String, Node], Seq[String]) = {
//    val alignmentXML = XML.loadString(alignmentSrc)
    val alignmentXML = DosProneXmlLoader.loadString(alignmentSrc)
    val cellNodesXML = (alignmentXML \ "Alignment" \ "map" \ "Cell").filter(_.namespace == Prefixes("align"))
    val cellsXML = {
      for (c <- cellNodesXML) yield {
        val id = cellNameRE.replaceFirstIn(getAttribute(c, Prefixes("rdf"), "about"), "$1")
        id -> c
      }
    }.toMap
    val steps = (alignmentXML \ "Alignment" \ "steps" \ "step").filter(_.namespace == Prefixes("sripas"))
    val stepSeq: Seq[String] = for (step <- steps) yield {
      getAttribute(step, Prefixes("sripas"), "cell").replace(Prefixes("sripas"), "")
    }
    (cellsXML, stepSeq)
  }

  private def getRdfAlignmentData(alignmentSrc: String): (Map[String, Resource], CellFormat, Map[String, String]) = {
    val model = ModelFactory.createDefaultModel()
    model.read(new ByteArrayInputStream(xmlSource.getBytes()), Prefixes("sripas"))
    val cellsRDF = {
      for (c <- getCells(model)) yield {
        val id = (c.getNameSpace + c.getLocalName)
          .replace(Prefixes("sripas"), "")
          .replace("http://www.inter-iot.eu/", "")
        id -> c
      }
    }.toMap
    (cellsRDF, getCellFormat(model), model.getNsPrefixMap.asScala.toMap)
  }

  private def getCells(model: Model): List[Resource] = {
    val selectQueryStr =
      """|
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX align: <http://knowledgeweb.semanticweb.org/heterogeneity/alignment#>
         |PREFIX sripas: <http://www.inter-iot.eu/sripas#>
         |SELECT ?c
         |WHERE {
         |  ?a rdf:type align:Alignment.
         |  ?a align:map ?c .
         |}
         |""".stripMargin
    val query = QueryFactory.create(selectQueryStr)
    val executionFactory = QueryExecutionFactory.create(query, model)
    val answers = executionFactory.execSelect()
    val result = answers.asScala.toList map { (sol: QuerySolution) =>
      sol.get("c").asResource()
    }
    executionFactory.close()
    result
  }

  private def getCellFormat(model: Model): CellFormat = {
    val selectQueryStr =
      """|
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX align: <http://knowledgeweb.semanticweb.org/heterogeneity/alignment#>
         |PREFIX sripas: <http://www.inter-iot.eu/sripas#>
         |SELECT ?cf
         |WHERE {
         |  ?a rdf:type align:Alignment.
         |  ?a sripas:cellFormat ?cf .
         |}
         |""".stripMargin
    val query = QueryFactory.create(selectQueryStr)
    val executionFactory = QueryExecutionFactory.create(query, model)
    val answers = executionFactory.execSelect()
    val result = answers.asScala.toSet map { (sol: QuerySolution) =>
      s"""${sol.get("cf").asResource().getLocalName}"""
    }
    executionFactory.close()
    result.headOption.getOrElse("rdfxml") match {
      case "turtle" => CellFormat.Turtle
      case _ => CellFormat.RdfXml
    }
  }

}
