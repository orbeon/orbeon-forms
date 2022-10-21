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
import org.orbeon.dom.{Document, Element, QName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.Transform
import org.orbeon.oxf.processor.ProcessorSupport
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.ElementAnalysis.attSet
import org.orbeon.oxf.xforms.analysis.controls.{InstanceMetadataBuilder, LHHA}
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.xbl.XBLAssetsBuilder.HeadElementBuilder
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.{EventNames, HeadElement}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xml.NamespaceMapping

import scala.collection.compat._


trait IndexableBinding {
  def selectors        : List[Selector]
  def namespaceMapping : NamespaceMapping
  def path             : Option[String]
  def lastModified     : Long
}

// Inline binding details, which cannot be shared between forms
case class InlineBindingRef(
  bindingPrefixedId : String,
  selectors         : List[Selector],
  namespaceMapping  : NamespaceMapping
) extends IndexableBinding {
  val path         = None
  val lastModified = -1L
}

object CommonBindingBuilder {

  def apply(
    bindingElem                 : Element, // `<xbl:binding>`
    bindingElemNamespaceMapping : NamespaceMapping,
    directName                  : Option[QName],
    cssName                     : Option[String],
    modelElements               : Seq[Element]
  ): CommonBinding = {

    // XBL modes
    val xblMode         = attSet(bindingElem, XXBL_MODE_QNAME)

    val modeValue               = xblMode("value")
    val modeExternalValue       = modeValue && xblMode("external-value")
    val modeJavaScriptLifecycle = xblMode("javascript-lifecycle")
    val modeLHHA                = xblMode("lhha")
    val modeFocus               = xblMode("focus")

    // LHHA that are handled the standard way (as opposed to the "custom" way)
    val standardLhhaAsSeq: Seq[LHHA] = {

      val modeLHHACustom = modeLHHA && xblMode("custom-lhha")

      LHHA.values flatMap { lhha =>
        ! (
          modeLHHACustom && ! xblMode(s"-custom-${lhha.entryName}") ||
            modeLHHA     &&   xblMode(s"+custom-${lhha.entryName}")
          ) option
            lhha
      }
    }

    // CSS classes to put in the markup
    val cssClasses: String =
      "xbl-component"                                            ::
      (cssName.toList           map  ("xbl-" +))                 :::
      (modeFocus                list "xbl-focusable")            :::
      (modeJavaScriptLifecycle  list "xbl-javascript-lifecycle") :::
      attSet(bindingElem, CLASS_QNAME).toList mkString " "

    val allowedExternalEvents: Set[String] =
      attSet(bindingElem, XXFORMS_EXTERNAL_EVENTS_ATTRIBUTE_NAME)     ++
      (if (modeFocus)         List(XFORMS_FOCUS, XXFORMS_BLUR) else Nil) ++
      (if (modeExternalValue) List(EventNames.XXFormsValue) else Nil)

    // Constant instance DocumentInfo by model and instance index
    // We use the indexes because at this time, no id annotation has taken place yet
    val constantInstances: Map[(Int, Int), DocumentNodeInfoType] = (
      for {
        (m, mi) <- modelElements.zipWithIndex
        (i, ii) <- m.elements(XFORMS_INSTANCE_QNAME).zipWithIndex
        im      = InstanceMetadataBuilder(i, partExposeXPathTypes = false, ElementAnalysis.createLocationData(i))
        if im.readonly && im.useInlineContent
      } yield
        (mi, ii) -> Instance.extractReadonlyDocument(im.inlineRootElemOpt.get, im.excludeResultPrefixes)
    ) toMap

    CommonBinding(
      bindingElemId               = bindingElem.idOpt,
      bindingElemNamespaceMapping = bindingElemNamespaceMapping,
      directName                  = directName,
      cssName                     = cssName,
      containerElementName        = bindingElem.attributeValueOpt(XXBL_CONTAINER_QNAME) getOrElse "div",
      modeBinding                 = xblMode("binding"), // "optional binding" (would need mandatory, optional, and prohibited)
      modeValue                   = modeValue,
      modeExternalValue           = modeExternalValue,
      modeJavaScriptLifecycle     = modeJavaScriptLifecycle,
      modeLHHA                    = modeLHHA,
      modeFocus                   = modeFocus,
      modeItemset                 = xblMode("itemset"), // NIY as of 2019-05-09
      modeSelection               = xblMode("selection"), // to indicate that the control acts as a selection control
      modeHandlers                = ! xblMode("nohandlers"),
      standardLhhaAsSeq           = standardLhhaAsSeq,
      labelFor                    = bindingElem.attributeValueOpt(XXBL_LABEL_FOR_QNAME),
      formatOpt                   = bindingElem.attributeValueOpt(XXBL_FORMAT_QNAME),
      serializeExternalValueOpt   = bindingElem.attributeValueOpt(XXBL_SERIALIZE_EXTERNAL_VALUE_QNAME),
      deserializeExternalValueOpt = bindingElem.attributeValueOpt(XXBL_DESERIALIZE_EXTERNAL_VALUE_QNAME),
      cssClasses                  = cssClasses,
      allowedExternalEvents       = allowedExternalEvents,
      constantInstances           = constantInstances
    )
  }
}

// Holds details of an xbl:xbl/xbl:binding
case class AbstractBinding(
  selectors        : List[Selector],
  bindingElement   : Element,
  path             : Option[String],
  lastModified     : Long,
  scripts          : Seq[HeadElement],
  styles           : Seq[HeadElement],
  handlers         : Seq[Element],
  modelElements    : Seq[Element],
  global           : Option[Document],
  namespaceMapping : NamespaceMapping,
  commonBinding    : CommonBinding
) extends IndexableBinding {

  private val xblMode         = attSet(bindingElement, XXBL_MODE_QNAME)
  val modeValue               = xblMode("value")
  val modeLHHA                = xblMode("lhha")

  def templateElementOpt: Option[Element] = bindingElement.elementOpt(XBL_TEMPLATE_QNAME)
  def supportAVTs       : Boolean = templateElementOpt exists (_.attributeValue(XXBL_AVT_QNAME) == "true")

  private def transformQNameOption = templateElementOpt flatMap
    (_.resolveAttValueQName(XXBL_TRANSFORM_QNAME, unprefixedIsNoNamespace = true))

  private def templateRootOption = templateElementOpt map { e =>
    if (e.jElements.size != 1)
      throw new OXFException("xxbl:transform requires a single child element.")
    e.jElements.get(0)
  }

  private lazy val transformConfig =
    for {
      transformQName <- transformQNameOption
      templateRoot   <- templateRootOption
    } yield
      Transform.createPipelineConfig(transformQName, lastModified) ->
        AbstractBinding.createTransformDomGenerator(templateRoot, lastModified)

  // A transform cannot be reused, so this creates a new one when called, based on the config
  def newTransform(boundElement: Element): Option[Document] = transformConfig map {
    case (pipelineConfig, domGenerator) =>
      // Run the transformation
      val generatedDocument =
        Transform.transformFromPipelineConfig(pipelineConfig, domGenerator, boundElement)

      // Repackage the result
      val generatedRootElement = generatedDocument.getRootElement.detach().asInstanceOf[Element]
      generatedDocument.addElement(QName("template", XBL_NAMESPACE))
      val newRoot = generatedDocument.getRootElement
      newRoot.add(XBL_NAMESPACE)
      newRoot.add(generatedRootElement)

      generatedDocument
  }
}

object AbstractBinding {

  // Create transform input separately to help with namespaces (easier with a separate document)
  // NOTE: We don't create and connect the pipeline here because we don't yet have the data input. Ideally we
  // should have something similar to what the pipeline processor does, with the ability to dynamically connect
  // pipeline inputs and inputs while still allowing caching of the pipeline itself.
  private def createTransformDomGenerator(transform: Element, lastModified: Long): DOMGenerator =
    PipelineUtils.createDOMGenerator(
      transform.createDocumentCopyParentNamespaces(detach = false),
      "xbl-transform-config",
      lastModified,
      ProcessorSupport.makeSystemId(transform)
    )

  def fromBindingElement(
    bindingElem  : Element, // `<xbl:binding>`
    path         : Option[String],
    lastModified : Long,
    scripts      : Seq[HeadElement]
  ): AbstractBinding = {

    assert(bindingElem ne null)

    val styles =
      for {
        resourcesElement <- bindingElem.elements(XBL_RESOURCES_QNAME)
        styleElement     <- resourcesElement.elements(XBL_STYLE_QNAME)
      } yield
        HeadElementBuilder(styleElement)

    val handlers =
      for {
        handlersElement <- bindingElem.elementOpt(XBL_HANDLERS_QNAME).toList
        handlerElement  <- handlersElement.elements(XBL_HANDLER_QNAME)
      } yield
        handlerElement

    val modelElements =
      for {
        implementationElement <- bindingElem.elementOpt(XBL_IMPLEMENTATION_QNAME).toList
        modelElement          <- implementationElement.elements(XFORMS_MODEL_QNAME)
      } yield
        modelElement

    val global = bindingElem.elementOpt(XXBL_GLOBAL_QNAME) map
      (_.createDocumentCopyParentNamespaces(detach = true))

    val selectors =
      CSSSelectorParser.parseSelectors(bindingElem.attributeValue(ELEMENT_QNAME))

    val bindingElemNamespaceMapping = NamespaceMapping(bindingElem.allInScopeNamespacesAsStrings)

    val directName =
      selectors collectFirst BindingDescriptor.directBindingPF(bindingElemNamespaceMapping, None) flatMap (_.elementName)

    // Get CSS name from direct binding if there is one. In the other cases, we won't have a class for now.
    val cssName =
      directName map (_.qualifiedName) map (_.replace(':', '-'))

    AbstractBinding(
      selectors,
      bindingElem,
      path,
      lastModified,
      scripts,
      styles,
      handlers,
      modelElements,
      global,
      bindingElemNamespaceMapping,
      CommonBindingBuilder(bindingElem, bindingElemNamespaceMapping, directName, cssName, modelElements)
    )
  }
}