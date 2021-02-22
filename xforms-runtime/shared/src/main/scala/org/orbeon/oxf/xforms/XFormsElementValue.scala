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
package org.orbeon.oxf.xforms

import java.{lang => jl}
import cats.syntax.option._
import org.orbeon.datatypes.LocationData
import org.orbeon.dom._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsContextStackSupport._
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.controls.{XFormsOutputControl, XXFormsAttributeControl}
import org.orbeon.oxf.xforms.model.{DataModel, StaticDataModel}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om
import org.orbeon.xforms.{XFormsId, XFormsNames}

import scala.util.control.NonFatal


object XFormsElementValue {

  /**
   * Get the value of an element by trying single-node binding, value attribute, linking attribute, and inline value
   * (including nested XHTML and `xf:output` elements).
   *
   * This may return an HTML string if HTML is accepted and found, or a plain string otherwise.
   *
   * @param container         current XBLContainer
   * @param contextStack      context stack for XPath evaluation
   * @param sourceEffectiveId source effective id for id resolution
   * @param childElement      element to evaluate (xf:label, etc.)
   * @param acceptHTML        whether the result may contain HTML
   * @param containsHTML      whether the result actually contains HTML (null allowed)
   * @return string containing the result of the evaluation, null if evaluation failed (see comments)
   */
  def getElementValue(
    container         : XBLContainer,
    contextStack      : XFormsContextStack,
    sourceEffectiveId : String,
    childElement      : Element,
    acceptHTML        : Boolean,
    defaultHTML       : Boolean,
    containsHTML      : Array[Boolean]
  ): Option[String] = {

    if (containsHTML ne null)
      containsHTML(0) = defaultHTML

    def maybeEscapeValue(value: String): String =
      value match {
        case v if acceptHTML && containsHTML == null => v.escapeXmlMinimal
        case v => v
      }

    val currentBindingContext = contextStack.getCurrentBindingContext

    // Try to get single node binding
    def fromBinding: Option[Option[String]] =
      currentBindingContext.newBind option {
        currentBindingContext.singleItemOpt map DataModel.getValue map maybeEscapeValue
      }

    // Try to get value attribute
    // NOTE: This is an extension attribute not standard in XForms 1.0 or 1.1
    def fromValueAtt: Option[Option[String]]  =
      childElement.attributeValueOpt(XFormsNames.VALUE_QNAME) map { valueAttribute =>
        val currentNodeset = currentBindingContext.nodeset
        if (! currentNodeset.isEmpty) {
          val stringResultOpt =
            try
              XPathCache.evaluateAsStringOpt(
                contextItems       = currentNodeset,
                contextPosition    = currentBindingContext.position,
                xpathString        = valueAttribute,
                namespaceMapping   = container.getNamespaceMappings(childElement),
                variableToValueMap = contextStack.getCurrentBindingContext.getInScopeVariables,
                functionLibrary    = container.getContainingDocument.functionLibrary,
                functionContext    = contextStack.getFunctionContext(sourceEffectiveId),
                baseURI            = null,
                locationData       = childElement.getData.asInstanceOf[LocationData],
                reporter           = container.getContainingDocument.getRequestStats.getReporter
              )
            catch {
              case NonFatal(t) =>
                XFormsError.handleNonFatalXPathError(container, t, Some(valueAttribute))
                "".some
            }

          stringResultOpt map maybeEscapeValue
        } else
          // There is a value attribute but the evaluation context is empty
          None
      }

    def fromNestedContent: Option[String] = {
      val sb = new jl.StringBuilder(20)
      childElement.visitDescendants(
        new LHHAElementVisitorListener(
          container         = container,
          contextStack      = contextStack,
          sourceEffectiveId = sourceEffectiveId,
          acceptHTML        = acceptHTML,
          defaultHTML       = defaultHTML,
          containsHTML      = containsHTML,
          sb                = sb,
          childElement      = childElement
        ),
        mutable = false
      )

      maybeEscapeValue(sb.toString).some
    }

    fromBinding orElse fromValueAtt getOrElse fromNestedContent
  }

