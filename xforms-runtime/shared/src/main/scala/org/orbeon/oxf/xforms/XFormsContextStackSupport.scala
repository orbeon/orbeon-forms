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

import cats.syntax.option._
import org.orbeon.dom.Element
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.WithExpressionOrConstantTrait
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsId


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

  def withContextAndModelOnly[T](
    bindingElem       : ElementAnalysis,
    parentEffectiveId : String
  )(body: BindingContext => T)(implicit contextStack: XFormsContextStack): T = {

    contextStack.pushBinding(
      ref                            = null,
      context                        = bindingElem.context.orNull,
      nodeset                        = null,
      modelId                        = bindingElem.model.map(_.staticId).orNull,
      bindId                         = null,
      bindingElement                 = bindingElem.element,
      bindingElementNamespaceMapping = bindingElem.namespaceMapping,
      sourceEffectiveId              = getElementEffectiveId(parentEffectiveId, bindingElem),
      scope                          = bindingElem.scope,
      handleNonFatal                 = true
    )

    body(contextStack.getCurrentBindingContext) |!>
      (_ => contextStack.popBinding())
  }

  def getElementEffectiveId(parentEffectiveId: String, elem: ElementAnalysis): String =
    XFormsId.buildEffectiveId(elem.prefixedId, XFormsId.getEffectiveIdSuffixParts(parentEffectiveId))

  def evaluateExpressionOrConstant(
    childElem           : WithExpressionOrConstantTrait,
    parentEffectiveId   : String,
    pushContextAndModel : Boolean)(implicit
    contextStack        : XFormsContextStack
  ): Option[String] =
    childElem.expressionOrConstant match {
      case Left(expr)   =>

        def evaluate(currentBindingContext: BindingContext) =
          XPathCache.evaluateAsStringOpt(
            contextItems       = currentBindingContext.nodeset,
            contextPosition    = currentBindingContext.position,
            xpathString        = expr,
            namespaceMapping   = childElem.namespaceMapping,
            variableToValueMap = currentBindingContext.getInScopeVariables,
            functionLibrary    = contextStack.container.getContainingDocument.functionLibrary,
            functionContext    = contextStack.getFunctionContext(getElementEffectiveId(parentEffectiveId, childElem)),
            baseURI            = null,
            locationData       = childElem.locationData,
            reporter           = contextStack.container.getContainingDocument.getRequestStats.getReporter
          )

        if (pushContextAndModel)
          withContextAndModelOnly(childElem, parentEffectiveId)(evaluate)
        else
          evaluate(contextStack.getCurrentBindingContext)
      case Right(constant) =>
        constant.some
    }
}
