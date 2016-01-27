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
package org.orbeon.oxf.xforms.analysis.model

import org.dom4j.{Document, Element, QName}
import org.orbeon.oxf.common.{ValidationException, Version}
import org.orbeon.oxf.http.Credentials
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util.ScalaUtils.{CodePointsOps, stringOptionToSet}
import org.orbeon.oxf.util.{Logging, NetUtils, XPath}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, SimpleElementAnalysis, StaticStateContext}
import org.orbeon.oxf.xforms.model.InstanceDataOps
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, ExtendedLocationData}
import org.orbeon.oxf.xml.{Dom4j, TransformerUtils}
import org.orbeon.saxon.dom4j.{DocumentWrapper, TypedDocumentWrapper}
import org.orbeon.saxon.om.DocumentInfo

import scala.collection.JavaConverters._

/**
 * Static analysis of an XForms instance.
 */
class Instance(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends SimpleElementAnalysis(
  staticStateContext,
  element,
  parent,
  preceding,
  scope
) with InstanceMetadata with Logging {

  def partExposeXPathTypes = part.isExposeXPathTypes

  override def extendedLocationData =
    new ExtendedLocationData(
      locationData,
      Some("processing XForms instance"),
      List("id" → staticId),
      Option(element)
    )

  // Get constant inline content from AbstractBinding if possible, otherwise extract from element.
  // Doing so allows for sharing of constant instances globally, among uses of an AbstractBinding and among multiple
  // instances of a given form. This is useful in particular for component i18n resource instances.
  def inlineContent = {

    // An instance within xf:implementation has a ComponentControl grandparent
    def componentForConstantInstances =
      if (readonly && useInlineContent)
        parent.get.parent collect { case component: ComponentControl ⇒ component }
      else
        None

    componentForConstantInstances map { component ⇒

      val modelIndex    = ElementAnalysis.precedingSiblingIterator(parent.get) count (_.localName == "model")
      val instanceIndex = ElementAnalysis.precedingSiblingIterator(this)       count (_.localName == "instance")

      debug("getting readonly inline instance from abstract binding", Seq(
        "model id"       → parent.get.staticId,
        "instance id"    → staticId,
        "scope id"       → component.binding.innerScope.scopeId,
        "binding name"   → component.binding.abstractBinding.debugBindingName,
        "model index"    → modelIndex.toString,
        "instance index" → instanceIndex.toString))

      // Delegate to AbstractBinding
      component.binding.abstractBinding.constantInstances((modelIndex, instanceIndex))
    } getOrElse
      extractInlineContent
  }
}

// Used to gather instance metadata from AbstractBinding
class ThrowawayInstance(val element: Element) extends InstanceMetadata {
  def extendedLocationData = ElementAnalysis.createLocationData(element)
  def partExposeXPathTypes = false
  def inlineContent = extractInlineContent
}

// Separate trait that can also be used by AbstractBinding to extract instance metadata
trait InstanceMetadata {

  def element: Element
  def partExposeXPathTypes: Boolean
  def extendedLocationData: ExtendedLocationData

  import ElementAnalysis._
  import Instance._

  val readonly = element.attributeValue(XXFORMS_READONLY_ATTRIBUTE_QNAME) == "true"
  val cache = Version.instance.isPEFeatureEnabled(element.attributeValue(XXFORMS_CACHE_QNAME) == "true", "cached XForms instance")
  val timeToLive = Instance.timeToLiveOrDefault(element)
  val handleXInclude = false

  // lazy because depends on property, which depends on top-level model being set in XFormsStaticStateImpl!
  lazy val exposeXPathTypes =
    Option(element.attributeValue(XXFORMS_EXPOSE_XPATH_TYPES_QNAME)) map
    (_ == "true") getOrElse
    ! readonly && partExposeXPathTypes

  val (indexIds, indexClasses) = {
    val tokens = attSet(element, XXFORMS_INDEX_QNAME)
    (tokens("id"), tokens("class"))
  }

  private val validation = element.attributeValue(XXFORMS_VALIDATION_QNAME)

  def isLaxValidation    = (validation eq null) || validation == "lax"
  def isStrictValidation = validation == "strict"
  def isSchemaValidation = isLaxValidation || isStrictValidation

  val credentialsOrNull = {
    // NOTE: AVTs not supported because XPath expressions in those could access instances that haven't been loaded
    def username = element.attributeValue(XXFORMS_USERNAME_QNAME)
    def password = element.attributeValue(XXFORMS_PASSWORD_QNAME)
    def preemptiveAuth = element.attributeValue(XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME)
    def domain = element.attributeValue(XXFORMS_DOMAIN_QNAME)

    Option(username) map (Credentials(_, password, preemptiveAuth, domain)) orNull
  }

  val excludeResultPrefixes = stringOptionToSet(Option(element.attributeValue(XXFORMS_EXCLUDE_RESULT_PREFIXES)))

  // Inline root element if any
  private val root = Dom4j.elements(element) headOption
  private def hasInlineContent = root.isDefined

  // Create inline instance document if any
  def inlineContent: DocumentInfo

  // Extract the inline content into a new document (mutable or not)
  protected def extractInlineContent =
    extractDocument(root.get, excludeResultPrefixes, readonly, exposeXPathTypes, removeInstanceData = false)

  // Don't allow more than one child element
  if (Dom4j.elements(element).size > 1)
    throw new ValidationException("xf:instance must contain at most one child element", extendedLocationData)

  private def getAttributeEncode(qName: QName) =
    Option(element.attributeValue(qName)) map (att ⇒ NetUtils.encodeHRRI(att.trimAllToEmpty, true))

  private def src = getAttributeEncode(SRC_QNAME)
  private def resource = getAttributeEncode(RESOURCE_QNAME)

  // @src always wins, @resource always loses
  val useInlineContent = src.isEmpty && hasInlineContent
  val useExternalContent = src.isDefined || ! hasInlineContent && resource.isDefined

  val (instanceSource, dependencyURL) =
    (if (useInlineContent) None else src orElse resource) match {
      case someSource @ Some(source) if ProcessorImpl.isProcessorOutputScheme(source) ⇒
        someSource → None // input:* doesn't add a URL dependency, but is handled by the pipeline engine
      case someSource @ Some(_) ⇒
        someSource → someSource
      case _ ⇒
        None → None
    }

  // Don't allow a blank src attribute
  if (useExternalContent && instanceSource.contains(""))
    throw new ValidationException("xf:instance must not specify a blank URL", extendedLocationData)
}

object Instance {

  def timeToLiveOrDefault(element: Element) = {
    val timeToLiveValue = element.attributeValue(XXFORMS_TIME_TO_LIVE_QNAME)
    Option(timeToLiveValue) map (_.toLong) getOrElse -1L
  }

  // Extract the document starting at the given root element
  // This always creates a copy of the original sub-tree
  //
  // @readonly         if true, the document returned is a compact TinyTree, otherwise a DocumentWrapper
  // @exposeXPathTypes if true, use a TypedDocumentWrapper
  def extractDocument(
    element               : Element,
    excludeResultPrefixes : Set[String],
    readonly              : Boolean,
    exposeXPathTypes      : Boolean,
    removeInstanceData    : Boolean
  ): DocumentInfo = {

    require(! (readonly && exposeXPathTypes)) // we can't expose types on readonly instances at the moment

    // Extract a document and adjust namespaces if requested
    // NOTE: Should implement exactly as per XSLT 2.0
    // NOTE: Should implement namespace fixup, the code below can break serialization
    def extractDocument =
      excludeResultPrefixes match {
        case prefixes if prefixes("#all") ⇒
          // Special #all
          Dom4jUtils.createDocumentCopyElement(element)
        case prefixes if prefixes.nonEmpty ⇒
          // List of prefixes
          Dom4jUtils.createDocumentCopyParentNamespaces(element, prefixes.asJava)
        case _ ⇒
          // No exclusion
          Dom4jUtils.createDocumentCopyParentNamespaces(element)
      }

    if (readonly)
      TransformerUtils.dom4jToTinyTree(XPath.GlobalConfiguration, extractDocument, false)
    else
      wrapDocument(
        if (removeInstanceData)
          InstanceDataOps.removeRecursively(extractDocument)
        else
          extractDocument,
        exposeXPathTypes
      )
  }

  def wrapDocument(document: Document, exposeXPathTypes: Boolean): DocumentWrapper =
    if (exposeXPathTypes)
      new TypedDocumentWrapper(
        Dom4jUtils.normalizeTextNodes(document).asInstanceOf[Document],
        null,
        XPath.GlobalConfiguration
      )
    else
      new DocumentWrapper(
        Dom4jUtils.normalizeTextNodes(document).asInstanceOf[Document],
        null,
        XPath.GlobalConfiguration
      )
}