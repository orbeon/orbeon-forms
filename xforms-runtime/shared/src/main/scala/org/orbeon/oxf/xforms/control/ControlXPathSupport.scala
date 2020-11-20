/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import java.{util => ju}

import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.util.{FunctionContext, XPathCache}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.saxon.om
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

trait ControlXPathSupport {

  self: XFormsControl =>

  def getNamespaceMappings: NamespaceMapping =
    if (staticControl ne null) staticControl.namespaceMapping else container.getNamespaceMappings(element)

  def evaluateBooleanAvt(attributeValue: String): Boolean =
    evaluateAvt(attributeValue) == "true"

  /**
   * Evaluate an attribute of the control as an AVT.
   *
   * @param attributeValue    value of the attribute
   * @return                  value of the AVT or null if cannot be computed
   */
  def evaluateAvt(attributeValue: String): String = {

    assert(isRelevant)

    if (! XMLUtils.maybeAVT(attributeValue))
      // Definitely not an AVT
      attributeValue
    else {
      // Possible AVT

      // NOTE: the control may or may not be bound, so don't use getBoundItem()
      val bc = bindingContext
      val contextNodeset = bc.nodeset
      if (contextNodeset.size == 0)
        null // TODO: in the future we should be able to try evaluating anyway
      else {
        try
          XPathCache.evaluateAsAvt(
            contextNodeset,
            bc.position,
            attributeValue,
            getNamespaceMappings,
            bc.getInScopeVariables,
            containingDocument.functionLibrary,
            newFunctionContext,
            null,
            getLocationData,
            containingDocument.getRequestStats.addXPathStat
          )
        catch {
          case NonFatal(t) =>
            XFormsError.handleNonFatalXPathError(container, t, Some(attributeValue))
            null
        }
      }
    }
  }

  // Evaluate an XPath expression as a string in the context of this control.
  // 5 usages
  def evaluateAsString(
    xpathString        : String,
    contextItems       : Seq[om.Item],
    contextPosition    : Int,
    namespaceMapping   : NamespaceMapping                        = getNamespaceMappings,
    variableToValueMap : ju.Map[String, ValueRepresentationType] = bindingContext.getInScopeVariables,
    functionContext    : FunctionContext                         = newFunctionContext
  ): Option[String] = {

    assert(isRelevant)

    // NOTE: the control may or may not be bound, so don't use getBoundNode()
    if (contextItems.isEmpty)
      None
    else {
      // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
      // Reason is that XPath functions might use the context stack to get the current model, etc.
      try
        XPathCache.evaluateAsStringOpt(
          contextItems.asJava,
          contextPosition,
          xpathString,
          namespaceMapping,
          variableToValueMap,
          containingDocument.functionLibrary,
          functionContext,
          null,
          getLocationData,
          containingDocument.getRequestStats.addXPathStat
        )
      catch {
        case NonFatal(t) =>
          XFormsError.handleNonFatalXPathError(container, t, Some(xpathString))
          None
      }
    }
  }

  // Return an XPath function context having this control as source control.
  def newFunctionContext =
    XFormsFunction.Context(container, bindingContext, getEffectiveId, bindingContext.modelOpt, null)
}
