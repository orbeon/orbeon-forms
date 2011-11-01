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

import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData}
import org.orbeon.oxf.xforms._
import java.util.{LinkedHashMap => JLinkedHashMap, Collections => JCollections}
import scala.collection.JavaConversions._
import org.orbeon.oxf.common.OXFException
import org.dom4j.{QName, Element}

object Headers {
    /**
     * Evaluate children <xforms:header> elements.
     *
     * @param contextStack      context stack set to enclosing <xforms:*> element
     * @param sourceEffectiveId effective id of the enclosing element
     * @param enclosingElement  enclosing element
     * @return map of headers or null if no header elements
     */
    def evaluateHeaders(xblContainer: XBLContainer, contextStack: XFormsContextStack,
                        sourceEffectiveId: String, enclosingElement: Element) = {


        val fullPrefix = xblContainer.getFullPrefix

        val headerElements = Dom4jUtils.elements(enclosingElement, XFormsConstants.XFORMS_HEADER_QNAME)
        if (headerElements.nonEmpty) {

            val headerNameValues = new JLinkedHashMap[String, Array[String]]

            // Handle a single header element
            def handleHeaderElement(headerElement: Element) = {

                def getElementValue(name: QName) = {
                    val element = headerElement.element(name)
                    if (element eq null)
                        throw new OXFException("Missing <" + name.getQualifiedName + "> child element of <header> element")

                    val scope = xblContainer.getPartAnalysis.getResolutionScopeByPrefixedId(xblContainer.getFullPrefix + XFormsUtils.getElementStaticId(element))
                    contextStack.pushBinding(element, sourceEffectiveId, scope)
                    val result = XFormsUtils.getElementValue(xblContainer.getContainingDocument, contextStack, sourceEffectiveId, element, false, null)
                    contextStack.popBinding

                    result
                }

                // Evaluate header name and value
                val headerName = getElementValue(XFormsConstants.XFORMS_NAME_QNAME)
                val headerValue = getElementValue(XFormsConstants.XFORMS_VALUE_QNAME)

                // Evaluate combine attribute as AVT
                val combine = {
                    val avtCombine = headerElement.attributeValue("combine", defaultCombineValue)
                    val result = XFormsUtils.resolveAttributeValueTemplates(contextStack.getCurrentBindingContext.getNodeset,
                        contextStack.getCurrentBindingContext.getPosition, contextStack.getCurrentVariables, XFormsContainingDocument.getFunctionLibrary,
                        contextStack.getFunctionContext(sourceEffectiveId), xblContainer.getContainingDocument.getNamespaceMappings(headerElement),
                        headerElement.getData.asInstanceOf[LocationData], avtCombine)
                    contextStack.returnFunctionContext()

                    if (!allowedCombineValues(result))
                        throw new OXFException("Invalid value '" + result + "' for attribute combine.")

                    result
                }

                // Array of existing values (an empty Array if none)
                val existingHeaderValues = headerNameValues.getOrElse(headerName, Array.empty[String])

                // Append/prepend/replace
                headerNameValues += (headerName -> (combine match {
                    case "append" => existingHeaderValues :+ headerValue
                    case "prepend" => headerValue +: existingHeaderValues
                    case _ => Array(headerValue)
                }))
            }

            // Process all nested <header> elements
            for (headerElement <- headerElements) {
                val headerScope = xblContainer.getPartAnalysis.getResolutionScopeByPrefixedId(fullPrefix + XFormsUtils.getElementStaticId(headerElement))
                contextStack.pushBinding(headerElement, sourceEffectiveId, headerScope)

                if (contextStack.getCurrentBindingContext.isNewBind) {
                    // There is a binding so a possible iteration
                    for (position <- 1 to contextStack.getCurrentNodeset.size) {
                        contextStack.pushIteration(position)
                        handleHeaderElement(headerElement)
                        contextStack.popBinding()
                    }
                } else {
                    // No binding, this is single header
                    handleHeaderElement(headerElement)
                }

                contextStack.popBinding()
            }
            headerNameValues
        } else
            JCollections.emptyMap[String, Array[String]]
    }

    val defaultCombineValue = "append"
    val allowedCombineValues = Set("append", "prepend", "replace")
}