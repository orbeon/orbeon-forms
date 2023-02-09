package org.orbeon.oxf.fr

import cats.syntax.option._
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.CoreUtils._
import org.scalatest.funspec.AnyFunSpecLike


class PdfProductionTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Runner PDF production") {

    val MetadataWords = List(
      "Orbeon Demo: Feedback Form",
      "First Name",
      "Last Name",
      "Email",
      "Phone Number",
      "Order Number",
      "Topic",
      "Questions and Comments",
      "Orbeon Demo: Feedback Form 1 / 1"
    )

    val DataWords = List(
      "Homer",
      "Simpson",
      "chunkylover53@aol.com",
      "(939) 555-0113",
      "O888",
      "Returns",
      "D'oh!",
    )

    val (_, content, _) =
      runFormRunnerReturnContent("tests", "pdf-production", "pdf", documentId = "9eff349bfd95aab8d4d5e048bd25a815".some)

      it("must contain metadata and data text") {
        withTestExternalContext { _ =>

          val extractedText =
            useAndClose(PDDocument.load(content.stream)) { pdd =>
              val stripper = new PDFTextStripper |!>
                (_.setSortByPosition(true))
              stripper.getText(pdd)
            }

          MetadataWords.iterator ++ DataWords.iterator foreach { word =>
            assert(extractedText.contains(word))
          }
        }
      }
  }
}