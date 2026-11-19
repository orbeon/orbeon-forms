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

import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, WithChildrenTrait}
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import shapeless.syntax.typeable.*


case class InstanceMetadata(
  readonly              : Boolean,
  cache                 : Boolean,
  timeToLive            : Long,
  handleXInclude        : Boolean,
  exposeXPathTypes      : Boolean,
  indexIds              : Boolean,
  indexClasses          : Boolean,
  isLaxValidation       : Boolean,
  isStrictValidation    : Boolean,
  isSchemaValidation    : Boolean,
  credentials           : Option[BasicCredentials],
  excludeResultPrefixes : Set[String],
  inlineRootElemOpt     : Option[Element],
  useInlineContent      : Boolean,
  useExternalContent    : Boolean,
  instanceSource        : Option[String],
  dependencyURL         : Option[String],
  mirror                : Boolean
)

class Instance(
  index                   : Int,
  elem                    : Element,
  parent                  : Option[ElementAnalysis],
  preceding               : Option[ElementAnalysis],
  staticId                : String,
  prefixedId              : String,
  namespaceMapping        : NamespaceMapping,
  scope                   : Scope,
  containerScope          : Scope,
  initialInstanceMetadata : InstanceMetadata
) extends ElementAnalysis(
  index,
  elem,
  parent,
  preceding,
  staticId,
  prefixedId,
  namespaceMapping,
  scope,
  containerScope
) {

  selfInstance =>

  private var _instanceMetadata: InstanceMetadata = initialInstanceMetadata

  // Get constant inline content from `CommonBinding` if possible, otherwise extract from element.
  // Doing so allows for sharing of constant instances globally, among uses of a `CommonBinding` and
  // among multiple instances of a given form. This is useful in particular for component i18n resource
  // instances.
  lazy val constantContent: Option[StaticXPath.DocumentNodeInfoType] =
    readonly && useInlineContent option {

      // An instance within `xf:implementation` has a `ComponentControl` grandparent
      parent flatMap (_.parent) match {
        case Some(component: ComponentControl) =>

          val parentModel = parent.get.narrowTo[WithChildrenTrait].get // TODO: `parent` should be `Option[WithChildrenTrait]`!

          val modelsIt    = component.children.iterator   collect { case m: Model    => m }
          val instancesIt = parentModel.children.iterator collect { case i: Instance => i }

          val modelIndex    = modelsIt.indexWhere(_ eq parentModel)
          val instanceIndex = instancesIt.indexWhere(_ eq selfInstance)

          component.commonBinding.constantInstances((modelIndex, instanceIndex))
        case _ =>
          // FIXME: `get`
          Instance.extractReadonlyDocument(inlineRootElemOpt.get, excludeResultPrefixes)
      }
    }

  // Materialize `constantContent` (`TinyTree`) and release the duplicate Orbeon DOM subtree on server-side static states;
  // for mutable inline instances, copy parent namespaces onto the root element and detach it from the enclosing `<xf:instance>`.
  def releaseInlineDom(): Unit =
    if (readonly && useInlineContent) {
      val _ = constantContent
      _instanceMetadata = _instanceMetadata.copy(inlineRootElemOpt = None)
      element.clearContent()
    } else if (useInlineContent) {
      inlineRootElemOpt.foreach { inlineElem =>
        if (! excludeResultPrefixes("#all")) {
          val savedParentOpt = inlineElem.parentElemOpt
          inlineElem.detach()
          inlineElem.copyMissingNamespacesByPrefix(savedParentOpt, excludeResultPrefixes)
        } else {
          inlineElem.detach()
        }
      }
      element.clearContent()
    }

  // Scala 3 would be nice here:
  // `export _instanceMetadata._`
  def readonly              : Boolean                  = _instanceMetadata.readonly
  def cache                 : Boolean                  = _instanceMetadata.cache
  def timeToLive            : Long                     = _instanceMetadata.timeToLive
  def handleXInclude        : Boolean                  = _instanceMetadata.handleXInclude
  def exposeXPathTypes      : Boolean                  = _instanceMetadata.exposeXPathTypes
  def indexIds              : Boolean                  = _instanceMetadata.indexIds
  def indexClasses          : Boolean                  = _instanceMetadata.indexClasses
  def isLaxValidation       : Boolean                  = _instanceMetadata.isLaxValidation
  def isStrictValidation    : Boolean                  = _instanceMetadata.isStrictValidation
  def isSchemaValidation    : Boolean                  = _instanceMetadata.isSchemaValidation
  def credentials           : Option[BasicCredentials] = _instanceMetadata.credentials
  def excludeResultPrefixes : Set[String]              = _instanceMetadata.excludeResultPrefixes
  def inlineRootElemOpt     : Option[Element]          = _instanceMetadata.inlineRootElemOpt
  def useInlineContent      : Boolean                  = _instanceMetadata.useInlineContent
  def useExternalContent    : Boolean                  = _instanceMetadata.useExternalContent
  def instanceSource        : Option[String]           = _instanceMetadata.instanceSource
  def dependencyURL         : Option[String]           = _instanceMetadata.dependencyURL
  def mirror                : Boolean                  = _instanceMetadata.mirror
}

object Instance {

  // Copy this here as we don't want a dependency on the processor stuff
  private val ProcessorInputScheme  = "input:"
  private val ProcessorOutputScheme = "output:"

  def isProcessorInputScheme(uri: String): Boolean =
    uri.startsWith(ProcessorInputScheme) &&
      (! uri.startsWith(ProcessorInputScheme + "/"))

  def isProcessorOutputScheme(uri: String): Boolean =
    uri.startsWith(ProcessorOutputScheme) &&
      (! uri.startsWith(ProcessorOutputScheme + "/"))

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
  // NOTE: Should implement exactly as per XForms 2.0
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
}