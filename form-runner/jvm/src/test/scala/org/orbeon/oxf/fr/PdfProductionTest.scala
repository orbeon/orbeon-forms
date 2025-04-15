package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.scalatest.funspec.AnyFunSpecLike

import scala.util.chaining.*


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
            (new PDFTextStripper)
              .tap(_.setSortByPosition(true))
              .pipe(_.getText(pdd))
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
            (new PDFTextStripper)
              .tap(_.setSortByPosition(true))
              .pipe(_.getText(pdd))
          }

        ExpectedMatches.iterator foreach { word =>
          assert(extractedText.contains(word))
        }
      }
    }

    it("PDF template output for most form controls must contain data text") {

      val ExpectedMatches = List(
        "For #5670: test PDF template output",
        "Michelle",
        // TODO: Text field with Character Counter
        // TODO: Text field with Clipboard Copy
        "Music is an art form whose medium is sound.",
        "Common elements of music are pitch (which",
        "Greek µ (mousike)",
        "From Wikipedia",
        // TODO: Plain text Area with Resizing
        // TODO: Plain text Area with Character Counter
        // TODO: Plain text Area with Clipboard Copy
        // TODO: Formatted Text Area
        // TODO: Explanatory Text
        "Michelle:info@orbeon.com",
        "299,792,458 m/s",
        "$ 10.99",
        "info@orbeon.com",
        "(555) 555-5555",
        "CA - California",
        // TODO: US SSN
        // TODO: US EIN
        "4/11/2025",
        "6:29:45 pm",
        // TODO: Date and Time
        // TODO: Dropdown Date
        // TODO: Fields Date
        // TODO: Dropdown
        // TODO: Dropdown with "Other"
        // TODO: Dropdown with Search
        "Antarctica",
        // TODO: Dynamic Dropdown with Search
        "For #5670: test PDF template output 1 / 2",
        // TODO: Radio Buttons
        // TODO: Radio Buttons with "Other"
        // TODO: Checkboxes
        // TODO: Scrollable Checkboxes
        // TODO: Single Checkbox
        // TODO: Yes/No Answer
        // TODO: Image Attachment
        // TODO: Handwritten Signature
        "For #5670: test PDF template output 2 / 2",
      )

      val (_, content, _) =
        runFormRunnerReturnContent("issue", "5670-1", "pdf", documentId = "4d07137a0f4f0f5c70ce68053fe8b8e93e78c7fe".some)

      withTestExternalContext { _ =>

        val extractedText =
          useAndClose(PDDocument.load(content.stream)) { pdd =>
            (new PDFTextStripper)
              .tap(_.setSortByPosition(true))
              .pipe(_.getText(pdd))
          }

        ExpectedMatches.iterator foreach { word =>
          assert(extractedText.contains(word))
        }
      }
    }
  }
}