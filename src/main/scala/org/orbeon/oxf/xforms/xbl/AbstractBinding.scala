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

import org.orbeon.css.CSSSelectorParser
import org.orbeon.css.CSSSelectorParser.Selector
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.dom4j.{Document, QName, Element}
import org.orbeon.oxf.xforms._
import analysis.ElementAnalysis.attSet
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.event.XFormsEvents.XFORMS_FOCUS
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.xbl.XBLResources.HeadElement
import org.orbeon.oxf.xforms.analysis.model.ThrowawayInstance
import scala.collection.JavaConverters._

trait IndexableBinding {
    def selectors           : List[Selector]
    def selectorsNamespaces : Map[String, String]
    def path                : Option[String]
    def lastModified        : Long
}

// Inline binding details, which cannot be shared between forms
case class InlineBindingRef(
    bindingPrefixedId   : String,
    selectors           : List[Selector],
    selectorsNamespaces : Map[String, String]
) extends IndexableBinding {
    val path         = None
    val lastModified = -1L
}

// Holds details of an xbl:xbl/xbl:binding
case class AbstractBinding(
    selectors      : List[Selector],
    cssName        : Option[String],
    bindingElement : Element,
    path           : Option[String],
    lastModified   : Long,
    bindingId      : Option[String],
    scripts        : Seq[HeadElement],
    styles         : Seq[HeadElement],
    handlers       : Seq[Element],
    modelElements  : Seq[Element],
    global         : Option[Document]
) extends IndexableBinding {

    val containerElementName =          // "div" by default
        Option(bindingElement.attributeValue(XXBL_CONTAINER_QNAME)) getOrElse
            "div"

    // XBL modes
    private val xblMode = attSet(bindingElement, XXBL_MODE_QNAME)
    def modeBinding     = xblMode("binding") // currently semantic is optional binding but need mandatory, optional, and prohibited
    def modeValue       = xblMode("value")
    def modeLHHA        = xblMode("lhha")
    def modeFocus       = xblMode("focus")
    def modeLHHACustom  = modeLHHA && xblMode("custom-lhha")
    def modeItemset     = xblMode("itemset") // NIY as of 2012-11-20
    def modeHandlers    = ! xblMode("nohandlers")

    val labelFor        = Option(bindingElement.attributeValue(XXBL_LABEL_FOR_QNAME))

    // Printable name for the binding match
    def debugBindingName = bindingElement.getQualifiedName

    // CSS classes to put in the markup
    val cssClasses =
        "xbl-component"                  ::
        (cssName map ("xbl-" +) toList)  :::
        (modeFocus list "xbl-focusable") :::
        attSet(bindingElement, CLASS_QNAME).toList mkString " "

    val allowedExternalEvents =
        attSet(bindingElement, XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME) ++ (modeFocus set XFORMS_FOCUS)

    // Constant instance DocumentInfo by model and instance index
    // We use the indexes because at this time, no id annotation has taken place yet
    val constantInstances = (
        for {
            (m, mi) ← modelElements.zipWithIndex
            (i, ii) ← Dom4j.elements(m, XFORMS_INSTANCE_QNAME).zipWithIndex
            im      = new ThrowawayInstance(i)
            if im.readonly && im.useInlineContent
        } yield
            (mi, ii) → im.inlineContent
    ) toMap

    def templateElement = Option(bindingElement.element(XBL_TEMPLATE_QNAME))
    def supportAVTs     = templateElement exists (_.attributeValue(XXBL_AVT_QNAME) == "true")

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
            generatedDocument.addElement(new QName("template", XBL_NAMESPACE, "xbl:template"))
            val newRoot = generatedDocument.getRootElement
            newRoot.add(XBL_NAMESPACE)
            newRoot.add(generatedRootElement)

            generatedDocument
    }

    def selectorsNamespaces =
        Dom4jUtils.getNamespaceContext(bindingElement).asScala.toMap
}

object AbstractBinding {

    def fromBindingElement(
        bindingElement : Element,
        path           : Option[String],
        lastModified   : Long,
        scripts        : Seq[HeadElement]
    ): AbstractBinding = {

        assert(bindingElement ne null)

        val bindingId = Option(XFormsUtils.getElementId(bindingElement))

        val styles =
            for {
                resourcesElement ← Dom4j.elements(bindingElement, XBL_RESOURCES_QNAME)
                styleElement     ← Dom4j.elements(resourcesElement, XBL_STYLE_QNAME)
            } yield
                HeadElement(styleElement)

        val handlers =
            for {
                handlersElement ← Option(bindingElement.element(XBL_HANDLERS_QNAME)).toSeq
                handlerElement ← Dom4j.elements(handlersElement, XBL_HANDLER_QNAME)
            } yield
                handlerElement

        val modelElements =
            for {
                implementationElement ← Option(bindingElement.element(XBL_IMPLEMENTATION_QNAME)).toSeq
                modelElement ← Dom4j.elements(implementationElement, XFORMS_MODEL_QNAME)
            } yield
                modelElement

        val global = Option(bindingElement.element(XXBL_GLOBAL_QNAME)) map
            (Dom4jUtils.createDocumentCopyParentNamespaces(_, true))

        val selectors =
            CSSSelectorParser.parseSelectors(bindingElement.attributeValue(ELEMENT_QNAME))

        // Get CSS name from direct binding if there is one. In the other cases, we won't have a class for now.
        val cssName = {

            val ns    = Dom4jUtils.getNamespaceContext(bindingElement).asScala.toMap
            val qName = selectors collectFirst BindingDescriptor.directBindingPF(ns, None) flatMap (_.elementName)

            qName map (_.getQualifiedName) map (_.replace(':', '-'))
        }

        AbstractBinding(
            selectors,
            cssName,
            bindingElement,
            path,
            lastModified,
            bindingId,
            scripts,
            styles,
            handlers,
            modelElements,
            global
        )
    }
}