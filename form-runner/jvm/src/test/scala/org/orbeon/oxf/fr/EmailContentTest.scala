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
import jakarta.mail.Session
import org.orbeon.oxf.fr.email.EmailMetadata.{HeaderName, TemplateMatch}
import org.orbeon.oxf.fr.email.{MessageContent, SMTP}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.scalatest.funspec.AnyFunSpecLike

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Properties as JProperties


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

    val (processorService, docOpt, _) = runFormRunner("issue", "6848", "new", initialize = true)

    val doc = docOpt.get

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
param3: <ul><li>Email 1: email1@from\.control</li><li>Email 2: email2@from\.control</li><li>Email 3: email3@from\.control</li><li>Email 4: email4@from\.control</li><li>Email 5: email5@from\.control</li><li>Custom 1: Control: custom1</li><li>Custom 2: Control: custom2</li><li>Param 1: Control: param1</li><li>Param 2: Control: param2</li></ul>
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
param14: http://localhost:8080/orbeon/fr/issue/6848/pdf/([a-zA-Z0-9]+)\?form-version=1&fr-access-token=(.+?)$""",
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
                """<li>Param 2: Control: param2</li></ul><br>""" +
                """param4: http://localhost:8080/orbeon/fr/issue/6848/edit/([a-zA-Z0-9]+)\?form-version=1<br>""" +
                """param5: http://localhost:8080/orbeon/fr/issue/6848/edit/([a-zA-Z0-9]+)\?form-version=1&amp;fr-access-token=(.+?)<br>""" +
                """param6: http://localhost:8080/orbeon/fr/issue/6848/view/([a-zA-Z0-9]+)\?form-version=1<br>""" +
                """param7: http://localhost:8080/orbeon/fr/issue/6848/view/([a-zA-Z0-9]+)\?form-version=1&amp;fr-access-token=(.+?)<br>""" +
                """param8: http://localhost:8080/orbeon/fr/issue/6848/new\?form-version=1<br>""" +
                """param9: http://localhost:8080/orbeon/fr/issue/6848/summary\?form-version=1<br>""" +
                """param10: http://localhost:8080/orbeon/fr/<br>""" +
                """param11: http://localhost:8080/orbeon/fr/forms<br>""" +
                """param12: http://localhost:8080/orbeon/fr/admin<br>""" +
                """param13: http://localhost:8080/orbeon/fr/issue/6848/pdf/([a-zA-Z0-9]+)\?form-version=1<br>""" +
                """param14: http://localhost:8080/orbeon/fr/issue/6848/pdf/([a-zA-Z0-9]+)\?form-version=1&amp;fr-access-token=(.+?)</div></body></html>$""",
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
param3: <ul><li>Email 1: email1@from\.control</li><li>Email 3: email3@from\.control</li><li>Email 4: email4@from\.control</li><li>Email 5: email5@from\.control</li><li>Custom 1: Control: custom1</li><li>Param 2: Control: param2</li></ul>$""",
              html    = false
            )
          )
        ),
      )

      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorService, doc) {

          implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

          for ((templateName, expectedResult) <- templatesAndResults) {

            val emailContents = process.SimpleProcess.emailsToSend(
              emailDataFormatVersion = DataFormatVersion.Edge,
              templateMatch          = TemplateMatch.First,
              language               = FormRunner.currentLang,
              templateNameOpt        = templateName.some,
              pdfParams              = Map.empty
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

    it("#7969: must keep header values on one line") {
      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorService, doc) {

          implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

          setControlValue(resolveObject[XFormsValueControl]("custom-1-control").get.effectiveId, "hello\r\nBcc: evil@example.org\nX-Other: injected")

          val emailContent = process.SimpleProcess.emailsToSend(
            emailDataFormatVersion = DataFormatVersion.Edge,
            templateMatch          = TemplateMatch.First,
            language               = FormRunner.currentLang,
            templateNameOpt        = "1".some,
            pdfParams              = Map.empty
          ).head

          // Just check that the newlines are replaced with spaces; without the fix, we would just get raw newlines here
          assert(emailContent.headers.contains(HeaderName.Custom("X-Custom-1st") -> "hello Bcc: evil@example.org X-Other: injected"))

          val headerLines = {
            val bytes = new ByteArrayOutputStream
            SMTP.mimeMessage(Session.getInstance(new JProperties), emailContent).writeTo(bytes)
            bytes.toString(StandardCharsets.UTF_8).split("\r\n").takeWhile(_.nonEmpty).toList
          }

          // Test the fix at the Jakarta Mail serialization level (after a writeTo/toString sequence)
          assert(headerLines.count(_ == "X-Custom-1st: hello Bcc: evil@example.org X-Other: injected") == 1)
          assert(! headerLines.contains("Bcc: evil@example.org"))
          assert(! headerLines.exists(_.startsWith("X-Other:")))
        }
      }
    }

    it("#7971: must escape control values inserted into an HTML body") {
      withTestExternalContext { implicit ec =>
        withFormRunnerDocument(processorService, doc) {

          implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

          val injected = """<b>bold</b> <a href="https://attacker.example/">click</a> & more"""
          val escaped  = """&lt;b&gt;bold&lt;/b&gt; &lt;a href=&quot;https://attacker.example/&quot;&gt;click&lt;/a&gt; &amp; more"""

          setControlValue(resolveObject[XFormsValueControl]("param-1-control").get.effectiveId, injected)

          val body = process.SimpleProcess.emailsToSend(
            emailDataFormatVersion = DataFormatVersion.Edge,
            templateMatch          = TemplateMatch.First,
            language               = FormRunner.currentLang,
            templateNameOpt        = "5".some,
            pdfParams              = Map.empty
          ).head.messageContent.content

          assert(body.contains(s"param1: $escaped<br>"))       // Control Value parameter
          assert(body.contains(s"<li>Param 1: $escaped</li>")) // All Control Values parameter
          assert(! body.contains(injected))
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
