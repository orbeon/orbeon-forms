/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.facades.Autosize
import org.orbeon.xforms.$
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom.html

import scala.scalajs.js


object AutosizeTextarea {

  XBL.declareCompanion("fr|autosize-textarea", js.constructorOf[AutosizeTextareaCompanion])

  private class AutosizeTextareaCompanion(containerElem: html.Element) extends XBLCompanion {

    private def textarea: html.TextArea =
      $(containerElem).find("textarea")(0).asInstanceOf[html.TextArea]

    override def init(): Unit =
      Autosize(textarea)

    override def destroy(): Unit =
      Autosize.destroy(textarea)

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      Autosize.update(textarea)
      js.undefined
    }

    // In order to be notified of value updates, we enable the `external-value` mode.
    // This means that we also need to implement this method, even though we don't
    // actually need to store the value independently from the textarea. So we just
    // delegate to the text area. This is required when the server updates client
    // values, in which case we check the current client value for comparison.
    override def xformsGetValue(): String = {
      Autosize.update(textarea)
      textarea.value
    }
  }
}
