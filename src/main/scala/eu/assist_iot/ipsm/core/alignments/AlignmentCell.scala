package eu.assist_iot.ipsm.core.alignments

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.log4s._

import eu.assist_iot.ipsm.core.alignments.CellFormat.CellFormat
import eu.assist_iot.ipsm.core.config.Prefixes
import org.apache.commons.io.IOUtils
import org.apache.jena.rdf.model._
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.update.UpdateAction
import org.apache.jena.vocabulary.RDF

import scala.collection.mutable.{Map => MMap, Set => MSet}
import scala.jdk.CollectionConverters._
import collection.immutable.Seq
import scala.xml.Node
import java.util.UUID.randomUUID

sealed trait CellFunctionParam {val position: Int; val id: String}
case class CellFunctionVarParam(position: Int, id: String) extends CellFunctionParam
case class CellFunctionValParam(position: Int, id: String, typ: Option[String] = None) extends CellFunctionParam

class AlignmentCell(xmlCell: Node, rdfCell: Resource, id: String, cellFormat: CellFormat, prefixMap: Map[String,String]) {
  private[this] val logger = getLogger

  val cellId: String = id

  private val datatypeBindingsMap: MMap[String, String] = MMap()
  // eventually contains only non-function-related ids
  private val datatypeBindingIds: MSet[String] = MSet()
  private val varMap: MMap[String, String] = MMap()
  private val blanknodeVars = MSet[String]()

  private val sparqlPrefixes = prefixMap.toList.map(arg => {
    s"PREFIX ${arg._1}: <${arg._2}>"
  })

  computeDatatypeInfo()
  private[alignments] val (e1Statements, e2Statements, variables) = getEntitiesInfo(rdfCell) match {
    case (e1s, e2s, vs) =>
      (e1s.map(tripletToString).mkString("\n"), e2s.map(tripletToString).mkString("\n"), vs)
  }
  private val filters = cellFilters match {
    case Nil => ""
    case filterList =>
      "\n\t" + filterList.mkString("\n\t")
  }

  private def sparqlUpdate: String = {
    s"""
       |${sparqlPrefixes.mkString("\n")}\n
       |DELETE {
       |$e1Statements
       |} INSERT {
       |$e2Statements
       |} WHERE {
       |$e1Statements$filters${bindings()}${nonFunctionDatatypeBindigs()}
       |}
     """.stripMargin
  }

  def applyCell(model: Model): Model = {
    logger.debug(s"Apply update: $sparqlUpdate")
    val copy = if (logger.isDebugEnabled) {
      logger.debug(s"Model: $model")
      ModelFactory.createDefaultModel().add(model)
    } else {
      null // scalastyle:ignore
    }
    try {
      UpdateAction.parseExecute(sparqlUpdate, model)
    } catch {
      case exc: Throwable =>
        logger.error(s"Exception in Cell application: ${exc.getMessage}")
    }
    logger.debug(s"""${
      if (model.isIsomorphicWith(copy)) {
        "Cell update with no effect on the model!\n" + sparqlUpdate
      } else {
        val outStream = new ByteArrayOutputStream
        RDFDataMgr.write(outStream, model, Lang.JSONLD)
        "Cell update applied:\n" + outStream.toString
      }
    }""")
    logger.debug(s"Translated model: $model")
    model
  }

  override def toString: String = {
    s"\n#$outputSep\n# Cell '$cellId' SPARQL update:\n#$outputSep$sparqlUpdate\n"
  }

  private def computeDatatypeInfo(): Unit = {
    for ( typing <- xmlCell \"typings" \ "typing" ) {
      val node = getAttribute(typing, Prefixes("sripas"), "about")
      val ntyp = getAttribute(typing, Prefixes("sripas"), "datatype")
      node match {
        case nodeRe(name) =>
          datatypeBindingsMap += (name -> s"BIND(STRDT(STR(?$name), <$ntyp>) AS ?${name}_typed)")
        case _ =>
      }
    }
    datatypeBindingIds ++= datatypeBindingsMap.keys
    logger.trace(s"datatypeBindingIds - BEFORE: $datatypeBindingIds")
    logger.trace(s"datatypeBindingsMap: $datatypeBindingsMap")
  }

