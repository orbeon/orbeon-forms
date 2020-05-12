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
package org.orbeon.oxf.xforms

import org.orbeon.dom.Element
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.saxon.om

object XFormsContextStackSupport {

  def withBinding[T](
    ref                            : Option[String],
    context                        : Option[String],
    modelId                        : Option[String],
    bindId                         : Option[String],
    bindingElement                 : Element,
    bindingElementNamespaceMapping : NamespaceMapping,
    sourceEffectiveId              : String,
    scope                          : Scope,
    handleNonFatal                 : Boolean
  )(body: => T)(implicit xformsContextStack: XFormsContextStack): T = {
    xformsContextStack.pushBinding(
      ref.orNull,
      context.orNull,
      null,
      modelId.orNull,
      bindId.orNull,
      bindingElement,
      bindingElementNamespaceMapping,
      sourceEffectiveId,
      scope,
      handleNonFatal
    )
    body |!> (_ => xformsContextStack.popBinding())
  }

  def withBinding[T](
    bindingElement    : Element,
    sourceEffectiveId : String,
    scope             : Scope
  )(body: BindingContext => T)(implicit contextStack: XFormsContextStack): T = {
    contextStack.pushBinding(bindingElement, sourceEffectiveId, scope)
    body(contextStack.getCurrentBindingContext) |!>
      (_ => contextStack.popBinding())
  }

  def withIteration[T](currentPosition: Int)(body: om.Item => T)(implicit contextStack: XFormsContextStack): T = {
    contextStack.pushIteration(currentPosition)
    body(contextStack.getCurrentBindingContext.contextItem) |!>
      (_ => contextStack.popBinding())
  }
}
