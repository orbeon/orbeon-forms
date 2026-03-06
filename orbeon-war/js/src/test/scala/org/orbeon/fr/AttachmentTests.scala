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
          val window            = xformsWindow.window
          val document          = window.document
          val attachmentControl = document.querySelectorT(".fr-attachment")
          val fileInputElem     = attachmentControl.querySelectorT("input[type='file']").asInstanceOf[html.Input]
          val fileContent       = "Hello World"
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
