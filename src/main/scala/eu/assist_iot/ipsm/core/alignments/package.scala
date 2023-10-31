package eu.assist_iot.ipsm.core

import eu.assist_iot.ipsm.core.config.Prefixes

import scala.xml.{Node, NodeSeq}

package object alignments {

  type Triplet = (String, String, String)
  type Triplets = List[Triplet]
  private[alignments] def tripletToString(t: Triplet): String = {
    s"\t${t._1} ${t._2} ${t._3} ."
  }

  object CellFormat extends Enumeration {
    type CellFormat = Value
    val Turtle, RdfXml = Value
    private val turtleURI = "http://inter-iot.eu/sripas#turtle"
    private val rdfxmlURI = "http://inter-iot.eu/sripas#rdfxml"
    implicit class CellFormatOps(cf: CellFormat) {
      def getUri: String = cf match {
        case Turtle => turtleURI
        case RdfXml => rdfxmlURI
      }
    }
    def fromUri(uri: String): Option[CellFormat] = uri match {
      case `turtleURI` => Option(Turtle)
      case `rdfxmlURI` => Option(RdfXml)
      case _ => Option.empty
    }
  }

  private[alignments] val cellNameRE = (s"""${Prefixes("sripas")}(.+)$$""").r
  private[alignments] val nodeRe = (s"""${Prefixes("sripas")}(.+)$$""").r
  private[alignments] val entityRe = (s"""${Prefixes("align")}entity(.+)$$""").r
  private[alignments] val typingsRe = (s"""${Prefixes("sripas")}typings$$""").r
  private[alignments] val datatypeRe = "^\\s*([^\\^]+)\\^\\^(\\S*)\\s*$".r

  private[alignments] val outputSep = "=" * 79

  private[alignments] def getAttribute(node: Node, prefix: String, name: String) : String = {
    var attrValue = ""
    node.attribute(prefix, name) match {
      case Some(attr) =>
        attrValue = attr.text
      case None =>
        attrValue = node.attribute(name).getOrElse(Seq()).text
    }
    attrValue
  }

  private[alignments] def cellFunctionParams(ns: NodeSeq): Seq[CellFunctionParam] = {
    val pars = for {
      p <- ns
    }  yield {
      val parOrder = p.attribute(Prefixes("sripas"), "order") match {
        case Some(_) => p.attribute(Prefixes("sripas"), "order")
        case None => p.attribute("order")
      }
      val parAbout = p.attribute(Prefixes("sripas"), "about") match {
        case Some(_) => p.attribute(Prefixes("sripas"), "about")
        case None => p.attribute("about")
      }
      val parVal = p.attribute(Prefixes("sripas"), "val") match {
        case Some(_) => p.attribute(Prefixes("sripas"), "val")
        case None => p.attribute("val")
      }
      val parTyp = p.attribute(Prefixes("sripas"), "datatype") match {
        case Some(_) => p.attribute(Prefixes("sripas"), "datatype")
        case None => p.attribute("datatype")
      }
      (parOrder, parAbout, parVal, parTyp)
    }
    pars.toList.collect({ // anonymous pattern matching function
      case (Some(order), Some(variable), _, _) =>
        CellFunctionVarParam(order.text.toInt, variable.text)
      case (Some(order), None, Some(value), Some(valTyp)) =>
        CellFunctionValParam(order.text.toInt,value.text, Some(valTyp.text))
      case (Some(order), None, Some(value), None) =>
        CellFunctionValParam(order.text.toInt,value.text)
    }).sortBy(_.position)
  }

  private[alignments] def cellFunctionBinding(nodes: NodeSeq): String = {
    // assumption: <return> element is valid, in particular nodes.size == 1
    getAttribute(nodes.head, Prefixes("sripas"), "about")
  }

  private[alignments] def functionName(node: Node): String = {
    // assumption: XML function node is valid
    getAttribute(node, Prefixes("sripas"), "about")
  }

}
