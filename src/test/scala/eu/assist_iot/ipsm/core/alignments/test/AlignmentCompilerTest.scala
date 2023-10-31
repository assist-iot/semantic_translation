package eu.assist_iot.ipsm.core.alignments.test

import java.io.{File, FileOutputStream, PrintWriter}
import com.typesafe.scalalogging.LazyLogging
import eu.assist_iot.ipsm.core.alignments.{Alignment, AlignmentValidator}
import org.scalatest.flatspec._
import org.scalatest.matchers.should.Matchers
import eu.assist_iot.ipsm.core.util.DosProneXmlLoader


class AlignmentCompilerTest extends AnyFlatSpec with Matchers with LazyLogging {
  val fnames = List(
     "BodyCloud_CO",
     "CO_FIWARE",
     "NOATUM_CO",
     "UniversAAL_CO"
  )

  fnames.foreach { an =>
    val alignmentName = an.replace("/","_")
    val alignment_path = getClass.getResource(s"/data/alignments/$an.rdf").getPath
    val alignment = DosProneXmlLoader.loadFile(alignment_path)

    val validationResult = AlignmentValidator(alignment.toString)
    if (validationResult.nonEmpty) {
      println()
      for (err <- validationResult) {
        println(s"Alignment '$an':\n")
        println(s"\tERROR: $err")
      }
      System.exit(1)
    }

    new File("target/output/alignments").mkdirs()
    val writer = new PrintWriter(new FileOutputStream(s"target/output/alignments/$alignmentName.sparql", false))

    writer.write(s"#${"=" * 79}\n")
    writer.write(s"# $alignmentName\n")
    writer.write(s"#${"=" * 79}\n")
    val align = new Alignment(alignment.toString())
    for (cId <- align.alignmentCells.keys) {
      val cell = align.alignmentCells(cId)
      writer.write(cell.toString)
      writer.write(s"#${"=" * 79}\n")
      s"""$alignmentName "${cell.cellId}"""" should "successfully compile to SPARQL" in {
        val cbn = cell != null
        cbn shouldEqual true
      }
    }
    writer.close()
  }
}
