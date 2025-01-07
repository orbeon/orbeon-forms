package org.orbeon.xbl

import enumeratum.EnumEntry.Lowercase
import enumeratum.*
import org.log4s.Logger
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{DocumentAPI, Page}
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.dom.{html, window}

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|


object FriendlyCaptcha {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.FriendlyCaptcha")

  XBL.declareCompanion("fr|friendly-captcha", js.constructorOf[FriendlyCaptchaCompanion])

  private class FriendlyCaptchaCompanion(containerElem: html.Element) extends XBLCompanion {

    import FriendlyCaptchaConfig.StartMode

    private var renderingStarted: Boolean = false
    private var widget: Option[FriendlyCaptchaWidget] = None

    //@JSExport
    def render(publicKey: String, scriptUrl: String, startMode: String, clientEndpoint: String): Unit = {

      logger.debug("render")

      if (! WebSupport.isScriptPresent(scriptUrl))
        containerElem.appendChild(
          dom.document
            .createScriptElement
            .kestrel(_.src = scriptUrl)
        )

      if (! renderingStarted) {
        renderingStarted = true
        renderFriendlyCaptcha(publicKey, StartMode.withNameInsensitiveOption(startMode).getOrElse(StartMode.Auto), clientEndpoint.trimAllToOpt)
      }
    }

    //@JSExport
    def reset(): Unit = {
      logger.debug("reset")
      widget.foreach(_.reset())
    }

    //@JSExport
    override def destroy(): Unit = {
      logger.debug("destroy")
      widget.foreach(_.destroy())
      widget = None
      renderingStarted = false
    }

    private val successfulResponse: js.Function1[String, Unit] = (response: String) => {
      logger.debug(s"sending successful response to server: `$response`")
      val responseControlEffectiveId = containerElem.querySelector(".xbl-fr-friendly-captcha-response").id
      DocumentAPI.setValue(responseControlEffectiveId, response)
    }

    private def renderFriendlyCaptcha(publicKey: String, mode: StartMode, clientEndpoint: Option[String]): Unit = {

      val reCaptchaNotFullyLoaded = {
        val topLevelObject = window.asInstanceOf[js.Dynamic].friendlyChallenge
        js.isUndefined(topLevelObject) ||
        js.isUndefined(topLevelObject.WidgetInstance)
      }

      if (reCaptchaNotFullyLoaded) {
        logger.debug("FriendlyCaptcha not fully loaded, trying again in a moment")
        val shortDelay = Page.getXFormsFormFromHtmlElemOrThrow(containerElem).configuration.internalShortDelay
        js.timers.setTimeout(shortDelay)(renderFriendlyCaptcha(publicKey, mode, clientEndpoint))
      } else {
        widget = Some(
          new FriendlyCaptchaWidget(
            containerElem.querySelector(".xbl-fr-friendly-captcha-div"),
            new FriendlyCaptchaConfig {
              startMode         = mode.entryName
              sitekey           = publicKey
              language          = WebSupport.findHtmlLang.getOrElse("en"): String
              solutionFieldName = "-"
              readyCallback     = js.defined((() => logger.debug("ready callback")): js.Function0[Unit])
              startedCallback   = js.defined((() => logger.debug("started callback")): js.Function0[Unit])
              doneCallback      = js.defined(successfulResponse)
              errorCallback     = js.defined((e => logger.error(s"error callback: `$e`")): js.Function1[String, Unit])
              puzzleEndpoint    = clientEndpoint.getOrElse("https://api.friendlycaptcha.com/api/v1/puzzle"): String // unclear why `js.orUndefined` fails at runtime here
            }
          )
        )
      }
    }
  }
}

@js.native
@JSGlobal("friendlyChallenge.WidgetInstance")
class FriendlyCaptchaWidget(elem: dom.Element, config: FriendlyCaptchaConfig) extends js.Object {
  def start()  : Unit = js.native
  def reset()  : Unit = js.native
  def destroy(): Unit = js.native
}

object FriendlyCaptchaConfig {
  sealed trait StartMode extends EnumEntry with Lowercase
  object StartMode extends Enum[StartMode] {
    def values = findValues
    case object Auto  extends StartMode
    case object Focus extends StartMode
    case object None  extends StartMode
  }
}

trait FriendlyCaptchaConfig extends js.Object {
  var startMode          : js.UndefOr[String]                     = js.undefined // "auto" | "focus" (default) | "none"
  var sitekey            : js.UndefOr[String]                     = js.undefined
  var readyCallback      : js.UndefOr[js.Function0[Unit]]         = js.undefined
  var startedCallback    : js.UndefOr[js.Function0[Unit]]         = js.undefined
  var doneCallback       : js.UndefOr[js.Function1[String, Unit]] = js.undefined
  var errorCallback      : js.UndefOr[js.Function1[String, Unit]] = js.undefined
  var language           : js.UndefOr[String | js.Object]         = js.undefined
  var solutionFieldName  : js.UndefOr[String]                     = js.undefined
  var styleNonce         : js.UndefOr[String]                     = js.undefined
  var puzzleEndpoint     : js.UndefOr[String]                     = js.undefined
  var skipStyleInjection : js.UndefOr[Boolean]                    = js.undefined
}