  private def cellFilters: List[String] = {
    val filterElems = (xmlCell \ "filters" \\ "filter").filter(_.namespace == Prefixes("sripas")).toList
    val filters = filterElems.filter(n => {
      val vn = "?" + getAttribute(n, Prefixes("sripas"), "about").replace(Prefixes("sripas"), "")
      variables.contains(vn)
    })
    val varTypes = for {
      f <- filters
      varName = "?" + getAttribute(f, Prefixes("sripas"), "about").replace(Prefixes("sripas"), "")
      varType = getAttribute(f, Prefixes("sripas"), "datatype")
    } yield {
      s"FILTER( datatype($varName) = <$varType> )"
    }
    varTypes
  }

  private def bindings(): String = {
    val funBinds = functionBindings()
    if (funBinds.isEmpty) {
      ""
    } else {
      s"\n${funBinds.mkString("\n")}"
    }
  }

  private def nonFunctionDatatypeBindigs(): String = {
    logger.trace(s"datatypeBindingIds - AFTER: $datatypeBindingIds")
    if (datatypeBindingIds.isEmpty) {
      ""
    } else {
      "\n\t" + datatypeBindingIds.map(s => datatypeBindingsMap(s)).mkString("\n\t")
    }
  }

  private def functionBindings(): Seq[String] = {
    val funcs = (xmlCell \ "transformation" \\ "function").filter(_.namespace == Prefixes("sripas"))
    val bindings = for {
      fun <- funcs
      fname = functionName(fun)
      fparams = cellFunctionParams((fun \ "param").filter(_.namespace == Prefixes("sripas")))
      fresult = cellFunctionBinding((fun \ "return").filter(_.namespace == Prefixes("sripas")))
    } yield {
      (fname, fparams.map({
        case CellFunctionVarParam(_, paramId) =>
          // paramId is function-related so gets removed from datatypeBindingIds
          paramId match {
            case nodeRe(v) =>
              logger.trace(s"param: $v")
              datatypeBindingIds.remove(v)
          }
          s"${toSparqlName(paramId)}"
        case CellFunctionValParam(_, v, None) => s""""$v""""
        case CellFunctionValParam(_, v, Some(typ)) => s""""$v"^^<$typ>"""
      }), fresult)
    }
    bindings.map({
      case (fname, params, res) =>
        // res is function-related, so gets removed from datatypeBindingIds
        res match {
          case nodeRe(v) =>
            logger.trace(s"res: $v")
            datatypeBindingIds.remove(v)
        }
        generateBindings(fname, params.toList, res)
    })
  }

