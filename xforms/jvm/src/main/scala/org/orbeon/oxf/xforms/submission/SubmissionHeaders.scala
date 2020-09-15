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
package org.orbeon.oxf.xforms.submission

import org.orbeon.datatypes.LocationData
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsContextStackSupport.{withBinding, withIteration}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames

import scala.collection.JavaConverters._
import scala.collection.mutable

object SubmissionHeaders {

  // Evaluate children <xf:header> elements.
  def evaluateHeaders(
    xblContainer      : XBLContainer,
    contextStack      : XFormsContextStack,         // context stack set to enclosing <xf:*> element
    sourceEffectiveId : String,                     // effective id of the enclosing <xf:*>element
    enclosingElement  : Element,                    // enclosing <xf:*> element
    initialHeaders    : Map[String, List[String]]   // initial headers or empty list of headers
  ): Map[String, List[String]] = {

    val fullPrefix = xblContainer.getFullPrefix

    val headerElements = enclosingElement.jElements(XFormsNames.XFORMS_HEADER_QNAME)
    if (headerElements.asScala.nonEmpty) {

      val headerNameValues = mutable.LinkedHashMap[String, List[String]](initialHeaders.toList: _*)

      // Handle a single header element
      def handleHeaderElement(headerElement: Element): Unit = {

        def getElementValue(name: QName): Option[String] = {
          val element = headerElement.element(name)
          if (element eq null)
            throw new OXFException(s"Missing <${name.qualifiedName}> child element of <header> element")

          val scope =
            xblContainer.getPartAnalysis.scopeForPrefixedId(fullPrefix + element.idOrNull)

          withBinding(element, sourceEffectiveId, scope) { _ =>
            XFormsElementValue.getElementValue(
              xblContainer,
              contextStack,
              sourceEffectiveId,
              element,
              acceptHTML = false,
              defaultHTML = false,
              null
            )
          }(contextStack)
        }

        // Evaluate header name and value
        val headerNameOpt  = getElementValue(XFormsNames.XFORMS_NAME_QNAME)
        val headerValueOpt = getElementValue(XFormsNames.XFORMS_VALUE_QNAME)

        // Evaluate combine attribute as AVT
        val combine = {
          val avtCombine = headerElement.attributeValueOpt("combine") getOrElse DefaultCombineValue
          val result = XPathCache.evaluateAsAvt(
            contextStack.getCurrentBindingContext.nodeset,
            contextStack.getCurrentBindingContext.position,
            avtCombine,
            xblContainer.getNamespaceMappings(headerElement),
            contextStack.getCurrentBindingContext.getInScopeVariables,
            xblContainer.getContainingDocument.functionLibrary, contextStack.getFunctionContext(sourceEffectiveId),
            null,
            headerElement.getData.asInstanceOf[LocationData],
            xblContainer.getContainingDocument.getRequestStats.getReporter)

          if (! AllowedCombineValues(result))
            throw new OXFException(s"Invalid value '$result' for attribute combine.")

          result
        }

        (headerNameOpt, headerValueOpt) match {
          case (Some(headerName), Some(headerValue)) =>

            // List of existing values (can be empty)
            def existingHeaderValues = headerNameValues.getOrElse(headerName, Nil)

            // Append/prepend/replace
            headerNameValues += headerName -> (combine match {
              case "append"  => existingHeaderValues :+ headerValue
              case "prepend" => headerValue +: existingHeaderValues
              case _         => List(headerValue)
            })
          case _ =>
        }
      }

      // Process all nested <header> elements
      for (headerElement <- headerElements.asScala) {
        val headerScope =
          xblContainer.getPartAnalysis.scopeForPrefixedId(fullPrefix + headerElement.idOrNull)

        contextStack.pushBinding(headerElement, sourceEffectiveId, headerScope)

        if (contextStack.getCurrentBindingContext.newBind) {
          // There is a binding so a possible iteration
          for (position <- 1 to contextStack.getCurrentBindingContext.nodeset.size) {
            withIteration(position) { _ =>
              handleHeaderElement(headerElement)
            }(contextStack)
          }
        } else {
          // No binding, this is a single header
          handleHeaderElement(headerElement)
        }

        contextStack.popBinding()
      }
      headerNameValues.toMap
    } else
      initialHeaders
  }

  val DefaultCombineValue = "append"
  val AllowedCombineValues = Set("append", "prepend", "replace")
}