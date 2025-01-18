package org.orbeon.xbl

import org.orbeon.facades.Bowser
import org.orbeon.web.DomSupport.*
import org.scalajs.dom.html


abstract class XblDateTimeCompanionSupport(containerElem: html.Element)
  extends XBLCompanionWithState(containerElem) {

  protected val isNativePicker: Boolean = {
    val always = containerElem.querySelectorOpt(":scope > .fr-native-picker-always").isDefined
    val iOS    = Bowser.ios.contains(true)
    always || iOS
  }

  protected def readValue(input: html.Input): String =
    input.value

  protected def writeValue(input: html.Input, value: String): Unit =
    input.value = value
}
