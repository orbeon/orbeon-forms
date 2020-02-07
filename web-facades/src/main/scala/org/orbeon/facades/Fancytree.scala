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
package org.orbeon.facades

import org.scalajs.jquery.JQueryPromise

import scala.scalajs.js

@js.native
trait Fancytree extends js.Object {
  val activeNode                : FancytreeNode = js.native
  def activateKey(key: String)  : FancytreeNode = js.native
  def reload(source: js.Object) : JQueryPromise = js.native
}

@js.native
trait FancytreeNode extends js.Object {
  val key: String = js.native
}

@js.native
trait FancytreeJsonNode extends js.Object {
  val title        : String                                  = js.native
  val key          : String                                  = js.native
  val folder       : js.UndefOr[Boolean]                     = js.native
  val children     : js.UndefOr[js.Array[FancytreeJsonNode]] = js.native
  val expanded     : js.UndefOr[Boolean]                     = js.native
  val extraClasses : js.UndefOr[String]                      = js.native
}

@js.native
trait FancytreeEventData extends js.Object {
  val tree       : Fancytree          = js.native
  val node       : FancytreeJsonNode  = js.native
  val targetType : js.UndefOr[String] = js.native
}

object FancytreeJsonNode {
  def apply(
    label           : String,
    value           : String,
    open            : Boolean,
    classesOrUndef  : js.UndefOr[String]                      = js.undefined,
    childrenOrUndef : js.UndefOr[js.Array[FancytreeJsonNode]] = js.undefined
  ): FancytreeJsonNode = {

    val result = (new js.Object).asInstanceOf[js.Dynamic]

    // https://github.com/mar10/fancytree/wiki/TutorialLoadData
    // "expanded, extraClasses, folder, hideCheckbox, key, lazy, selected, title, tooltip, unselectable"

    result.title = label
    result.key   = value

    childrenOrUndef foreach { children =>
      result.folder   = true
      result.children = children
    }

    if (open)
      result.expanded = true

    classesOrUndef foreach { classes =>
      result.extraClasses = classes
    }

    result.asInstanceOf[FancytreeJsonNode]
  }
}