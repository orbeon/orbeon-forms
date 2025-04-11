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

    it("automatic PDF must contain metadata and data text") {

      val ExpectedMetadataMatches = List(
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

      val ExpectedDataMatches = List(
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

      withTestExternalContext { _ =>

        val extractedText =
          useAndClose(PDDocument.load(content.stream)) { pdd =>
            val stripper = new PDFTextStripper |!>
              (_.setSortByPosition(true))
            stripper.getText(pdd)
          }

        ExpectedMetadataMatches.iterator ++ ExpectedDataMatches.iterator foreach { word =>
          assert(extractedText.contains(word))
        }
      }
    }

    it("PDF template for PTA form must contain data text") {

      val ExpectedMatches = List(
        "Name of PTA Unit: Springfield PTA",
        "Address of Unit: 742 Evergreen Terrace",
        "Unit Number: 12345",
        "Submitted by: Homer Simpson",
        "Phone: (939) 555-0113",
        "Position: Parent",
        "Date submitted: 1/1/2023",
        "Email: chunkylover53@aol.com",
        "Check #: 1",
        "Per Capita Dues for: 12 Members",
        "$ 57.00",
        "$ 125.00",
        "$ 1.00",
        "$ 183.00",
      )

      val (_, content, _) =
        runFormRunnerReturnContent("orbeon", "pta-remittance", "pdf", documentId = "aed126ad8341b629f3b19ee7f3e9d4f7c83ebfe5".some)

      withTestExternalContext { _ =>

        val extractedText =
          useAndClose(PDDocument.load(content.stream)) { pdd =>
            val stripper = new PDFTextStripper |!>
              (_.setSortByPosition(true))
            stripper.getText(pdd)
          }

        ExpectedMatches.iterator foreach { word =>
          assert(extractedText.contains(word))
        }
      }
    }
  }
}