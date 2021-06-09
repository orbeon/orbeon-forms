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

import org.orbeon.dom.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsContextStackSupport._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis.findChildElem
import org.orbeon.oxf.xforms.analysis.WithChildrenTrait
import org.orbeon.oxf.xforms.analysis.controls.{HeaderControl, WithExpressionOrConstantTrait}
import org.orbeon.xforms.{XFormsId, XFormsNames}

import scala.collection.mutable


object SubmissionHeaders {

  // Evaluate children <xf:header> elements.
  def evaluateHeaders(
    parentEffectiveId : String,                             // effective id of the enclosing `<xf:submission>` or `<xf:output>`
    enclosingElement  : WithChildrenTrait,                  // in practice `<xf:submission>` or `<xf:output>`
    initialHeaders    : Map[String, List[String]])(implicit // initial headers or empty list of headers
    contextStack      : XFormsContextStack                  // context stack set to enclosing <xf:*> element
  ): Map[String, List[String]] = {

    val xblContainer = contextStack.container

    enclosingElement.children collect { case e: HeaderControl => e } match {
      case headerElems if headerElems.nonEmpty =>

        val headerNameValues = mutable.LinkedHashMap[String, List[String]](initialHeaders.toList: _*)

        // Process all nested <header> elements
        for (headerElem <- headerElems) {

          withBinding(headerElem.element, getElementEffectiveId(parentEffectiveId, headerElem), headerElem.scope) { headerElemBindingContext =>

            // Handle a single header element
            def handleHeaderElement(): Unit = {

              def getElementValue(name: QName): Option[String] = {

                val labelOrValueElem =
                  findChildElem(headerElem, name) collect { case e: WithExpressionOrConstantTrait => e } getOrElse
                    (throw new OXFException(s"Missing `<${name.qualifiedName}>` child element of `<xf:header>` element"))

                evaluateExpressionOrConstant(
                  labelOrValueElem,
                  parentEffectiveId,
                  pushContextAndModel = true
                )
              }

              // Evaluate header name and value
              val headerNameOpt  = getElementValue(XFormsNames.XFORMS_NAME_QNAME)
              val headerValueOpt = getElementValue(XFormsNames.XFORMS_VALUE_QNAME)

              // Evaluate combine attribute as AVT
              val combine = {
                val avtCombine = headerElem.element.attributeValueOpt("combine") getOrElse DefaultCombineValue
                val result =
                  XPathCache.evaluateAsAvt(
                    contextStack.getCurrentBindingContext.nodeset,
                    contextStack.getCurrentBindingContext.position,
                    avtCombine,
                    headerElem.namespaceMapping,
                    contextStack.getCurrentBindingContext.getInScopeVariables,
                    xblContainer.getContainingDocument.functionLibrary,
                    contextStack.getFunctionContext(parentEffectiveId),
                    null,
                    headerElem.locationData,
                    xblContainer.getContainingDocument.getRequestStats.getReporter
                  )

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

            if (headerElemBindingContext.newBind) {
              // There is a binding so a possible iteration
              for (position <- 1 to headerElemBindingContext.nodeset.size) {
                withIteration(position) { _ =>
                  handleHeaderElement()
                }
              }
            } else {
              // No binding, this is a single header
              handleHeaderElement()
            }
          }
        }
        headerNameValues.toMap
      case _ =>
        initialHeaders
    }
  }

  val DefaultCombineValue = "append"
  val AllowedCombineValues = Set("append", "prepend", "replace")
}