/**
 * Copyright (C) 2025 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr

import cats.implicits.catsSyntaxOptionId
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.fr.email.EmailMetadata.{HeaderName, TemplateMatch}
import org.orbeon.oxf.fr.email.{Attachment, EmailContent, MessageContent}
import org.orbeon.oxf.fr.process.FormRunnerRenderedFormat.*
import org.orbeon.oxf.fr.process.ProcessInterpreter.ActionParams
import org.orbeon.oxf.fr.process.SimpleProcess.clearRenderedFormatsResources
import org.orbeon.oxf.fr.process.{FormRunnerActionsCommon, RenderedFormat}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.ContentTypes
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.file.{Files, Path}


class EmailContentTest
  extends DocumentTestBase
    with ResourceManagerSupport
    with AnyFunSpecLike
    with FormRunnerSupport {

  // Some of the EmailContent logic is already tested by S3Test:
  //  - template selection (by 'enable if true' expression, template name, or language)
  //  - attachments (actual content, content type)
  //
  //  We'll focus here on the following:
  //   - headers (standard vs custom, control value vs formula vs text)
  //   - subject (default, specified with or without template parameters)
  //   - body (default, specified with or without template parameters, non-HTML vs HTML)
  //   - template parameters (all types)

  describe("Form Runner email generation") {

    def pdfRenderingsFor(params: ActionParams): List[PdfRendering] =
      PdfRendering.fromActionParamsPerPdfTemplate(
        params                       = params,
        frFormAttachmentsRootElemOpt = FormRunnerActionsCommon.findFrFormAttachmentsRootElem,
        defaultLang                  = FormRunner.currentLang
      )

    def defaultPdfRenderings: List[PdfRendering] = pdfRenderingsFor(Map.empty)

    val (processorServiceFor6848, Some(docFor6848), _) = runFormRunner("issue", "6848", "new")

    val (processorServiceFor7872, Some(docFor7872), _) = runFormRunner("issue", "7872", "new")

    val (processorServiceFor7854, Some(docFor7854), _) = runFormRunner("issue", "7854", "new")

    it("must handle headers, subject, body. and template parameters correctly") {
      val templatesAndResults = Seq(
        "1" -> List(
          ExpectedEmailContent(
            headers               = Set(
              (HeaderName.From,                   "email1@from.control"),
              (HeaderName.To,                     "email2@from.control"),
              (HeaderName.CC,                     "email3@from.control"),
              (HeaderName.BCC,                    "email4@from.control"),
              (HeaderName.ReplyTo,                "email5@from.control"),
              (HeaderName.Custom("X-Custom-1st"), "Control: custom1"),
              (HeaderName.Custom("X-Custom-2nd"), "Control: custom2")
            ),
            subject               = "Orbeon Forms Confirmation",
            messageContentAsRegex = MessageContent(
              content = "^Please find attached the form in PDF and XML format\\.$",
              html    = false
            )
          )
        ),
        "2" -> List(
          ExpectedEmailContent(
            headers               = Set(
              (HeaderName.From,                   "email1@from.control-suffix"),
              (HeaderName.To,                     "email2@from.control-suffix"),
              (HeaderName.CC,                     "email3@from.control-suffix"),
              (HeaderName.BCC,                    "email4@from.control-suffix"),
              (HeaderName.ReplyTo,                "email5@from.control-suffix"),
              (HeaderName.Custom("X-Custom-1st"), "Control: custom1-suffix"),
              (HeaderName.Custom("X-Custom-2nd"), "Control: custom2-suffix")
            ),
            subject               = "Email subject",
            messageContentAsRegex = MessageContent(
              content = "^Email body$",
              html    = false
            )
          )
        ),
        "3" -> List(
          ExpectedEmailContent(
            headers               = Set(
              (HeaderName.From,    "Text: sender"),
              (HeaderName.To,      "Text: recipient"),
              (HeaderName.CC,      "Text: cc"),
              (HeaderName.BCC,     "Text: bcc"),
              (HeaderName.ReplyTo, "Text: reply to"),
              (HeaderName.Custom("X-Custom-1st"), "Text: x-custom-1st"),
              (HeaderName.Custom("X-Custom-2nd"), "Text: x-custom-2nd")
            ),
            subject               = "Email subject: Control: param1",
            messageContentAsRegex = MessageContent(
              content = """^Email body:
param1: Control: param1
param2: Control: param2-suffix
param3: <ul><li>Email 1: email1@from\.control</li><li>Email 2: email2@from\.control</li><li>Email 3: email3@from\.control</li><li>Email 4: email4@from\.control</li><li>Email 5: email5@from\.control</li><li>Custom 1: Control: custom1</li><li>Custom 2: Control: custom2</li><li>Param 1: Control: param1</li><li>Param 2: Control: param2</li><li>Dropdown 1: Second choice</li><li>Checkboxes 1: ☒ First choice, ☒ Third choice</li><li>My date: 3/27/2026</li><li>My time: 1:58:56 pm</li></ul>
param4: http://localhost:8080/orbeon/fr/issue/6848/edit/([a-zA-Z0-9]+)\?form-version=1
param5: http://localhost:8080/orbeon/fr/issue/6848/edit/([a-zA-Z0-9]+)\?form-version=1&fr-access-token=(.+?)
param6: http://localhost:8080/orbeon/fr/issue/6848/view/([a-zA-Z0-9]+)\?form-version=1
param7: http://localhost:8080/orbeon/fr/issue/6848/view/([a-zA-Z0-9]+)\?form-version=1&fr-access-token=(.+?)
param8: http://localhost:8080/orbeon/fr/issue/6848/new\?form-version=1
param9: http://localhost:8080/orbeon/fr/issue/6848/summary\?form-version=1
param10: http://localhost:8080/orbeon/fr/
param11: http://localhost:8080/orbeon/fr/forms
param12: http://localhost:8080/orbeon/fr/admin
param13: http://localhost:8080/orbeon/fr/issue/6848/pdf/([a-zA-Z0-9]+)\?form-version=1
param14: http://localhost:8080/orbeon/fr/issue/6848/pdf/([a-zA-Z0-9]+)\?form-version=1&fr-access-token=(.+?)
param15: Second choice
param16: First choice, Third choice$""",
              html    = false
            )
          )
        ),
        "4" -> List(
          ExpectedEmailContent(
            headers               = Set(
              (HeaderName.From, "email1@from.control"),
              (HeaderName.To,   "email2@from.control")
            ),
            subject               = "Orbeon Forms Confirmation",
            messageContentAsRegex = MessageContent(
              content = "<html><body><div><strong>HTML</strong> email body</div></body></html>",
              html    = true
            )
          )
        ),
        "5" -> List(
          ExpectedEmailContent(
            headers               = Set(
              (HeaderName.From, "email1@from.control"),
              (HeaderName.To,   "email2@from.control")
            ),
            subject               = "Orbeon Forms Confirmation",
            messageContentAsRegex = MessageContent(
              content =
                """^<html><body><div><strong>HTML</strong> email body:<br>""" +
                """param1: Control: param1<br>""" +
                """param2: Control: param2-suffix<br>""" +
                """param3: <ul><li>Email 1: email1@from\.control</li><li>Email 2: email2@from\.control</li>""" +
                """<li>Email 3: email3@from\.control</li><li>Email 4: email4@from\.control</li>""" +
                """<li>Email 5: email5@from\.control</li><li>Custom 1: Control: custom1</li>""" +
                """<li>Custom 2: Control: custom2</li><li>Param 1: Control: param1</li>""" +
                """<li>Param 2: Control: param2</li>""" +
                """<li>Dropdown 1: Second choice</li>""" +
                """<li>Checkboxes 1: ☒ First choice, ☒ Third choice</li>""" +
                """<li>My date: 3/27/2026</li>""" +
                """<li>My time: 1:58:56 pm</li>""" +
                """</ul><br>""" +
                """param4: http://localhost:8080/orbeon/fr/issue/6848/edit/([a-zA-Z0-9]+)\?form-version=1<br>""" +
                """param5: http://localhost:8080/orbeon/fr/issue/6848/edit/([a-zA-Z0-9]+)\?form-version=1&fr-access-token=(.+?)<br>""" +
                """param6: http://localhost:8080/orbeon/fr/issue/6848/view/([a-zA-Z0-9]+)\?form-version=1<br>""" +
                """param7: http://localhost:8080/orbeon/fr/issue/6848/view/([a-zA-Z0-9]+)\?form-version=1&fr-access-token=(.+?)<br>""" +
                """param8: http://localhost:8080/orbeon/fr/issue/6848/new\?form-version=1<br>""" +
                """param9: http://localhost:8080/orbeon/fr/issue/6848/summary\?form-version=1<br>""" +
                """param10: http://localhost:8080/orbeon/fr/<br>""" +
                """param11: http://localhost:8080/orbeon/fr/forms<br>""" +
                """param12: http://localhost:8080/orbeon/fr/admin<br>""" +
                """param13: http://localhost:8080/orbeon/fr/issue/6848/pdf/([a-zA-Z0-9]+)\?form-version=1<br>""" +
                """param14: http://localhost:8080/orbeon/fr/issue/6848/pdf/([a-zA-Z0-9]+)\?form-version=1&fr-access-token=(.+?)<br>""" +
                """param15: Second choice<br>""" +
                """param16: First choice, Third choice<br>""" +
                """param17: 3/27/2026<br>""" +
                """param18: 1:58:56 pm<br>""" +
                """param19: hidden1""" +
                """</div></body></html>$""",
              html    = true
            )
          )
        ),
        "6" -> List(
          ExpectedEmailContent(
            headers               = Set(
              (HeaderName.From, "email1@from.control"),
              (HeaderName.To,   "email2@from.control")
            ),
            subject               = "Orbeon Forms Confirmation",
            messageContentAsRegex = MessageContent(
              content = """^Email body:
param3: <ul><li>Email 1: email1@from\.control</li><li>Email 3: email3@from\.control</li><li>Email 4: email4@from\.control</li><li>Email 5: email5@from\.control</li><li>Custom 1: Control: custom1</li><li>Param 2: Control: param2</li><li>Dropdown 1: Second choice</li><li>Checkboxes 1: ☒ First choice, ☒ Third choice</li><li>My date: 3/27/2026</li><li>My time: 1:58:56 pm</li></ul>$""",
              html    = false
            )
          )
        ),
      )

      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorServiceFor6848, docFor6848) {

          implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

          for ((templateName, expectedResult) <- templatesAndResults) {

            val emailContents = process.SimpleProcess.emailsToSend(
              emailDataFormatVersion   = DataFormatVersion.Edge,
              templateMatch            = TemplateMatch.First,
              language                 = FormRunner.currentLang,
              templateNameOpt          = templateName.some,
              pdfRenderings            = defaultPdfRenderings
            )

            assert(emailContents.size == expectedResult.size, "Wrong email count")

            if (emailContents.size == expectedResult.size) {
              emailContents.zip(expectedResult).foreach { case (emailContent, expectedEmailContent) =>
                // Ignore headers order
                assert(emailContent.headers.toSet       == expectedEmailContent.headers)
                assert(emailContent.subject             == expectedEmailContent.subject)
                assert(emailContent.messageContent.html == expectedEmailContent.messageContentAsRegex.html)
                // We test the body with a regex
                assert(emailContent.messageContent.content.matches(expectedEmailContent.messageContentAsRegex.content))
              }
            }
          }
        }
      }
    }

    it("must generate the PDF rendered format only when requested as attachment") {
      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorServiceFor7872, docFor7872) {

          implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

          for ((templateName, expectedResult) <- List(
            "with-pdf-false"   -> false,
            "with-pdf-true"    -> true,
            "with-pdf-default" -> true // `oxf.fr.email.attach-pdf` is `true` by default
          )) locally {

            clearRenderedFormatsResources()

            process.SimpleProcess.emailsToSend(
              emailDataFormatVersion   = DataFormatVersion.Edge,
              templateMatch            = TemplateMatch.First,
              language                 = FormRunner.currentLang,
              templateNameOpt          = templateName.some,
              pdfRenderings            = defaultPdfRenderings
            )
            .foreach { emailContent =>

              // Must match the expected result in the email content
              assert(emailContent.attachments.exists(_.contentType == "application/pdf") == expectedResult)

              val urisByRenderedFormat =
                renderedFormatPathOpt(
                  urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
                  request              = PrintRequest(RenderedFormat.Pdf, PdfRendering.Automatic("en")),
                  defaultLang          = "en"
                )

              // Must also have been generated in the `urls` instance only when needed
              assert(urisByRenderedFormat.isDefined == expectedResult)
            }
          }
        }
      }
    }

    it("#7854: must attach several PDFs in the order of the requested PDF templates") {
      assume(Version.isPE)
      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorServiceFor7854, docFor7854) {

          implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

          // The form has two PDF templates: agreement and confirmation. The PDF filename for this test is defined
          // as concat(fr:pdf-template-name(), '.pdf')

          def emailContentFor(params: ActionParams): EmailContent = {
            clearRenderedFormatsResources()
            process.SimpleProcess.emailsToSend(
              emailDataFormatVersion = DataFormatVersion.Edge,
              templateMatch          = TemplateMatch.First,
              language               = FormRunner.currentLang,
              templateNameOpt        = "default".some,
              pdfRenderings          = pdfRenderingsFor(params)
            ).head
          }

          def pdfAttachments(emailContent: EmailContent): List[Attachment] =
            emailContent.attachments.filter(_.contentType == "application/pdf")

          def attachmentBytes(attachment: Attachment): Array[Byte] =
            useAndClose(attachment.contentFactory().stream)(_.readAllBytes())

          def renderedPdfBytes(templateName: String): Array[Byte] = {
            val (uri, _) =
              renderedFormatPathOpt(
                urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
                request              = PrintRequest(
                  RenderedFormat.Pdf,
                  PdfRendering.Template(PdfTemplate("", Some(templateName), Some("en")))
                ),
                defaultLang          = "en"
              ).get
            Files.readAllBytes(Path.of(uri))
          }

          // Two templates, in the requested order
          locally {
            val attachments = pdfAttachments(emailContentFor(Map(Some(PdfTemplateNamesParam) -> "confirmation agreement")))
            assert(attachments.map(_.filename) == List("confirmation.pdf", "agreement.pdf"))
            assert(attachmentBytes(attachments(0)) sameElements renderedPdfBytes("confirmation"))
            assert(attachmentBytes(attachments(1)) sameElements renderedPdfBytes("agreement"))
            assert(! (attachmentBytes(attachments(0)) sameElements attachmentBytes(attachments(1))))
          }

          // Singular and plural parameters combined, singular first
          locally {
            val params: ActionParams = Map(Some(PdfTemplateNameParam) -> "agreement", Some(PdfTemplateNamesParam) -> "confirmation agreement")
            assert(pdfAttachments(emailContentFor(params)).map(_.filename) == List("agreement.pdf", "confirmation.pdf"))
          }

          // No name requested: single attachment using the default template selection, as before
          assert(pdfAttachments(emailContentFor(Map.empty)).map(_.filename) == List("agreement.pdf"))

          // #7891: test that TIFF attachments work again (regression during XPL/XSL-to-Scala refactoring)
          locally {
            val attachments = emailContentFor(Map(Some(PdfTemplateNamesParam) -> "confirmation agreement")).attachments
            assert(
              attachments.map(_.contentType) ==
                List(
                  ContentTypes.makeContentTypeCharset(ContentTypes.XmlContentType, Some(EmailContent.Charset)),
                  ContentTypes.PdfContentType,
                  ContentTypes.PdfContentType,
                  ContentTypes.TiffContentType,
                  ContentTypes.TiffContentType
                )
            )
            val tiffAttachments = attachments.filter(_.contentType == ContentTypes.TiffContentType)
            assert(tiffAttachments.map(_.filename) == List("confirmation.tiff", "agreement.tiff"))
            // Real TIFF content (little- or big-endian header), and different for each template
            val tiffHeaders = tiffAttachments.map(attachmentBytes).map(_.take(4).toList)
            assert(tiffHeaders.forall(h => h == List[Byte](0x49, 0x49, 0x2a, 0x00) || h == List[Byte](0x4d, 0x4d, 0x00, 0x2a)))
            assert(! (attachmentBytes(tiffAttachments(0)) sameElements attachmentBytes(tiffAttachments(1))))
          }
        }
      }
    }
  }

  // We'll ignore attachments for now (they're tested via S3Test)
  case class ExpectedEmailContent(
    headers              : Set[(HeaderName, String)],
    subject              : String,
    messageContentAsRegex: MessageContent,
  )
}
