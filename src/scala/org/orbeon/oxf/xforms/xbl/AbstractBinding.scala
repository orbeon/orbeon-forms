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
import analysis.ElementAnalysis.attSet
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml.{Dom4j, NamespaceMapping}

// Holds details of an xbl:xbl/xbl:binding
case class AbstractBinding(
    qNameMatch: QName,
    bindingElement: Element,
    lastModified: Long,
    bindingId: Option[String],
    scripts: Seq[Element],
    styles: Seq[Element],
    handlers: Seq[Element],
    implementations: Seq[Element],
    global: Option[Document]
) {
    def templateElement = Option(bindingElement.element(XBL_TEMPLATE_QNAME))

    val containerElementName =          // "div" by default
        Option(bindingElement.attributeValue(XFormsConstants.XXBL_CONTAINER_QNAME)) getOrElse
            "div"

    // Return a CSS-friendly name for this binding (no `|` or `:`)
    def cssName = qNameMatch.getQualifiedName.replace(':', '-')

    val allowedExternalEvents =
        attSet(bindingElement, XFormsConstants.XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME)

    private def transformQNameOption = templateElement flatMap
        (e ⇒ Option(Dom4jUtils.extractAttributeValueQName(e, XXBL_TRANSFORM_QNAME)))

    private def templateRootOption = templateElement map { e ⇒
        if (e.elements.size != 1)
            throw new OXFException("xxbl:transform requires a single child element.")
        e.elements.get(0).asInstanceOf[Element]
    }

    private lazy val transformConfig =
        for {
            transformQName ← transformQNameOption
            templateRoot ← templateRootOption
        } yield
            Transform.createTransformConfig(transformQName, templateRoot, lastModified)

    // A transform cannot be reused, so this creates a new one when called, based on the config
    def newTransform(boundElement: Element) = transformConfig map {
        case (pipelineConfig, domGenerator) ⇒
            // Run the transformation
            val generatedDocument = Transform.transformBoundElement(pipelineConfig, domGenerator, boundElement)

            // Repackage the result
            val generatedRootElement = generatedDocument.getRootElement.detach.asInstanceOf[Element]
            generatedDocument.addElement(new QName("template", XFormsConstants.XBL_NAMESPACE, "xbl:template"))
            val newRoot = generatedDocument.getRootElement
            newRoot.add(XFormsConstants.XBL_NAMESPACE)
            newRoot.add(generatedRootElement)

            generatedDocument
    }

    // XBL mode
    val xblMode = attSet(bindingElement, XFormsConstants.XXBL_MODE_QNAME)
    def modeAllXForms = xblMode("xforms")
    def modeBinding =   modeAllXForms || xblMode("binding")
    def modeValue =     modeAllXForms || xblMode("value")
    def modeUIEvents =  modeAllXForms || xblMode("ui-events")
    def modeLHHA =      modeAllXForms || xblMode("lhha")
    def modeItemset =   modeAllXForms || xblMode("itemset")
    def modeHandlers =  ! xblMode("nohandlers")
}

object AbstractBinding {
    // Construct an AbstractBinding
    def apply(bindingElement: Element, lastModified: Long, scripts: Seq[Element], namespaceMapping: NamespaceMapping) = {

        assert(bindingElement ne null)

        val bindingId = Option(XFormsUtils.getElementId(bindingElement))

        val styles =
            for {
                resourcesElement ← Dom4j.elements(bindingElement, XBL_RESOURCES_QNAME)
                styleElement ← Dom4j.elements(resourcesElement, XBL_STYLE_QNAME)
            } yield
                styleElement

        val handlers =
            for {
                handlersElement ← Option(bindingElement.element(XBL_HANDLERS_QNAME)).toSeq
                handlerElement ← Dom4j.elements(handlersElement, XBL_HANDLER_QNAME)
            } yield
                handlerElement

        val implementations =
            for {
                implementationElement ← Option(bindingElement.element(XBL_IMPLEMENTATION_QNAME)).toSeq
                modelElement ← Dom4j.elements(implementationElement, XFORMS_MODEL_QNAME)
            } yield
                modelElement

        val global = Option(bindingElement.element(XXBL_GLOBAL_QNAME)) map
            (Dom4jUtils.createDocumentCopyParentNamespaces(_, true))

        new AbstractBinding(qNameMatch(bindingElement, namespaceMapping), bindingElement, lastModified, bindingId, scripts, styles, handlers, implementations, global)
    }

    def elementQualifiedName(bindingElement: Element) =
        bindingElement.attributeValue(ELEMENT_QNAME).replace('|', ':')

    private def qNameMatch(bindingElement: Element, namespaceMapping: NamespaceMapping) =
        Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, elementQualifiedName(bindingElement), true)

    // Find a cached abstract binding or create and cache a new one
    def findOrCreate(path: Option[String], bindingElement: Element, lastModified: Long, scripts: Seq[Element], namespaceMapping: NamespaceMapping) = {

        val qName = qNameMatch(bindingElement, namespaceMapping)

        path flatMap (BindingCache.get(_, qName, lastModified)) match {
            case Some(cachedBinding) ⇒
                // Found in cache
                cachedBinding
            case None ⇒
                val newBinding = AbstractBinding(bindingElement, lastModified, scripts, namespaceMapping)
                // Cache binding
                path foreach (BindingCache.put(_, qName, lastModified, newBinding))
                newBinding
        }
    }
}