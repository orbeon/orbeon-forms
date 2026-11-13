package org.orbeon.fr

import org.orbeon.fr.PlaywrightDomSupport.*
import org.scalatest.funspec.FixtureAnyFunSpecLike


trait RecaptchaTests {
  this: FixtureAnyFunSpecLike & ClientTestSupport =>

  describe("reCAPTCHA v2") {
    it("must block user when unchecked and allow user when checked") { _ =>
      withFormReady(app = "tests", form = "recaptcha") { case FormRunnerWindow(xformsWindow, _) =>

        val page = xformsWindow.page

        assert(page.querySelectorOpt(".fr-mode-new").isDefined)
        assert(page.querySelectorOpt(".xbl-fr-recaptcha.xforms-invalid").isDefined)

        page.querySelectorT(".fr-review-button button").click()
        xformsWindow.ajaxServer.waitForAllEventsProcessed()

        assert(page.querySelectorOpt("#fr-validation-dialog[open]").isDefined)
        assert(page.querySelectorOpt(".fr-mode-new").isDefined)
        assert(page.querySelectorOpt(".fr-mode-view").isEmpty)

        page.querySelectorT("#fr-validation-dialog-cancel button").click()
        xformsWindow.ajaxServer.waitForAllEventsProcessed()

        page.frameLocator(".xbl-fr-recaptcha-div iframe[title='reCAPTCHA']").locator("#recaptcha-anchor").click()
        page.locator(".xbl-fr-recaptcha:not(.xforms-invalid)").waitFor()
        xformsWindow.ajaxServer.waitForAllEventsProcessed()

        page.querySelectorT(".fr-review-button button").click()
        page.locator(".fr-mode-view").waitFor()

        assert(page.querySelectorOpt(".fr-mode-view").isDefined)
      }
    }
  }
}
