/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.Element
import org.orbeon.xforms.HeadElement

import scala.collection.mutable


object XBLAssetsBuilder {

  object HeadElementBuilder {
    def apply(e: Element): HeadElement = {

      val href    = e.attributeValue("href")
      val src     = e.attributeValue("src")
      val resType = e.attributeValue("type")
      val rel     = e.attributeValueOpt("rel") getOrElse "" toLowerCase

      e.getName match {
        case "link" if (href ne null) && ((resType eq null) || resType == "text/css") && rel == "stylesheet" =>
          HeadElement.Reference(href)
        case "style" if (src ne null) && ((resType eq null) || resType == "text/css") =>
          HeadElement.Reference(src)
        case "script" if (src ne null) && ((resType eq null) || resType == "text/javascript") =>
          HeadElement.Reference(src)
        case "style" if src eq null  =>
          HeadElement.Inline(e.getStringValue)
        case "script" if src eq null =>
          HeadElement.Inline(e.getStringValue)
        case _ =>
          throw new IllegalArgumentException(
            s"Invalid element passed to HeadElement(): ${e.toDebugString}"
          )
      }
    }
  }
}
