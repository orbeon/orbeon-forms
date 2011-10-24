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
import control.{XFormsComponentControl, XFormsControl, XFormsControlFactory}
import org.orbeon.oxf.xforms.XFormsConstants._
import collection.JavaConversions._
import scala.collection.JavaConverters._
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.common.OXFException
import java.util.{Map => JMap}

// Holds details of an xbl:xbl/xbl:binding
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
) {
    def templateElement = Option(bindingElement.element(XBL_TEMPLATE_QNAME))

    private def transformQNameOption = templateElement flatMap
        (e => Option(Dom4jUtils.extractAttributeValueQName(e, XXBL_TRANSFORM_QNAME)))

    private def templateRootOption = templateElement map { e =>
        if (e.elements.size != 1)
            throw new OXFException("xxbl:transform requires a single child element.")
        e.elements.get(0).asInstanceOf[Element]
    }

    private lazy val transformConfig =
        for {
            transformQName <- transformQNameOption
            templateRoot <- templateRootOption
        } yield
            Transform.createTransformConfig(transformQName, templateRoot, lastModified)

    // A transform cannot be reused, so this creates a new one when called, based on the config
    def newTransform(boundElement: Element) = transformConfig map {
        case (pipelineConfig, domGeneratorConfig) =>
            // Run the transformation
            val generatedDocument = Transform.transformBoundElement(pipelineConfig, domGeneratorConfig, boundElement)

            // Repackage the result
            val generatedRootElement = generatedDocument.getRootElement.detach.asInstanceOf[Element]
            generatedDocument.addElement(new QName("template", XFormsConstants.XBL_NAMESPACE, "xbl:template"))
            val newRoot = generatedDocument.getRootElement
            newRoot.add(XFormsConstants.XBL_NAMESPACE)
            newRoot.add(generatedRootElement)

            generatedDocument
    }
    
    def createFactory =
        new XFormsControlFactory.Factory {
            def createXFormsControl(container: XBLContainer, parent: XFormsControl, element: Element, name: String, effectiveId: String, state: JMap[String, Element]) =
                new XFormsComponentControl(container, parent, element, name, effectiveId)
        }
}

object AbstractBinding {
    // Construct an AbstractBinding
    def apply(bindingElement: Element, lastModified: Long, scripts: Seq[Element], namespaceMapping: NamespaceMapping) = {

        assert(bindingElement ne null)

        def extractChildrenModels(parentElement: Element, detach: Boolean): Seq[Document] =
            Dom4jUtils.elements(parentElement, XFORMS_MODEL_QNAME).asScala map
                (Dom4jUtils.createDocumentCopyParentNamespaces(_, detach))

        val bindingId = Option(XFormsUtils.getElementStaticId(bindingElement))

        val styles =
            for {
                resourcesElement <- Dom4jUtils.elements(bindingElement, XBL_RESOURCES_QNAME)
                styleElement <- Dom4jUtils.elements(resourcesElement, XBL_STYLE_QNAME)
            } yield
                styleElement

        val handlers =
            for {
                handlersElement <- Option(bindingElement.element(XBL_HANDLERS_QNAME)).toSeq
                handlerElement <- Dom4jUtils.elements(handlersElement, XBL_HANDLER_QNAME)
            } yield
                handlerElement

        val implementations =
            for {
                implementationElement <- Option(bindingElement.element(XBL_IMPLEMENTATION_QNAME)).toSeq
                modelDocument <- extractChildrenModels(implementationElement, true)
            } yield
                modelDocument

        val global = Option(bindingElement.element(XXBL_GLOBAL_QNAME)) map
            (Dom4jUtils.createDocumentCopyParentNamespaces(_, true))

        new AbstractBinding(qNameMatch(bindingElement, namespaceMapping), bindingElement, lastModified, bindingId, scripts, styles, handlers, implementations, global)
    }

    private def qNameMatch(bindingElement: Element, namespaceMapping: NamespaceMapping) = {
        val elementAttribute = bindingElement.attributeValue(ELEMENT_QNAME)
        Dom4jUtils.extractTextValueQName(namespaceMapping.mapping, elementAttribute.replace('|', ':'), true)
    }

    // Find a cached abstract binding or create and cache a new one
    def findOrCreate(path: Option[String], bindingElement: Element, lastModified: Long, scripts: Seq[Element], namespaceMapping: NamespaceMapping) = {

        val qName = qNameMatch(bindingElement, namespaceMapping)

        path flatMap (BindingCache.get(_, qName, lastModified)) match {
            case Some(cachedBinding) =>
                // Found in cache
                cachedBinding
            case None =>
                val newBinding = AbstractBinding(bindingElement, lastModified, scripts, namespaceMapping)
                // Cache binding
                path foreach (BindingCache.put(_, qName, lastModified, newBinding))
                newBinding
        }
    }
}