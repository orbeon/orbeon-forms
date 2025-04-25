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
        // Text Field
        "a23599e2da5cd2afca30670a801e465c7993f0ae",
        // Text Field with Character Counter
        "1a7f1efa91c4835191b6551376c2ceef93f1b36e",
        // Text Field with Clipboard Copy
        "85c20808a5e73558375b86e2ea4ce4e1b3e4c642",
        // Plain Text Area
        "Lorem ipsum dolor sit amet, consectetur",
        "adipiscing elit. Nam condimentum quam ut orci",
        "efficitur, sit amet elementum nunc lobortis.",
        "Sed consequat viverra varius. In at turpis",
        "rutrum, ultricies lorem sed, scelerisque justo.",
        // Plain Text Area with Character Counter
        "Nulla pretium, justo at gravida suscipit, mi felis",
        "malesuada ligula, nec placerat libero felis a",
        "nibh. Pellentesque fermentum rutrum aliquam.",
        "Ut eleifend velit id tellus tempor mollis.",
        // Plain Text Area with Resizing
        "Pellentesque orci ex, venenatis id nunc non,",
        "commodo imperdiet magna. Sed faucibus",
        "enim in nunc laoreet, vitae aliquam neque",
        "mattis. Duis a nibh et magna auctor maximus",
        "aliquam nec enim. Nunc convallis laoreet",
        // Plain Text Area with Clipboard Copy
        "Sed consectetur ante ut mauris dictum,",
        "tincidunt tincidunt sapien feugiat. Maecenas",
        "fermentum sagittis nisl et fringilla. Sed at",
        "cursus turpis. Aliquam erat volutpat. Aliquam",
        "erat volutpat. Pellentesque tristique euismod",
        "sem sed commodo.",
//        "Music is an art form whose medium is sound.",
//        "Common elements of music are pitch (which",
//        "Greek µ (mousike)",
//        "From Wikipedia",
        // TODO: Formatted Text Area
        // TODO: Explanatory Text
        // Calculated value
        "686614dd52e678010d2ad24a5ff04e0160b1fb02",
        // Calculated value with Clipboard Copy
        "3dbf71213d81e2e21a15fbdb2426abd650ffb5d5",
        // Number
        "299,792,458 m/s",
        // Currency
        "$ 10.99",
        // Email address
        "info@orbeon.com",
        // US Phone Number
        "(555) 555-5555",
        // US State
        "CA - California",
        // US SSN
        "078-05-1120",
        // US EIN
        "00-1234567",
        // Date
        "4/11/2025",
        // Time
        "6:29:45 pm",
        // TODO: Date and Time
        // TODO: Dropdown Date
        // TODO: Fields Date
        // TODO: Dropdown
        // TODO: Dropdown with "Other"
        // TODO: Dropdown with Search
        // US State
        "Antarctica",
        // TODO: Dynamic Dropdown with Search
        // TODO: Radio Buttons
        // TODO: Radio Buttons with "Other"
        // TODO: Checkboxes
        // TODO: Scrollable Checkboxes
        // TODO: Single Checkbox
        // TODO: Yes/No Answer
        // TODO: Image Attachment
        // TODO: Handwritten Signature
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