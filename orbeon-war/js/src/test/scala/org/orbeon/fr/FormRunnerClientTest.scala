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
      withRunTomcatContainer("Tomcat", ServerExternalPort, checkImageRunning = true) {
        async {
          val sessionCookie = await(waitForServerCookie(None, OrbeonServerUrl))
          assert(sessionCookie.isSuccess)

          val window =
            FormRunnerWindow(await(loadDocumentViaJSDOM("/fr/tests/control-names/new", OrbeonServerUrl, sessionCookie.toOption)))

          // For the first one, we wait if needed so that the initialization completes. Ideally, we'd use an API to know
          // whether the forms are loaded.
          await {
            eventually(1.second, 10.seconds) {
              assert(window.formRunnerApi.getForm(js.undefined).findControlsByName("first-name").head.classList.contains("xforms-control"))
            }
          }
          assert(window.formRunnerApi.getForm(js.undefined).findControlsByName("last-name").head.classList.contains("xforms-control"))
          assert(window.formRunnerApi.getForm(js.undefined).findControlsByName("i-dont-exist").isEmpty)

          // TODO
//          it("must find the correct iterations for the `name` controls") {
//            assert(
//              List(List(1), List(2)) === (FormRunnerAPI.findControlsByName("name").to(List) map (e => XFormsId.fromEffectiveId(e.id).iterations))
//            )
//          }
        }
      }
    }
  }
}
