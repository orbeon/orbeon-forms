package org.orbeon.fr

import org.orbeon.oxf.util.FutureUtils._
import org.scalatest.funspec.AsyncFunSpec

import scala.async.Async._
import scala.concurrent.duration._
import scala.scalajs.js


class FormRunnerClientTest extends AsyncFunSpec with ClientTestSupport {

  val ServerExternalPort = 8888
  val OrbeonServerUrl    = s"http://localhost:$ServerExternalPort/orbeon"

  describe("Form Runner client tests") {
    it("must find form controls by name") {
      withRunTomcatContainer("FormRunnerTomcat", ServerExternalPort, checkImageRunning = true, network = None) {
        async {

          val sessionCookie = await(waitForServerCookie(None, OrbeonServerUrl))
          assert(sessionCookie.isSuccess)

          val window =
            FormRunnerWindow(await(loadDocumentViaJSDOM("/fr/tests/control-names/new", OrbeonServerUrl, sessionCookie.toOption)))

          lazy val form = window.formRunnerApi.getForm(js.undefined)

          // For the first one, we wait if needed so that the initialization completes. Ideally, we'd use an API to know
          // whether the forms are loaded.
          await {
            eventually(1.second, 10.seconds) {
              assert(form.findControlsByName("first-name").head.classList.contains("xforms-control"))
            }
          }

          assert(form.findControlsByName("last-name").head.classList.contains("xforms-control"))
          assert(form.findControlsByName("i-dont-exist").isEmpty)

          assert(form.findControlsByName("comments").length == 1)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          // Use same value to make sure that we get a resolving `Promise` in this case as well
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)
          await(form.setControlValue("comments", "Hello world!").map(_.toFuture).get)

          assert(form.getControlValue("comments").contains("Hello world!"))

          await(form.activateControl("add-comment").map(_.toFuture).get)

          // Only one "Add Comment" button
          assert(form.activateControl("add-comment", 1).isEmpty)

          assert(form.findControlsByName("comments").length == 2)
          assert(form.findControlsByName("comments").forall(_.classList.contains("xforms-textarea")))

          await(form.setControlValue("comments", "Hello world, again!", 1).map(_.toFuture).get)
          assert(form.getControlValue("comments", 1).contains("Hello world, again!"))

          assert(form.findControlsByName("comments").map(_.id).sameElements(List("message-section≡grid-3-grid≡comments-control⊙1", "message-section≡grid-3-grid≡comments-control⊙2")))
        }
      }
    }
  }
}