  private def generateBindings(fname: String, params: Seq[String], res: String): String = {
    val operators = Set("*", "+", "/", "-")
    val keys = datatypeBindingsMap.keys.toSet.map((s:String) => s"?$s")
    val pars = params.map{s =>
      if (keys.contains(s)) {
        s"${s}_typed"
      } else {
        s
      }
    }
    val appl = if (operators.contains(fname)) {
      // removing quotes from "number strings" to produce a proper (bracketed) arithmetic expression
      s"(${pars.map(s => s.replaceAll("\"","")).mkString(s" $fname ")})"
    } else {
      s"$fname(${pars.mkString(", ")})"
    }
    val parBindings = params.collect({
      case s if keys.contains(s) =>
        val key = s.replace("?","")
        s"  ${datatypeBindingsMap(key)}"
    })
    parBindings.mkString("\n") +
      s"\tBIND( $appl AS ${toSparqlName(res)})" +
      (res match {
        case nodeRe(name) =>
          // add type binding for the function result
          if (keys.contains(s"?$name")) {
            "\n  " + datatypeBindingsMap(name)
          } else {
            ""
          }
        case _ => ""
      })
  }

  private def replaceTyped(str: String): String = {
    var res = str
    for { name <- datatypeBindingsMap.keys } {
      val nodeRe = s"\\?$name".r
      res = nodeRe.replaceAllIn(res, s"?${name}_typed")
    }
    res
  }

  private def getEntitiesInfo(cell: Resource): (Triplets, Triplets, Set[String]) = {
    val entities: MMap[String, Triplets] = MMap()
    val cellProperties = cell.listProperties().asScala.toList
    var entityVars = Set[String]()
    for (prop <- cellProperties) {
      val pred = prop.getPredicate
      pred.getNameSpace + pred.getLocalName match {
        case entityRe(entityNo) =>
          val model = ModelFactory.createDefaultModel()
          val literal = prop.getObject.asLiteral().getLexicalForm
          cellFormat match {
            case CellFormat.Turtle =>
              val usedPrefs = prefixMap.keys.filter { pref =>
                literal.matches(s"(?s).*\\b$pref:.*")
              }
              val prefs = usedPrefs.map(pref => {
                s"@prefix $pref: <${prefixMap(pref)}> ."
              }).mkString("\n")
              model.read(IOUtils.toInputStream(s"$prefs\n$literal", "UTF-8"), Prefixes("sripas"), "TTL")
            case _ => // we are assuming just two cases for the time being: CellFormat.Turtle and CellFormal.RdfXml
              val rdfSurroundedLiteral = s"""
                                            |<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                                            |$literal
                                            |</rdf:RDF>
              """.stripMargin
              model.read(new ByteArrayInputStream(rdfSurroundedLiteral.getBytes()), Prefixes("sripas"))
          }
          val stmts = model.listStatements().asScala.toList
          buildVarMap(stmts)
          val tr = stmts.filter{s =>
            !(s.getPredicate == RDF.`type` && s.getObject.asResource().getNameSpace == Prefixes("sripas"))
          }
          val trans = tr.collect{
            case stmt =>
              val (s,o) = (toSparqlName(stmt.getSubject), toSparqlName(stmt.getObject))
              val (subj, obj) = {
                if (entityNo == "2") {
                  (replaceTyped(s), replaceTyped(o))
                } else {
                  (s, o)
                }
              }
              ( subj, s"<${stmt.getPredicate.toString}>", obj )
          }
          if (entityNo == "1") {
            entityVars = entityVariables(model)
          }
          entities += entityNo -> trans
        case _ =>
      }
    }
    val insertPart = entities.getOrElse("2", Nil)
      .map{ t => {
        (toBlank(t._1), toBlank(t._2), toBlank(t._3))
      }}
    (entities.getOrElse("1", Nil), insertPart, entityVars)
  }

  private def toBlank(name: String): String = {
    if (blanknodeVars.contains(name)) {
      "^\\?BN(.+)$".r.replaceFirstIn(name,"_:BN$1")
    } else {
      name
    }
  }

  private def entityVariables(model: Model): Set[String] = {
    val getVar = (s: String) => s match {
      case nodeRe(v) => Some(s"?$v")
      case _ => None
    }
    val ss = model.listStatements().asScala.toSet
    ss flatMap { stmt =>
      val subj = stmt.getSubject.asResource()
      val pred = stmt.getPredicate.asResource()
      val obj = stmt.getObject

      (if (!subj.isAnon) {
        getVar(subj.getURI)
      } else {
        None
      }).toSet ++
      getVar(pred.getURI).toSet ++
      (if (obj.isResource && !obj.isAnon) {
        getVar(obj.asResource().getURI)
      } else {
        None
      }).toSet

    }
  }

  private def buildVarMap(stmts: List[Statement]): Unit = {
    for (s <- stmts) {
      if (s.getSubject.isAnon && s.getPredicate == RDF.`type` && s.getObject.isURIResource) {
        (s.getSubject.toString, s.getObject.toString) match {
          case (anonName, nodeRe(varName)) =>
            varMap += anonName -> varName
          case _ =>
        }
      }
    }
  }

  private def toSparqlName(node: RDFNode): String = node match {
    case n if n.isAnon =>
      logger.debug(s"ANON: $node")
      val resName = n.toString
      if (!varMap.isDefinedAt(resName)) {
        val id = randomUUID().toString.replace("-", "_")
        val bnVarName = s"BN_$id"
        blanknodeVars += s"?$bnVarName"
        varMap += resName -> bnVarName
      }
      s"?${varMap(resName)}"
    case uri if uri.isURIResource =>
      logger.debug(s"URI: $node")
      uri.toString match {
        case nodeRe(v) => s"?$v"
        case uriStr =>
          s"<$uriStr>"
      }
    case literal =>
      logger.debug(s"LITERAL: $node")
      val str = literal.toString
      str match {
        case datatypeRe(v, t) =>
          s""""$v"^^<$t>"""
        case _ =>
          s""""$str""""
      }
  }

  private def toSparqlName(resource: Resource): String = resource match {
    case res if res.isAnon =>
      val resName = res.toString
      if (!varMap.isDefinedAt(resName)) {
        val id = randomUUID().toString.replace("-", "_")
        val bnVarName = s"BN_$id"
        blanknodeVars += s"?$bnVarName"
        varMap += resName -> bnVarName
      }
      s"?${varMap(resName)}"
    case uri =>
      uri.toString match {
        case nodeRe(v) => s"?$v"
        case uriStr =>
          s"<$uriStr>"
      }
  }

  private def toSparqlName(name: String): String = {
    name match {
      case nodeRe(identifier) => s"?$identifier"
      case _ => s"<$name>"
    }
  }

}
