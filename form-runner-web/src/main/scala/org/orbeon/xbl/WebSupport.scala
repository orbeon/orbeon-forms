package org.orbeon.xbl

import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object WebSupport {

  def isScriptPresent(scriptUrlPrefix: String): Boolean =
    dom.document
      .asInstanceOf[js.Dynamic]
      .scripts.asInstanceOf[js.Array[html.Script]] // TODO: why is `.scripts` missing? maybe add in a facade
      .exists(_.src.startsWith(scriptUrlPrefix))

  def findHtmlLang: Option[String] =
    Option(dom.document.documentElement.getAttribute("lang"))
}
