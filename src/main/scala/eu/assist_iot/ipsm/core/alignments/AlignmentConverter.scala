package eu.assist_iot.ipsm.core.alignments

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}

import eu.assist_iot.ipsm.core.alignments.CellFormat.CellFormat
import eu.assist_iot.ipsm.core.config.Prefixes
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{RDFDataMgr, RDFFormat}
import org.dom4j._
import org.dom4j.io.{OutputFormat, SAXReader, XMLWriter}

import scala.jdk.CollectionConverters._
import scala.util.control.Breaks._

/**
  * Converts string representations of XML alignment format (old IPSM-AF) to RDF/XML representation (new IPSM-AF)
  */
object AlignmentConverter {

  /**
    * Gets the prefix for uriNamespace from the document, if present.
    * Otherwise sets the prefix for uriNamespace to defaultPrefix in the document and returns uriNamespace
    *
    * @param defaultPrefix ---
    * @param uriNamespace  ---
    * @param document      ---
    * @return
    */
  private def getOrForcePrefix(defaultPrefix: String, uriNamespace: String, document: Document): String = {
    val namespaceObj = Option(document.getRootElement.getNamespaceForURI(uriNamespace))
    if (namespaceObj.isDefined && namespaceObj.get.getPrefix != "") {
      namespaceObj.get.getPrefix
    } else {
      var newPrefix = defaultPrefix
      var i = 1
      breakable {
        while (document.getRootElement.getNamespaceForPrefix(newPrefix) != null) {
          val t = document.getRootElement.getNamespaceForPrefix(newPrefix)
          if (t.getURI == uriNamespace) break()
          newPrefix = defaultPrefix + i
          i += 1
        }
      }
      document.getRootElement.addNamespace(newPrefix, uriNamespace)
      newPrefix
    }
  }

  /**
    * Gathers all namespaces for an element and all its parents in a single collection
    *
    * @param elem ---
    * @return ---
    */
  private def collectNamespaces(elem: Element): Set[Namespace] = {
    elem.additionalNamespaces().asScala.toSet ++
      elem.declaredNamespaces().asScala.toSet ++
      (if (elem.getParent != null) {
        collectNamespaces(elem.getParent)
      } else {
        Set[Namespace]()
      })
  }

