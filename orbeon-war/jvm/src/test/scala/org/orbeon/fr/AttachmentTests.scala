package org.orbeon.fr

import com.microsoft.playwright.ElementHandle
import com.microsoft.playwright.options.FilePayload
import org.orbeon.fr.DockerSupport.eventually
import org.orbeon.fr.PlaywrightDomSupport.*
import org.scalatest.funspec.FixtureAnyFunSpecLike

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*


trait AttachmentTests {
  this: FixtureAnyFunSpecLike & ClientTestSupport =>

  import Private.*

  describe("Attachment control tests") {
    it("must show 'Drag file here or select file.' message") { _ =>
      withFormReady(app = "tests", form = "attachment") { case FormRunnerWindow(xformsWindow, _) =>
        val page              = xformsWindow.page
        val attachmentControl = page.querySelectorT(".fr-attachment")
        val messageText       = attachmentControl.textContent()
        assert(messageText.contains("Drag file here or select file."))
      }
    }

    it("must upload file successfully") { _ =>
      withFormReady(app = "tests", form = "attachment") { case FormRunnerWindow(xformsWindow, _) =>
        val page              = xformsWindow.page
        val attachmentControl = page.querySelectorT(".fr-attachment")
        simulateFileUpload(attachmentControl, fileContent = "Hello World")

        eventually(500.millis, 30.seconds) {
          val filenameLink = page.querySelectorOpt(".fr-attachment .fr-attachment-filename a")
          assert(filenameLink.isDefined, "Filename link not yet present")
          assert(filenameLink.get.textContent() == "test.txt")
        }
      }
    }

    it("must show dialog for empty file") { _ =>
      withFormReady(app = "tests", form = "attachment") { case FormRunnerWindow(xformsWindow, _) =>
        val page              = xformsWindow.page
        val attachmentControl = page.querySelectorT(".fr-attachment")
        simulateFileUpload(attachmentControl, fileContent = "")

        eventually(500.millis, 30.seconds) {
          // The dialog tells us that we can't upload empty files.
          val dialog = page.querySelectorOpt("dialog[open]")
          assert(dialog.isDefined, "Error dialog not yet visible")
          val dialogHead = dialog.get.querySelectorT(".xxforms-dialog-head")
          assert(dialogHead.textContent() == "Unable to complete action")
          val dialogBody = dialog.get.querySelectorT("div.xforms-output-output")
          assert(dialogBody.textContent() == "The file test.txt is empty.")
        }

        // The attachment control is still empty.
        val messageText = attachmentControl.textContent()
        assert(messageText.contains("Drag file here or select file."))
      }
    }

    it("must show inline error message for empty file") { _ =>
      withFormReady(app = "tests", form = "attachment") { case FormRunnerWindow(xformsWindow, _) =>
        val page               = xformsWindow.page
        val attachmentControls = page.querySelectorAllT(".fr-attachment")
        val inlineControl      = attachmentControls(1)
        simulateFileUpload(inlineControl, fileContent = "")

        eventually(500.millis, 30.seconds) {
          val inlineMessage = inlineControl.querySelectorOpt(".fr-attachment-upload-error-message")
          assert(inlineMessage.isDefined, "Inline error message not yet visible")
          assert(inlineMessage.get.textContent().contains("The file test.txt is empty."))
        }

        // No dialog is shown.
        val dialog = page.querySelectorOpt("dialog[open]")
        assert(dialog.isEmpty, "Error dialog should not be shown for inline appearance")

        // The attachment control is still empty.
        val messageText = inlineControl.textContent()
        assert(messageText.contains("Drag file here or select file."))
      }
    }
  }

  private object Private {
    def simulateFileUpload(attachmentControl: ElementHandle, fileContent: String): Unit = {
      val fileInputElem = attachmentControl.querySelectorT("input[type='file']")
      fileInputElem.setInputFiles(new FilePayload("test.txt", "text/plain", fileContent.getBytes(StandardCharsets.UTF_8)))
    }
  }
}
