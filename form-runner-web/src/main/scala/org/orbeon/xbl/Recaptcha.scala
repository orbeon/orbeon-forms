package org.orbeon.xbl

import org.orbeon.oxf.util.PathUtils
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{DocumentAPI, Page}
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLScriptElement
import org.scalajs.dom.{html, window}

import scala.scalajs.js


object Recaptcha {

  val ComponentName   = "recaptcha"
  var ReCaptchaScript = "https://www.recaptcha.net/recaptcha/api.js"

  XBL.declareCompanion(s"fr|$ComponentName", js.constructorOf[RecaptchaCompanion])

  private class RecaptchaCompanion(containerElem: html.Element) extends XBLCompanion {

    var rendered: Boolean = false

    //@JSExport
    def render(publicKeyV2: String, publicKeyV3: String, theme: String): Unit = {

      // Some people seem to have problems using both reCAPTCHA v2 and v3 on the same page. As of 2024-06-19, no problem
      // has been observed with out current implementation. For reference, here is some code that could help in case of
      // issues: https://stackoverflow.com/questions/53184795/both-recaptcha-v2-and-v3-on-same-page/78479621#78479621

      val publicKeyV2Opt = Option(publicKeyV2).filter(_.nonEmpty)
      val publicKeyV3Opt = Option(publicKeyV3).filter(_.nonEmpty)

      // Find if the reCAPTCHA has been loaded already
      val alreadyLoaded = {
        // TODO: why if `.scripts` missing? maybe add in a facade
        val scripts = dom.document.asInstanceOf[js.Dynamic].scripts.asInstanceOf[js.Array[HTMLScriptElement]]
        scripts.exists(_.src.startsWith(ReCaptchaScript))
      }

      // Load reCAPTCHA script with appropriate language
      if (! alreadyLoaded) {
        val htmlLangOpt      = Option(dom.document.querySelector("html").getAttribute("lang"))
        val langParameterSeq = htmlLangOpt   .map(("hl"    , _)).toSeq
        val v3SiteKeySeq     = publicKeyV3Opt.map(("render", _)).toSeq
        val reCaptchaScript  = dom.document.createElement("script").asInstanceOf[HTMLScriptElement]
        reCaptchaScript.src  = PathUtils.recombineQuery(ReCaptchaScript, langParameterSeq ++ v3SiteKeySeq)

        containerElem.appendChild(reCaptchaScript)
      }

      if (! rendered) {
        rendered = true
        publicKeyV2Opt.foreach(renderRecaptcha(_, theme) )
      }
    }

    private def grecaptcha() = window.asInstanceOf[js.Dynamic].grecaptcha

    private val successfulResponse: js.Function1[String, Unit] = (response: String) => {
      val responseId = containerElem.querySelector(".xbl-fr-recaptcha-response").id;
      DocumentAPI.setValue(responseId, response)
    }

    private def renderRecaptcha(publicKey: String, theme: String): Unit = {

      val reCaptchaNotFullyLoaded =
        js.isUndefined(grecaptcha())        ||
        js.isUndefined(grecaptcha().render)

      if (reCaptchaNotFullyLoaded) {
        val shortDelay = Page.getXFormsFormFromHtmlElemOrThrow(containerElem).configuration.internalShortDelay
        js.timers.setTimeout(shortDelay)(renderRecaptcha(publicKey, theme))
      } else {
        grecaptcha().render(
          containerElem.querySelector(".xbl-fr-recaptcha-div"),
          js.Dictionary(
            "sitekey"  -> publicKey,
            "theme"    -> theme,
            "callback" -> successfulResponse
          )
        )
      }
    }

    //@JSExport
    def execute(publicKeyV3: String): Unit =
      grecaptcha().execute(publicKeyV3, js.Dictionary("action"  -> "submit")).`then`(successfulResponse)

    def reset(): Unit =
      grecaptcha().reset()
  }
}
