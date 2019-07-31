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

import org.orbeon.xforms.$
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom.html

import scala.scalajs.js.Dynamic.{global ⇒ g}


object AutosizeTextarea {

  XBL.declareCompanion(
    "fr|autosize-textarea",
    new XBLCompanion {

      def textarea: html.TextArea =
        $(containerElem).find("textarea")(0).asInstanceOf[html.TextArea]

      override def init(): Unit =
        g.autosize(textarea)

      override def destroy(): Unit =
        g.autosize.destroy(textarea)

      override def xformsUpdateValue(newValue: String): Unit =
        g.autosize.update(textarea)

      override def xformsGetValue(): String = {
        g.autosize.update(textarea)
        textarea.value
      }
    }
  )
}
