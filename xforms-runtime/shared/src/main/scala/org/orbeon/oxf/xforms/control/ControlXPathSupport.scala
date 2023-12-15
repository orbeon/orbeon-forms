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

import org.orbeon.datatypes.LocationData
import cats.syntax.option._

import java.{util => ju}
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.util.{FunctionContext, XPathCache}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.XPathErrorDetails
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


trait ControlXPathSupport {

  self: XFormsControl =>

  def getNamespaceMappings: NamespaceMapping =
    if (staticControl ne null) staticControl.namespaceMapping else container.getNamespaceMappings(element)

  def evaluateBooleanAvt(attributeValue: String, collector: ErrorEventCollector): Boolean =
    evaluateAvt(attributeValue, collector) == "true"

  /**
   * Evaluate an attribute of the control as an AVT.
   *
   * @param attributeValue    value of the attribute
   * @return                  value of the AVT or null if cannot be computed
   */
  def evaluateAvt(attributeValue: String, collector: ErrorEventCollector): String = {

    assert(isRelevant)

    ControlXPathSupport.evaluateAvt(
      attributeValue    = attributeValue,
      bindingContext    = bindingContext,
      namespaceMappings = getNamespaceMappings,
      container         = container,
      locationData      = getLocationData,
      eventTarget       = this,
      collector         = collector
    )(newFunctionContext).orNull
  }

  // Evaluate an XPath expression as a string in the context of this control.
  // 6 usages
  def evaluateAsString(
    xpathString        : String,
    contextItems       : Seq[om.Item],
    contextPosition    : Int,
    collector          : ErrorEventCollector,
    contextMessage     : String,
    namespaceMapping   : NamespaceMapping                        = getNamespaceMappings,
    variableToValueMap : ju.Map[String, ValueRepresentationType] = bindingContext.getInScopeVariables,
    functionContext    : FunctionContext                         = newFunctionContext,
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
          collector(
            new XXFormsXPathErrorEvent(
              target         = this,
              expression     = xpathString,
              details        = XPathErrorDetails.ForOther(contextMessage),
              message        = XFormsCrossPlatformSupport.getRootThrowable(t).getMessage,
              throwable      = t
            )
          )
          None
      }
    }
  }

  // Return an XPath function context having this control as source control.
  def newFunctionContext =
    XFormsFunction.Context(container, bindingContext, getEffectiveId, bindingContext.modelOpt, None)
}

object ControlXPathSupport {

  def evaluateAvt(
    attributeValue   : String,
    bindingContext   : BindingContext,
    namespaceMappings: NamespaceMapping,
    container        : XBLContainer, // used for `PartAnalysis` and `XFCD`
    locationData     : LocationData,
    eventTarget      : XFormsEventTarget,
    collector        : ErrorEventCollector,
  )(implicit xfc: XFormsFunction.Context): Option[String] = {

    if (! XMLUtils.maybeAVT(attributeValue))
      // Definitely not an AVT
      attributeValue.some
    else {
      // Possible AVT

      // NOTE: the control may or may not be bound, so don't use getBoundItem()
      val contextNodeset = bindingContext.nodeset
      if (contextNodeset.size == 0)
        None // TODO: in the future we should be able to try evaluating anyway
      else {
        try
          XPathCache.evaluateAsAvt(
            contextNodeset,
            bindingContext.position,
            attributeValue,
            namespaceMappings,
            bindingContext.getInScopeVariables,
            container.containingDocument.functionLibrary,
            xfc,
            null,
            locationData,
            container.containingDocument.getRequestStats.addXPathStat
          ).some
        catch {
          case NonFatal(t) =>
            collector(
              new XXFormsXPathErrorEvent(
                target         = eventTarget,
                expression     = attributeValue,
                details        = XPathErrorDetails.ForOther("avt"),
                message        = XFormsCrossPlatformSupport.getRootThrowable(t).getMessage,
                throwable      = t
              )
            )
            None
        }
      }
    }
  }
}