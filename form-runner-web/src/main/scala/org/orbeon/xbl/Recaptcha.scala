package org.orbeon.xbl

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{DocumentAPI, Page}
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLScriptElement
import org.scalajs.dom.{html, window}

import scala.scalajs.js


object Recaptcha {

  private val ReCaptchaScript = "https://www.recaptcha.net/recaptcha/api.js"

  XBL.declareCompanion("fr|recaptcha", js.constructorOf[RecaptchaCompanion])

  private class RecaptchaCompanion(containerElem: html.Element) extends XBLCompanion {

    private var renderingStarted: Boolean = false

    //@JSExport
    def render(publicKeyV2: String, publicKeyV3: String, theme: String): Unit = {

      // Some people seem to have problems using both reCAPTCHA v2 and v3 on the same page. As of 2024-06-19, no problem
      // has been observed with our current implementation. For reference, here is some code that could help in case of
      // issues: https://stackoverflow.com/questions/53184795/both-recaptcha-v2-and-v3-on-same-page/78479621#78479621

      val publicKeyV2Opt = Option(publicKeyV2).filter(_.nonEmpty)
      val publicKeyV3Opt = Option(publicKeyV3).filter(_.nonEmpty)

      // Load reCAPTCHA script with appropriate language
      if (! WebSupport.isScriptPresent(ReCaptchaScript)) {

        val langParameterSeq = WebSupport.findHtmlLang.map(("hl"    , _)).toSeq
        val v3SiteKeySeq     = publicKeyV3Opt         .map(("render", _)).toSeq

        containerElem.appendChild(
          dom.document
            .createElement("script").asInstanceOf[HTMLScriptElement]
            |!> (_.src = PathUtils.recombineQuery(ReCaptchaScript, langParameterSeq ++ v3SiteKeySeq))
        )
      }

      if (! renderingStarted) {
        renderingStarted = true
        publicKeyV2Opt.foreach(renderRecaptcha(_, theme) )
      }
    }

    //@JSExport
    def execute(publicKeyV3: String): Unit =
      grecaptcha.execute(publicKeyV3, js.Dictionary("action"  -> "submit")).`then`(successfulResponse)

    //@JSExport
    def reset(): Unit =
      grecaptcha.reset()

    private def grecaptcha: js.Dynamic = window.asInstanceOf[js.Dynamic].grecaptcha

    private val successfulResponse: js.Function1[String, Unit] = (response: String) => {
      val responseControlEffectiveId = containerElem.querySelector(".xbl-fr-recaptcha-response").id
      DocumentAPI.setValue(responseControlEffectiveId, response)
    }

    private def renderRecaptcha(publicKey: String, theme: String): Unit = {

      val reCaptchaNotFullyLoaded =
        js.isUndefined(grecaptcha)        ||
        js.isUndefined(grecaptcha.render)

      if (reCaptchaNotFullyLoaded) {
        val shortDelay = Page.getXFormsFormFromHtmlElemOrThrow(containerElem).configuration.internalShortDelay
        js.timers.setTimeout(shortDelay)(renderRecaptcha(publicKey, theme))
      } else {
        grecaptcha.render(
          containerElem.querySelector(".xbl-fr-recaptcha-div"),
          // TODO: facade
          js.Dictionary(
            "sitekey"  -> publicKey,
            "theme"    -> theme,
            "callback" -> successfulResponse
          )
        )
      }
    }
  }
}