  /**
    * Converts string representations of XML alignment format (old IPSM-AF) to RDF/XML representation (new IPSM-AF)
    *
    * @param xmlSource an alignment source in old XML format
    * @return an alignment source in new RDF/XML format, converted from xmlSource
    */
  def convertFormatToRDFXML(xmlSource: String): String = {

    val document = new SAXReader().read(new StringReader(xmlSource))
    val root = document.getRootElement
    val rdfPrefix = getOrForcePrefix("rdf", Prefixes("rdf"), document)
    val alignPrefix = getOrForcePrefix("align", Prefixes("align"), document)
    val sripasPrefix = getOrForcePrefix("sripas", Prefixes("sripas"), document)
    val iiotPrefix = getOrForcePrefix("iiot", Prefixes("iiot"), document)
    val dcelemPrefix = getOrForcePrefix("dcelem", Prefixes("dc"), document)
    val exmoPrefix = getOrForcePrefix("exmo", Prefixes("exmo"), document)

    def getAttributeValue(elem: Element, attrName: String, default: String): String =
      Option(elem.attributeValue(attrName)).filter(_ != "").getOrElse(default)

    val newDocument = DocumentHelper.createDocument
    val newRoot = newDocument.addElement(s"$rdfPrefix:RDF")
    //    newRoot.add(Namespace.get(root.getNamespace.getURI ))
    collectNamespaces(root).foreach(ns => newRoot.addNamespace(ns.getPrefix, ns.getURI))
    val alignmentNode = root.selectSingleNode(s"/Alignment").asInstanceOf[Element]

    collectNamespaces(alignmentNode).foreach(ns => newRoot.addNamespace(ns.getPrefix, ns.getURI))
    val newAlignmentNode = newRoot.addElement(s"$alignPrefix:Alignment")

    //Header properties
    val attName = getAttributeValue(alignmentNode, "name",
      "new alignment")
    val attVersion = getAttributeValue(alignmentNode, "version",
      "1.0")
    val attCreator = getAttributeValue(alignmentNode, "creator",
      "IPSM automated converter")
    val attDescription = getAttributeValue(alignmentNode, "description",
      "Alignment automatically converted from xn XML version into RDF/XML")

    newAlignmentNode.addElement(s"$dcelemPrefix:title").addText(attName)
    newAlignmentNode.addElement(s"$exmoPrefix:version").addText(attVersion)
    newAlignmentNode.addElement(s"$dcelemPrefix:creator").addText(attCreator)
    newAlignmentNode.addElement(s"$dcelemPrefix:description").addText(attDescription)
    newAlignmentNode.addElement(s"$dcelemPrefix:date").addText(new java.util.Date().toString)

    //For some reason attribute names cannot be modified with .setName(...), so it has to be done this way
    def changeAttrName(elem: Element, oldName: String, newName: String): Unit = {
      val attrs = elem.attributes().asScala
      elem.setAttributes(new java.util.LinkedList[Attribute]())
      attrs.foreach(attr =>
        if (attr.getName == oldName) {
          elem.addAttribute(newName, attr.getValue)
        } else {
          elem.addAttribute(attr.getName, attr.getValue)
        }
      )
    }

    def convertOntologyTags(alignmentNode: Node, tagName: String): Node = {
      //      val t = alignmentNode.getPath
      val ontoNode = alignmentNode.selectSingleNode(s"$sripasPrefix:$tagName")
      ontoNode.setName(s"$alignPrefix:$tagName")

      val ontologyNode = ontoNode.selectSingleNode(s"$sripasPrefix:Ontology").asInstanceOf[Element]
      ontologyNode.setName(s"$alignPrefix:Ontology")

      changeAttrName(ontologyNode, "about", s"$rdfPrefix:about")

      val formalismNode = ontologyNode.selectSingleNode(s"$sripasPrefix:formalism")
      formalismNode.setName(s"$alignPrefix:formalism")

      val formalismNode2 = formalismNode.selectSingleNode(s"$sripasPrefix:Formalism")
      formalismNode2.setName(s"$alignPrefix:Formalism")

      changeAttrName(formalismNode2.asInstanceOf[Element], "name", s"$alignPrefix:name")
      changeAttrName(formalismNode2.asInstanceOf[Element], "uri", s"$alignPrefix:uri")

      ontoNode
    }

    //onto1 & onto2
    val onto1Node = convertOntologyTags(alignmentNode, "onto1").detach()
    val onto2Node = convertOntologyTags(alignmentNode, "onto2").detach()

    newAlignmentNode.add(onto1Node)
    newAlignmentNode.add(onto2Node)

    //steps
    val stepsNode = root.selectSingleNode(s"$sripasPrefix:steps").asInstanceOf[Element]
    stepsNode.setName(s"$sripasPrefix:steps")
    stepsNode.addAttribute(s"$rdfPrefix:parseType", "Literal")
    stepsNode.selectNodes(s"$sripasPrefix:step").asScala.foreach(n => {
      val e = n.asInstanceOf[Element]
      e.setName(s"$sripasPrefix:step")
      changeAttrName(e, "order", s"$sripasPrefix:order")
      changeAttrName(e, "cell", s"$sripasPrefix:cell")
    }
    )
    newAlignmentNode.add(stepsNode.detach())

    //Cell format
    newAlignmentNode.addElement(s"$sripasPrefix:cellFormat")
      .addElement(s"$iiotPrefix:DataFormat")
      .addAttribute(s"$rdfPrefix:about", "http://inter-iot.eu/sripas#rdfxml")

    //Cells
    val cellNodes = root
      .selectSingleNode(s"$sripasPrefix:map")
      .selectNodes(s"$sripasPrefix:Cell").asScala
    cellNodes.map(_.asInstanceOf[Element]).foreach(cellNode => {
      val mapNode = newAlignmentNode.addElement(s"$alignPrefix:map")
      cellNode.setName(s"$alignPrefix:Cell")
      changeAttrName(cellNode, "id", s"$rdfPrefix:about")
      List("entity1", "entity2").foreach(name =>
        cellNode.selectSingleNode(s"$sripasPrefix:$name").asInstanceOf[Element]
          .addAttribute(s"$rdfPrefix:parseType", "Literal"))
      List("entity1", "entity2", "relation", "measure").foreach(name =>
        Option(cellNode.selectSingleNode(s"$sripasPrefix:$name"))
          .foreach(_.setName(s"$alignPrefix:$name"))
      )
      List("transformation", "typings", "filters").foreach(name =>
        Option(cellNode.selectSingleNode(s"$sripasPrefix:$name"))
          .foreach { node =>
            node.setName(s"$sripasPrefix:$name")
            node.asInstanceOf[Element].addAttribute(s"$rdfPrefix:parseType", "Literal")
          }
      )

      mapNode.add(cellNode.detach())
    }
    )

    //Removing xmlns="" that was for some reason put there by DOM4J
    //This is stupid, but I don't know a better way
    var tempString = newDocument.asXML()
    List("onto1", "onto2", "Ontology", "steps", "Cell")
      .foreach(s => tempString = tempString.replace(s"$s xmlns" + "=\"\"", s))

    //Expanding local call URIs to full URIs with sripas prefix
    val replacementAttributes = List(s"$rdfPrefix:about", s"$rdfPrefix:resource", s"$sripasPrefix:cell")
    cellNodes.flatMap(cn => Option(cn.asInstanceOf[Element].attributeValue(s"$rdfPrefix:about")))
      .filterNot(s => s.contains(":"))
      .foreach(s =>
        replacementAttributes.foreach( rAtt =>
          tempString = tempString.replace(rAtt + "=\"" + s + "\"",
            rAtt + "=\"" + Prefixes("sripas") + s + "\"")
        )
      )

    val document3 = new SAXReader().read(new StringReader(tempString))

    // Pretty print the document
    val baos = new ByteArrayOutputStream
    val ps = new PrintStream(baos, true)
    val format = OutputFormat.createPrettyPrint
    val writer = new XMLWriter(ps, format)
    writer.write(document3)
    new String(baos.toByteArray)
  }

