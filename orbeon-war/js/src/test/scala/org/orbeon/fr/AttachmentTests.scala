package org.orbeon.fr

import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.FixtureAsyncFunSpecLike

import org.orbeon.oxf.util.FutureUtils.eventually

import scala.async.Async.*
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array


trait AttachmentTests {
  this: FixtureAsyncFunSpecLike & ClientTestSupport =>

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
          val window = xformsWindow.window

          val attachmentControl = document.querySelectorT(".fr-attachment")

          // Find the file input element
          val fileInput = attachmentControl.querySelectorOpt("input[type='file']")
          assert(fileInput.isDefined, "File input element not found")

          val inputElem = fileInput.get.asInstanceOf[html.Input]

          // Create a test file using the File constructor from the window object
          val fileContent = "Hello World"

          // Access the File constructor from the window object (available in JSDOM)
          val FileConstructor = window.asInstanceOf[js.Dynamic].File

          // Create file using the constructor
          val file = js.Dynamic.newInstance(FileConstructor)(
            js.Array(fileContent),
            "test.txt",
            js.Dynamic.literal(`type` = "text/plain", lastModified = js.Date.now())
          ).asInstanceOf[dom.File]

          // Set files property using Object.defineProperty
          val fileList = js.Dynamic.literal()
          fileList.updateDynamic("0")(file)
          fileList.updateDynamic("length")(1)

          window.asInstanceOf[js.Dynamic].Object.defineProperty(
            inputElem,
            "files",
            js.Dynamic.literal(
              value = fileList,
              writable = false
            )
          )

          // Trigger change event to initiate the upload
          val EventConstructor = window.asInstanceOf[js.Dynamic].Event
          val changeEvent = js.Dynamic.newInstance(EventConstructor)(
            "change",
            js.Dynamic.literal(bubbles = true)
          )
          inputElem.asInstanceOf[js.Dynamic].dispatchEvent(changeEvent)

          // Poll until the attachment filename link appears in the DOM
          // (upload HTTP request + Ajax response processing must complete)
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
  }
}
