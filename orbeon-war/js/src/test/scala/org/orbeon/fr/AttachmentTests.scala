package org.orbeon.fr

import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import org.orbeon.oxf.util.FutureUtils.eventually

import scala.async.Async.*
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.scalajs.js


trait AttachmentTests {
  this: FixtureAsyncFunSpecLike & ClientTestSupport =>

  import Private.*

  describe("Attachment control tests") {
    it("must show 'Drag file here or select file.' message") { _ =>
      withFormReady(app = "tests", form = "attachment") { case FormRunnerWindow(xformsWindow, _) =>
        async {
          val document = xformsWindow.window.document
          val attachmentControl = document.querySelectorT(".fr-attachment")
          val messageText = attachmentControl.textContent
          assert(messageText.contains("Drag file here or select file."))
        }
      }
    }

    it("must upload file successfully") { _ =>
      withFormReady(app = "tests", form = "attachment") { case FormRunnerWindow(xformsWindow, _) =>
        async {
          val document = xformsWindow.window.document
          simulateFileUpload(document, fileContent = "Hello World")

          await(eventually(500.millis, 30.seconds) {
            Future {
              val filenameLink = document.querySelectorOpt(".fr-attachment .fr-attachment-filename a")
              assert(filenameLink.isDefined, "Filename link not yet present")
              assert(filenameLink.get.textContent == "test.txt")
            }
          })
        }
      }
    }

    it("must fail uploading an empty file") { _ =>
      withFormReady(app = "tests", form = "attachment") { case FormRunnerWindow(xformsWindow, _) =>
        async {
          val document = xformsWindow.window.document
          simulateFileUpload(document, fileContent = "")

          await(eventually(500.millis, 30.seconds) {
            Future {
              // The dialog tells us that we can't upload empty files.
              val dialog = document.querySelectorOpt("dialog[open]")
              assert(dialog.isDefined, "Error dialog not yet visible")
              val dialogHead = dialog.get.querySelectorT(".xxforms-dialog-head")
              assert(dialogHead.textContent == "Unable to complete action")
              val dialogBody = dialog.get.querySelectorT("div.xforms-output-output")
              assert(dialogBody.textContent == "The file uploaded is empty.")
            }
          })

          // The attachment control is still empty.
          val attachmentControl = document.querySelectorT(".fr-attachment")
          val messageText = attachmentControl.textContent
          assert(messageText.contains("Drag file here or select file."))
        }
      }
    }
  }

  private object Private {
    def simulateFileUpload(document: html.Document, fileContent: String): Unit = {
      val attachmentControl = document.querySelectorT(".fr-attachment")
      val fileInputElem     = attachmentControl.querySelectorT("input[type='file']").asInstanceOf[html.Input]
      val file              = new dom.File(js.Array(fileContent), "test.txt")
      val fileList          = js.Array(file).asInstanceOf[dom.FileList]

      js.Object.defineProperty(
        fileInputElem,
        "files",
        new js.PropertyDescriptor {
          value    = js.defined(fileList)
          writable = false
        }
      )
      assert(fileInputElem.files.length == 1)

      val changeEvent = new dom.Event("change", new dom.EventInit { bubbles = true })
      fileInputElem.dispatchEvent(changeEvent)
    }
  }
}
