package org.orbeon.oxf.fr

import cats.data.NonEmptyList
import cats.syntax.option.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.text.PDFTextStripper
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.properties.{CombinedPropertyStore, PropertyLoader, PropertyStore}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.dom.IOSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.io.File
import java.nio.file.Files
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.sys.process.*
import scala.util.chaining.*


class PdfProductionTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  // Data for the `tests/pdf-production` form: a Feedback form with a WebP photo attachment
  private val PdfProductionDocumentId = "9eff349bfd95aab8d4d5e048bd25a815"

  // Data for the `tests/pdf-a-attachment` form: a single attachment control, whose value is shown with a mediatype icon
  private val PdfAAttachmentDocumentId = "5f1c7998b2d34e6f8a90c1d2e3f40718"

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
        "Photo",
      )

      val (_, content, _) =
        runFormRunnerReturnContent("tests", "pdf-production", "pdf", documentId = PdfProductionDocumentId.some)

      withTestExternalContext { _ =>

        val extractedText =
          useAndClose(Loader.loadPDF(new RandomAccessReadBuffer(content.stream))) { pdd =>
            (new PDFTextStripper)
              .tap(_.setSortByPosition(true))
              .pipe(_.getText(pdd))
          }

        ExpectedMetadataMatches.iterator ++ ExpectedDataMatches.iterator foreach { word =>
          assert(extractedText.contains(word))
        }
      }
    }

    it("PDF must contain WebP image attachment") {
      val (_, content, _) =
        runFormRunnerReturnContent("tests", "pdf-production", "pdf", documentId = PdfProductionDocumentId.some)
      withTestExternalContext { _ =>
        useAndClose(Loader.loadPDF(new RandomAccessReadBuffer(content.stream))) { pdd =>
          // The test that follows assumes there is only one page
          assert(pdd.getPages.getCount == 1)
          // Look for 2 images: the logo and our WebP image
          val imageCount = pdd.getPage(0).getResources.getXObjectNames.asScala.size
          assert(imageCount == 2)
        }
      }
    }

    it("PDF template for PTA form must contain data text") {
      assume(Version.isPE)

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
          useAndClose(Loader.loadPDF(new RandomAccessReadBuffer(content.stream))) { pdd =>
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
      assume(Version.isPE)

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
        "•••-••-1120",
        // US EIN
        "00-1234567",
        // LEI
        "F50EOCWSQFAUVO9Q8Z97",
        // ISIN
        "US0378331005",
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
          useAndClose(Loader.loadPDF(new RandomAccessReadBuffer(content.stream))) { pdd =>
            (new PDFTextStripper)
              .tap(_.setSortByPosition(true))
              .pipe(_.getText(pdd))
          }

        ExpectedMatches.iterator foreach { word =>
          assert(extractedText.contains(word))
        }
      }
    }

    it("#7214: automatic PDF must contain correct Unicode diacritics") {

      // Form data:         U+0054 U+0068 U+006F U+006D U+0061 U+0073 U+030C U+0063 U+0068 U+000A ("Thomašch")
      // Normalized output: U+0054 U+0068 U+006F U+006D U+0061 U+0161        U+0063 U+0068 U+000A ("Thomašch")

      // Using code points below to make sure the string is not modified by tools (editors, etc.)
      val ExpectedDataMatches = List(
        String.valueOf(
          Array("0054", "0068", "006F", "006D", "0061", "0161", "0063", "0068", "000A")
            .map(Integer.parseInt(_, 16))
            .flatMap(Character.toChars)
        ),
      )

      val (_, content, _) =
        runFormRunnerReturnContent("issue", "7214", "pdf", documentId = "6c989a0cb3e3e2d9334c5a2e985b4bc9a27e164c".some)

      withTestExternalContext { _ =>

        val extractedText =
          useAndClose(Loader.loadPDF(new RandomAccessReadBuffer(content.stream))) { pdd =>
            (new PDFTextStripper)
              .tap(_.setSortByPosition(true))
              .pipe(_.getText(pdd))
          }

        ExpectedDataMatches.iterator foreach { word =>
          assert(extractedText.contains(word))
        }
      }
    }
  }

  describe("Form Runner PDF/A production") {

    // `oxf.fr.pdf.pdf/a` is a global property, so override it for the request only, with a combined property store
    def pdfAPropertyStore(flavour: String): PropertyStore =
      CombinedPropertyStore.combine(NonEmptyList.of(
        Some(PropertyLoader.getPropertyStore(None)),
        Some(PropertyStore.parse(IOSupport.readOrbeonDom(
          s"""<properties xmlns:xs="http://www.w3.org/2001/XMLSchema">
             |    <property as="xs:string" name="oxf.fr.pdf.pdf/a" value="$flavour"/>
             |</properties>""".stripMargin), ""))
      )).get

    for (flavour <- List("1a", "1b", "2a", "2b", "2u", "3a", "3b", "3u")) {
      it(s"automatic PDF must be a valid PDF/A-$flavour") {

        assume(PdfAValidator.isAvailable, "the PDF/A validator classpath is only set when running tests with sbt")

        val (_, content, _) =
          runFormRunnerReturnContent(
            "tests",
            "pdf-production",
            "pdf",
            documentId = PdfProductionDocumentId.some,
            attributes = Map(PropertyLoader.RequestPropertiesAttributeName -> pdfAPropertyStore(flavour))
          )

        val (compliant, report) = PdfAValidator.validate(content.body, flavour)
        assert(compliant, s"veraPDF report:\n$report")
      }
    }

    it("automatic PDF without `oxf.fr.pdf.pdf/a` must not be a valid PDF/A-1b") {

      assume(PdfAValidator.isAvailable, "the PDF/A validator classpath is only set when running tests with sbt")

      val (_, content, _) =
        runFormRunnerReturnContent("tests", "pdf-production", "pdf", documentId = PdfProductionDocumentId.some)

      val (compliant, report) = PdfAValidator.validate(content.body, "1b")
      assert(! compliant)
      assert(report.startsWith("NOT COMPLIANT"), s"veraPDF report:\n$report")
    }

    // The attachment mediatype icon is a Font Awesome glyph, which PDF/A-2a and 3a reject without an ActualText entry
    describe("attachment mediatype icon") {

      def pdfContent(flavour: Option[String]) =
        runFormRunnerReturnContent(
          "tests",
          "pdf-a-attachment",
          "pdf",
          documentId = PdfAAttachmentDocumentId.some,
          attributes = flavour.map(f => PropertyLoader.RequestPropertiesAttributeName -> pdfAPropertyStore(f)).toMap
        )._2

      def hasFontAwesome(pdf: Array[Byte]): Boolean =
        useAndClose(Loader.loadPDF(new RandomAccessReadBuffer(pdf))) { pdd =>
          pdd.getPages.asScala.exists { page =>
            val resources = page.getResources
            resources.getFontNames.asScala.exists(name => resources.getFont(name).getName.contains("FontAwesome"))
          }
        }

      it("must show in a PDF which is not a PDF/A") {
        assert(hasFontAwesome(pdfContent(None).body))
      }

      for (flavour <- List("1a", "1b", "2b", "2u", "3b", "3u")) {
        it(s"must show in a PDF/A-$flavour, which must be valid") {

          assume(PdfAValidator.isAvailable, "the PDF/A validator classpath is only set when running tests with sbt")

          val pdf = pdfContent(flavour.some).body
          assert(hasFontAwesome(pdf))

          val (compliant, report) = PdfAValidator.validate(pdf, flavour)
          assert(compliant, s"veraPDF report:\n$report")
        }
      }

      for (flavour <- List("2a", "3a")) {
        it(s"must not show in a PDF/A-$flavour, which must be valid") {

          assume(PdfAValidator.isAvailable, "the PDF/A validator classpath is only set when running tests with sbt")

          val pdf = pdfContent(flavour.some).body
          assert(! hasFontAwesome(pdf))

          val (compliant, report) = PdfAValidator.validate(pdf, flavour)
          assert(compliant, s"veraPDF report:\n$report")
        }
      }
    }
  }
}

// Run PdfAValidatorMain in a separate JVM, as veraPDF depends on Saxon-HE, which shares its packages with Orbeon's Saxon
object PdfAValidator {

  private val ClasspathPropertyName = "orbeon.test.pdfa-validator.classpath"

  def isAvailable: Boolean =
    Option(System.getProperty(ClasspathPropertyName)).exists(_.nonEmpty)

  def validate(pdf: Array[Byte], flavour: String): (Boolean, String) = {
    val file = File.createTempFile("pdfa-", ".pdf")
    try {
      Files.write(file.toPath, pdf)
      val java   = new File(System.getProperty("java.home"), "bin/java").getAbsolutePath
      val output = new StringBuilder
      val exitCode =
        Process(
          List(
            java,
            "-Xmx256m",
            "-cp",
            System.getProperty(ClasspathPropertyName),
            "org.orbeon.pdfa.PdfAValidatorMain",
            file.getAbsolutePath,
            flavour
          )
        ).!(ProcessLogger(line => output.append(line).append('\n')))

      (exitCode == 0, output.toString)
    } finally
      file.delete()
  }
}