  private class LHHAElementVisitorListener private (
    prefix            : String,
    container         : XBLContainer,
    contextStack      : XFormsContextStack,
    sourceEffectiveId : String,
    acceptHTML        : Boolean,
    defaultHTML       : Boolean,
    containsHTML      : Array[Boolean],
    sb                : jl.StringBuilder,
    childElement      : Element,
    hostLanguageAVTs  : Boolean,
  ) extends VisitorListener {

    thisLHHAElementVisitorListener =>

    // Constructor for "dynamic" case, i.e. when we know the child element can have dynamic content
    def this(
      container         : XBLContainer,
      contextStack      : XFormsContextStack,
      sourceEffectiveId : String,
      acceptHTML        : Boolean,
      defaultHTML       : Boolean,
      containsHTML      : Array[Boolean],
      sb                : jl.StringBuilder,
      childElement      : Element
    ) {
      this(
        prefix            = container.getFullPrefix,
        container         = container,
        contextStack      = contextStack,
        sourceEffectiveId = sourceEffectiveId,
        acceptHTML        = acceptHTML,
        defaultHTML       = defaultHTML,
        containsHTML      = containsHTML,
        sb                = sb,
        childElement      = childElement,
        hostLanguageAVTs  = XFormsGlobalProperties.isHostLanguageAVTs
      )
    }

    private var lastIsStart = false

    def startElement(element: Element): Unit = {

      implicit val ctxStack: XFormsContextStack = contextStack

      if (element.getQName == XFormsNames.XFORMS_OUTPUT_QNAME) {
        // This is an xf:output nested among other markup
        val outputControl: XFormsOutputControl =
          new XFormsOutputControl(container, null, element, null) {
            // Override this as super.getContextStack() gets the containingDocument's stack, and here we need whatever is the current stack
            // Probably need to modify super.getContextStack() at some point to NOT use the containingDocument's stack
            override def getContextStack: XFormsContextStack =
              thisLHHAElementVisitorListener.contextStack

            override def getEffectiveId: String =
              // Return given source effective id, so we have a source effective id for resolution of index(), etc.
              sourceEffectiveId

            override def isAllowedBoundItem(item: om.Item): Boolean = StaticDataModel.isAllowedBoundItem(item)
          }
        val isHTMLMediatype = ! defaultHTML && LHHAAnalysis.isHTML(element) || defaultHTML && ! LHHAAnalysis.isPlainText(element)
        withBinding(element, sourceEffectiveId, outputControl.getChildElementScope(element)) { bindingContext =>
          outputControl.setBindingContext(
            bindingContext = bindingContext,
            parentRelevant = true,
            update         = false,
            restoreState   = false,
            state          = None
          )
        }
        if (outputControl.isRelevant)
          if (acceptHTML) {
            if (isHTMLMediatype) {
              if (containsHTML != null)
                containsHTML(0) = true // this indicates for sure that there is some nested HTML
              sb.append(outputControl.getExternalValue())
            } else {
              // Mediatype is not HTML so we don't escape
              sb.append(outputControl.getExternalValue().escapeXmlMinimal)
            }
          } else if (isHTMLMediatype) {
            // HTML is not allowed here, better tell the user
            throw new OXFException("HTML not allowed within element: " + childElement.getName)
          } else
            sb.append(outputControl.getExternalValue())
      } else {
        // This is a regular element, just serialize the start tag to no namespace
        // If HTML is not allowed here, better tell the user
        if (!acceptHTML)
          throw new OXFException("Nested XHTML or XForms not allowed within element: " + childElement.getName)
        if (containsHTML != null)
          containsHTML(0) = true
        sb.append('<')
        sb.append(element.getName)
        for (attribute <- element.attributes) {
          val currentAttributeName = attribute.getName
          val currentAttributeValue = attribute.getValue
          val resolvedValue =
            if (hostLanguageAVTs && XMLUtils.maybeAVT(currentAttributeValue)) {
              // This is an AVT, use attribute control to produce the output
              val attributeControl = new XXFormsAttributeControl(container, element, currentAttributeName, currentAttributeValue, element.getName)

              withBinding(element, sourceEffectiveId, attributeControl.getChildElementScope(element)) { bindingContext =>
                attributeControl.setBindingContext(
                  bindingContext = bindingContext,
                  parentRelevant = true,
                  update         = false,
                  restoreState   = false,
                  state          = None
                )
              }
              attributeControl.getExternalValue()
            } else if (currentAttributeName == "id") {
              // https://github.com/orbeon/orbeon-forms/issues/4782
              val it = ctxStack.getCurrentBindingContext.repeatPositions
	      
              if (it.isEmpty)
                prefix + currentAttributeValue
              else
                XFormsId.fromEffectiveId(prefix + currentAttributeValue).copy(iterations = it.toList.reverse).toEffectiveId
            } else {
              // Simply use control value
              currentAttributeValue
            }
          // Only consider attributes in no namespace
          if ("" == attribute.getNamespaceURI) {
            sb.append(' ')
            sb.append(currentAttributeName)
            sb.append("=\"")
            if (resolvedValue != null) sb.append(resolvedValue.escapeXmlMinimal)
            sb.append('"')
          }
        }
        sb.append('>')
        lastIsStart = true
      }
    }

    def endElement(element: Element): Unit = {
      val elementName = element.getName
      if ((! lastIsStart || ! VoidElements(elementName)) && element.getQName != XFormsNames.XFORMS_OUTPUT_QNAME) {
        // This is a regular element, just serialize the end tag to no namespace
        // UNLESS the element was just opened. This means we output `<br>`, not `<br></br>`, etc.
        sb.append("</")
        sb.append(elementName)
        sb.append('>')
      }
      lastIsStart = false
    }

    def text(text: Text): Unit = {
      sb.append(
        if (acceptHTML)
          text.getStringValue.escapeXmlMinimal
        else
          text.getStringValue
      )
      lastIsStart = false
    }
  }
}