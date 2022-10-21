package org.orbeon.xbl

import org.orbeon.xforms.{DocumentAPI, Page}
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLScriptElement
import org.scalajs.dom.window

import scala.scalajs.js

object Recaptcha {

  val ComponentName   = "recaptcha"
  var ReCaptchaScript = "https://www.recaptcha.net/recaptcha/api.js"

  XBL.declareCompanion(
    name = s"fr|$ComponentName",
    companion = new XBLCompanion {

      var rendered: Boolean = false

      //@JSExport
      def render(publicKey: String, theme: String): Unit = {

        // Find if the reCAPTCHA has been loaded already
        val alreadyLoaded = {
          // TODO: why if `.scripts` missing? maybe add in a facade
          val scripts = dom.document.asInstanceOf[js.Dynamic].scripts.asInstanceOf[js.Array[HTMLScriptElement]]
          scripts.exists(_.src.startsWith(ReCaptchaScript))
        }

        // Load reCAPTCHA script with appropriate language
        if (! alreadyLoaded) {
          val htmlLangOpt     = Option(dom.document.querySelector("html").getAttribute("lang"))
          val langParameter   = htmlLangOpt.map(lang => s"?hl=$lang").getOrElse("")
          val reCaptchaScript = dom.document.createElement("script").asInstanceOf[HTMLScriptElement]
          reCaptchaScript.src = ReCaptchaScript + langParameter
          containerElem.appendChild(reCaptchaScript)
        }

        if (! rendered) {
            rendered = true
            renderRecaptcha(publicKey, theme)
        }
      }

      def renderRecaptcha(publicKey: String, theme: String): Unit = {

        val grecaptcha = window.asInstanceOf[js.Dynamic].grecaptcha
        val reCaptchaNotFullyLoaded =
          js.isUndefined(grecaptcha)        ||
          js.isUndefined(grecaptcha.render)

        if (reCaptchaNotFullyLoaded) {
          val shortDelay = Page.getFormFromElemOrThrow(containerElem).configuration.internalShortDelay
          js.timers.setTimeout(shortDelay)(renderRecaptcha(publicKey, theme))
        } else {
          val successfulResponse: js.Function1[String, Unit] = (response: String) => {
            val responseId = containerElem.querySelector(".xbl-fr-recaptcha-response").id;
            DocumentAPI.setValue(responseId, response)
          }
          grecaptcha.render(
            containerElem.querySelector(".xbl-fr-recaptcha-div"),
            js.Dictionary(
              "sitekey"  -> publicKey,
              "theme"    -> theme,
              "callback" -> successfulResponse
            )
          )
        }
      }
    }
  )
}