  /**
    * Returns an RDF/XML Alignment source with alignment cells converted to cellFormat.
    * If the original source already has cells in cellFormat, it is returned unchanged
    *
    * @param cellFormat ---
    * @return ---
    */
  //noinspection ScalaStyle
  def convertCellFormat(alignment: Alignment, cellFormat: CellFormat): String = {
    //---INIT---
    val document = new SAXReader().read(new StringReader(alignment.xmlSource))
    val root = document.getRootElement

    val alignPrefix = getOrForcePrefix("align", Prefixes("align"), document)
    val rdfPrefix = getOrForcePrefix("rdf", Prefixes("rdf"), document)
    val sripasPrefix = getOrForcePrefix("sripas", Prefixes("sripas"), document)
    val iiotPrefix = getOrForcePrefix("iiot", Prefixes("iiot"), document)

    def changeCellFormat(newCellFormat: CellFormat, oldCellFormat: CellFormat, cell: Node): Unit = {
      val cellUri = cell.valueOf(s"@$rdfPrefix:about")

      val res = alignment.rdfCells.values.find(r => r.getURI == cellUri)

      if (res.isEmpty) {
        throw new Exception(s"no valid RDF cell with URI $cellUri in rdfCells map")
      }

      //Pairs of entity XML Element, and entity context extracted from RDF
      for {
        (entity, content) <- List("entity1", "entity2").map { txt =>
          val elem = cell
            .selectSingleNode(s"$alignPrefix:$txt")
            .asInstanceOf[Element]

          val node = res.get
            .getProperty(
              ModelFactory
                .createDefaultModel()
                .createProperty(s"${Prefixes("align")}$txt")
            )
            .getObject

          (elem, node)
        }
      } {
        val namespaces = collectNamespaces(entity)

        //clear contents
        entity
          .selectNodes("*")
          .asScala
          .foreach(n => n.detach())

        val entityModel = loadEntityToModel(content.asLiteral().getValue.toString, oldCellFormat, namespaces)

        //calculate and set new contents
        newCellFormat match {
          case CellFormat.Turtle =>
            val newContent = getEntityContentAsTTLString(entityModel)
            entity.setText(newContent)
            //change attributes that control parsing
            entity.setAttributes(
              entity
                .attributes().asScala
                .filterNot(att => att.getName == "parseType" && att.getNamespacePrefix == rdfPrefix)
                .asJava
            )
            entity.addAttribute(s"$rdfPrefix:datatype", s"${Prefixes("xsd")}string")
          case CellFormat.RdfXml =>
            val entityDocument = getEntityContentAsRDFXMLDocument(entityModel)
            val newEntityNodes = entityDocument.getRootElement.content().asScala.filter(_ match {
              case _: Namespace => false
              case _ => true
            })
            entity.setContent(newEntityNodes.asJava)
            entity.setAttributes(
              entity
                .attributes().asScala
                .filterNot(att => att.getName == "datatype" && att.getNamespacePrefix == rdfPrefix)
                .asJava
            )
            entity.addAttribute(s"$rdfPrefix:parseType", "Literal")
          //throw new NotImplementedError("Coversion of cells to RDF/XML format is not yet implemented")
        }
      }
    }

    def loadEntityToModel(oldContent: String, oldFormat: CellFormat, namespaces: Set[Namespace]): Model = {
      val model = ModelFactory.createDefaultModel()
      oldFormat match {
        case CellFormat.Turtle =>
          val prefixes = namespaces.map(ns => s"@prefix ${ns.getPrefix}: <${ns.getURI}> .").mkString("\n")
          val ttlString =
            s"""
               |$prefixes
               |$oldContent
             """.stripMargin
          //          @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
          model.read(new StringReader(ttlString), Prefixes("sripas"), "Turtle")
        case CellFormat.RdfXml =>
          val prefixes = namespaces.map(ns => s"xmlns:${ns.getPrefix}=${"\"" + ns.getURI + "\""}")
            .mkString("\n")
          val rdfString =
            s"""
               |<rdf:RDF
               |$prefixes
               |>
               |$oldContent
               |</rdf:RDF>
            """.stripMargin
          model.read(new StringReader(rdfString), Prefixes("sripas"))
      }
      model
    }

    def getEntityContentAsRDFXMLDocument(modelWithEntity: Model): Document = {
      val outStream = new ByteArrayOutputStream
      RDFDataMgr.write(outStream, modelWithEntity, RDFFormat.RDFXML_PRETTY)
      new SAXReader().read(new StringReader(outStream.toString))
    }

    def getEntityContentAsTTLString(modelWithEntity: Model): String = {
      val outStream = new ByteArrayOutputStream
      RDFDataMgr.write(outStream, modelWithEntity, RDFFormat.TURTLE_PRETTY)
      outStream.toString.split("\\r?\\n|\\r").filterNot(s => s.startsWith("@prefix")).mkString("\n") + "\n"
    }

    /**
      * Sets the XML node under sripas:CellFormat and inserts it into the alignment document
      *
      * @param cellFormat ---
      * @param document   ---
      * @return previously set CellFormat, or default RDF/XML, if not present
      */
    def setCellFormatPropertyInXML(cellFormat: CellFormat, document: org.dom4j.Document): CellFormat = {
      val cfName = s"$sripasPrefix:cellFormat"
      val dfName = s"$iiotPrefix:DataFormat"

      def insertCellFormatNode(alignmentElem: Element, cfNode: Element): Unit = {
        //        val elemList = alignmentElem.nodeIterator().asScala

        def insertBeforeMap(nodeList: List[org.dom4j.Node]): List[org.dom4j.Node] = nodeList match {
          case head :: Nil => head :: cfNode :: Nil
          case head :: tail =>
            head match {
              case el: Element if el.getName == s"map" && el.getNamespace.getPrefix == s"$alignPrefix" =>
                cfNode :: nodeList
              case _ => head :: insertBeforeMap(tail)
            }
          case Nil => List(cfNode)
        }

        alignmentElem.setContent(insertBeforeMap(alignmentElem.nodeIterator().asScala.toList).asJava)
      }

      val cellFormatNode: Element = {
        val t = root.selectNodes(s"$alignPrefix:Alignment/$cfName")
        if (t.isEmpty) {
          val alElem = root.selectSingleNode(s"$alignPrefix:Alignment").asInstanceOf[Element]
          val cfNode = alElem.addElement(cfName)
          cfNode.detach()
          insertCellFormatNode(alElem, cfNode)
          cfNode
        } else {
          t.asScala.head.asInstanceOf[Element]
        }
      }

      val dataFormatNode = {
        val t = cellFormatNode.selectNodes(dfName)
        if (t.isEmpty) {
          val newNode = cellFormatNode.addElement(dfName)
          //Remove all other nodes under sripas:cellFormat, to preserve RDF/XML structural correctness
          newNode.detach()
          cellFormatNode.setContent(List(newNode.asInstanceOf[org.dom4j.Node]).asJava)
          newNode
        } else {
          t.asScala.head.asInstanceOf[Element]
        }
      }

      val oldFormat = dataFormatNode
        .attributes().asScala
        .find(att => att.getName == "about" && att.getNamespace.getPrefix == rdfPrefix)
        .flatMap(attr => {
          dataFormatNode.remove(attr)
          CellFormat.fromUri(attr.getValue)
        })
        .getOrElse(CellFormat.RdfXml)

      dataFormatNode.addAttribute(s"$rdfPrefix:about", cellFormat.getUri)
      oldFormat
    }

    //---BODY---
    if (cellFormat == alignment.cellFormat) {
      alignment.xmlSource
    } else {
      val oldCellFormat = setCellFormatPropertyInXML(cellFormat, document)
      val cells = root.selectNodes(s"$alignPrefix:Alignment/$alignPrefix:map/$alignPrefix:Cell").asScala
      for (cell <- cells) {
        changeCellFormat(cellFormat, oldCellFormat, cell)
      }

      // Pretty print the document
      //      val baos = new ByteArrayOutputStream
      //      val ps = new PrintStream(baos, true)
      //      val format = OutputFormat.createPrettyPrint()
      //      val writer = new XMLWriter(ps, format)
      //      writer.write(document)
      //      new String(baos.toByteArray)
      document.asXML()
    }
  }

}
