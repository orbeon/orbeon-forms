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

import org.orbeon.datatypes.ExtendedLocationData
import org.orbeon.dom.{Document, Element, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Logging, StaticXPath}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import shapeless.syntax.typeable._



/**
 * Static analysis of an XForms instance.
 */
class Instance(
  index                    : Int,
  element                  : Element,
  parent                   : Option[ElementAnalysis],
  preceding                : Option[ElementAnalysis],
  staticId                 : String,
  prefixedId               : String,
  namespaceMapping         : NamespaceMapping,
  scope                    : Scope,
  containerScope           : Scope,
  val partExposeXPathTypes : Boolean
) extends ElementAnalysis(
  index,
  element,
  parent,
  preceding,
  staticId,
  prefixedId,
  namespaceMapping,
  scope,
  containerScope
) with InstanceMetadata with Logging {

  override def extendedLocationData =
    XmlExtendedLocationData(
      locationData,
      Some("processing XForms instance"),
      List("id" -> staticId),
      Option(element)
    )

  // Get constant inline content from `CommonBinding` if possible, otherwise extract from element.
  // Doing so allows for sharing of constant instances globally, among uses of a `CommonBinding` and
  // among multiple instances of a given form. This is useful in particular for component i18n resource
  // instances.
  lazy val constantContent: Option[StaticXPath.DocumentNodeInfoType] =
    readonly && useInlineContent option {

      // An instance within `xf:implementation` has a `ComponentControl` grandparent
      val componentOpt =
        parent flatMap (_.parent) flatMap ( _.narrowTo[ComponentControl])

      componentOpt match {
        case Some(component) =>

          val modelIndex    = ElementAnalysis.precedingSiblingIterator(parent.get) count (_.localName == XFORMS_MODEL_QNAME.localName)
          val instanceIndex = ElementAnalysis.precedingSiblingIterator(this)       count (_.localName == XFORMS_INSTANCE_QNAME.localName)

//          debug(
//            "getting readonly inline instance from abstract binding",
//            List(
//              "model id"       -> parent.get.staticId,
//              "instance id"    -> staticId,
//              "scope id"       -> (component.bindingOpt map (_.innerScope.scopeId) orNull),
//              "binding name"   -> component.commonBinding.debugBindingName,
//              "model index"    -> modelIndex.toString,
//              "instance index" -> instanceIndex.toString
//            )
//          ) // TODO: pass a logger?

          component.commonBinding.constantInstances((modelIndex, instanceIndex))
        case None =>

//          debug(
//            "getting readonly inline instance from top-level",
//            List(
//              "model id"       -> parent.get.staticId,
//              "instance id"    -> staticId,
//              "scope id"       -> scope.scopeId
//            )
//          ) // TODO: pass a logger?

          // FIXME: `get`
          Instance.extractReadonlyDocument(inlineRootElemOpt.get, excludeResultPrefixes)
      }
    }
}

// Separate trait that can also be used by AbstractBinding to extract instance metadata
trait InstanceMetadata {

  def element: Element
  def partExposeXPathTypes: Boolean
  def extendedLocationData: ExtendedLocationData

  import ElementAnalysis._

  val readonly         = element.attributeValue(XXFORMS_READONLY_ATTRIBUTE_QNAME) == "true"
  val cache            = element.attributeValue(XXFORMS_CACHE_QNAME) == "true"
  val timeToLive       = Instance.timeToLiveOrDefault(element)
  val handleXInclude   = false

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

  val credentials: Option[BasicCredentials] = {
    // NOTE: AVTs not supported because XPath expressions in those could access instances that haven't been loaded
    def username       = element.attributeValue(XXFORMS_USERNAME_QNAME)
    def password       = element.attributeValue(XXFORMS_PASSWORD_QNAME)
    def preemptiveAuth = element.attributeValue(XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME)
    def domain         = element.attributeValue(XXFORMS_DOMAIN_QNAME)

    Option(username) map (BasicCredentials(_, password, preemptiveAuth, domain))
  }

  val excludeResultPrefixes: Set[String] = element.attributeValue(XXFORMS_EXCLUDE_RESULT_PREFIXES).tokenizeToSet

  // Inline root element if any
  // TODO: When not needed, we should not keep a reference on this.
  // TODO: When needed, wwe should just keep a detached template.
  val inlineRootElemOpt: Option[Element] = element.elements.headOption
  private def hasInlineContent = inlineRootElemOpt.isDefined

  // Don't allow more than one child element
  if (element.elements.size > 1)
    throw new ValidationException("xf:instance must contain at most one child element", extendedLocationData)

  private def getAttributeEncode(qName: QName): Option[String] =
    element.attributeValueOpt(qName) map (att => att.trimAllToEmpty.encodeHRRI(processSpace = true))

  private def src: Option[String]      = getAttributeEncode(SRC_QNAME)
  private def resource: Option[String] = getAttributeEncode(RESOURCE_QNAME)

  // `@src` always wins, `@resource` always loses
  val useInlineContent = src.isEmpty && hasInlineContent
  val useExternalContent = src.isDefined || ! hasInlineContent && resource.isDefined

  val (instanceSource, dependencyURL) =
    (if (useInlineContent) None else src orElse resource) match {
      case someSource @ Some(source) if Instance.isProcessorOutputScheme(source) =>
        someSource -> None // input:* doesn't add a URL dependency, but is handled by the pipeline engine
      case someSource @ Some(_) =>
        someSource -> someSource
      case _ =>
        None -> None
    }

  // Don't allow a blank `src` attribute
  if (useExternalContent && instanceSource.exists(_.isAllBlank))
    throw new ValidationException("`xf:instance` must not specify a blank URL", extendedLocationData)
}

object Instance {

  def isProcessorOutputScheme(uri: String): Boolean =
    uri.startsWith(ProcessorOutputScheme) &&
      ! uri.startsWith(ProcessorOutputScheme + "/")

  def timeToLiveOrDefault(element: Element): Long =
    element.attributeValueOpt(XXFORMS_TIME_TO_LIVE_QNAME) flatMap (_.trimAllToOpt) map (_.toLong) getOrElse -1L

  def extractReadonlyDocument(
    element               : Element,
    excludeResultPrefixes : Set[String]
  ): StaticXPath.DocumentNodeInfoType =
    StaticXPath.orbeonDomToTinyTree(
      extractDocHandlePrefixes(element, excludeResultPrefixes)
    )

  // Extract a document and adjust namespaces if requested
  // NOTE: Should implement exactly as per XSLT 2.0
  // NOTE: Should implement namespace fixup, the code below can break serialization
  def extractDocHandlePrefixes(element: Element, excludeResultPrefixes : Set[String]): Document =
    excludeResultPrefixes match {
      case prefixes if prefixes("#all") =>
        // Special #all
        Document(element.createCopy)
      case prefixes if prefixes.nonEmpty =>
        // List of prefixes
        element.createDocumentCopyParentNamespaces(detach = false, prefixesToFilter = prefixes)
      case _ =>
        // No exclusion
        element.createDocumentCopyParentNamespaces(detach = false)
    }

  // Copy this here as we don't want a dependency on the processor stuff
  private val ProcessorOutputScheme = "output:"
}