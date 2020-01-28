/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.Constants._
import org.orbeon.xforms.facade.Utils
import org.scalajs.dom
import org.scalajs.dom.raw

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("ORBEON.xforms.server.Server")
object ServerAPI {

  @JSExport
  def callUserScript(
    formId       : String,
    functionName : String,
    targetId     : String,
    observerId   : String,
    rest         : js.Any*
  ): Unit = {

    def getElementOrNull(id: String): raw.Element = {

      def fromId =
        Option(dom.document.getElementById(id))

      def fromRepeat =
        Option(dom.document.getElementById(s"repeat-begin-$id")) // e.g. with `xxforms-nodeset-changed`

      def fromDelimiter =
        id.lastIndexOfOpt(RepeatSeparator) flatMap { lastRepeatSeparatorIndex =>

          val separatorPosition =
            id.lastIndexOfOpt(RepeatIndexSeparator) map (lastRepeatSeparatorIndex max) getOrElse lastRepeatSeparatorIndex

          Option(
            Utils.findRepeatDelimiter(
              formId    = formId,
              repeatId  = id.substring(0, separatorPosition),
              iteration = id.substring(separatorPosition + 1).toInt
            )
          )
        }

      fromId orElse fromRepeat orElse fromDelimiter orNull
    }

    // Don't use `eval` as `Content-Security-Policy` header might block it
    global.selectDynamic(functionName).asInstanceOf[js.Function].call(
      thisArg = getElementOrNull(observerId),
      new js.Object { val target = getElementOrNull(targetId) } +: // `event.target`
      rest                                                         // custom arguments passed with `<xxf:param>` in `<xf:action>`
      : _*                                                         // pass as individual arguments (#3205)
    )
  }
}
