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

import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.dom4j.{Document, QName, Element}
import org.orbeon.oxf.xforms._
import analysis.IdGenerator
import collection.JavaConversions._
import scala.collection.JavaConverters._
import org.orbeon.oxf.xml.NamespaceMapping

case class AbstractBinding(
    qNameMatch: QName,
    bindingElement: Element,
    lastModified: Long,
    bindingId: Option[String],
    scripts: Seq[Element],
    styles: Seq[Element],
    handlers: Seq[Element],
    implementations: Seq[Document],
    global: Option[Document]
)

object AbstractBinding {
    // Construct an AbstractBinding
    def apply(bindingElement: Element, lastModified: Long, scripts: Seq[Element], namespaceMapping: NamespaceMapping, idGenerator: IdGenerator) = {

        assert(bindingElement ne null)

        def extractChildrenModels(parentElement: Element, detach: Boolean): Seq[Document] =
            Dom4jUtils.elements(parentElement, XFormsConstants.XFORMS_MODEL_QNAME).asScala map
                (Dom4jUtils.createDocumentCopyParentNamespaces(_, detach))

        val bindingId = {
            val existingBindingId = XFormsUtils.getElementStaticId(bindingElement)
            Option(existingBindingId) orElse (Option(idGenerator) map (_.getNextId))
        }

        val styles =
            for {
                resourcesElement <- Dom4jUtils.elements(bindingElement, XFormsConstants.XBL_RESOURCES_QNAME)
                styleElement <- Dom4jUtils.elements(resourcesElement, XFormsConstants.XBL_STYLE_QNAME)
            } yield
                styleElement

        val handlers =
            for {
                handlersElement <- Option(bindingElement.element(XFormsConstants.XBL_HANDLERS_QNAME)).toSeq
                handlerElement <- Dom4jUtils.elements(handlersElement, XFormsConstants.XBL_HANDLER_QNAME)
            } yield
                handlerElement

        val implementations =
            for {
                implementationElement <- Option(bindingElement.element(XFormsConstants.XBL_IMPLEMENTATION_QNAME)).toSeq
                modelDocument <- extractChildrenModels(implementationElement, true)
            } yield
                modelDocument

        val global = Option(bindingElement.element(XFormsConstants.XXBL_GLOBAL_QNAME)) map
            (Dom4jUtils.createDocumentCopyParentNamespaces(_, true))

        new AbstractBinding(qNameMatch(bindingElement, namespaceMapping), bindingElement, lastModified, bindingId, scripts, styles, handlers, implementations, global)
    }

    def qNameMatch(bindingElement: Element, namespaceMapping: NamespaceMapping) = {
        val elementAttribute = bindingElement.attributeValue(XFormsConstants.ELEMENT_QNAME)
        Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, elementAttribute.replace('|', ':'), true)
    }
}