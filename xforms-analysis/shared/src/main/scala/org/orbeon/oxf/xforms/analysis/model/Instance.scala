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
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, WithChildrenTrait}
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping
import shapeless.syntax.typeable._


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
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope,
  instanceMetadata : InstanceMetadata
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
) {

  selfInstance =>

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
        case _ =>

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

  // Scala 3 would be nice here:
  // `export instanceMetadata._`
  def readonly              : Boolean                  = instanceMetadata.readonly
  def cache                 : Boolean                  = instanceMetadata.cache
  def timeToLive            : Long                     = instanceMetadata.timeToLive
  def handleXInclude        : Boolean                  = instanceMetadata.handleXInclude
  def exposeXPathTypes      : Boolean                  = instanceMetadata.exposeXPathTypes
  def indexIds              : Boolean                  = instanceMetadata.indexIds
  def indexClasses          : Boolean                  = instanceMetadata.indexClasses
  def isLaxValidation       : Boolean                  = instanceMetadata.isLaxValidation
  def isStrictValidation    : Boolean                  = instanceMetadata.isStrictValidation
  def isSchemaValidation    : Boolean                  = instanceMetadata.isSchemaValidation
  def credentials           : Option[BasicCredentials] = instanceMetadata.credentials
  def excludeResultPrefixes : Set[String]              = instanceMetadata.excludeResultPrefixes
  def inlineRootElemOpt     : Option[Element]          = instanceMetadata.inlineRootElemOpt
  def useInlineContent      : Boolean                  = instanceMetadata.useInlineContent
  def useExternalContent    : Boolean                  = instanceMetadata.useExternalContent
  def instanceSource        : Option[String]           = instanceMetadata.instanceSource
  def dependencyURL         : Option[String]           = instanceMetadata.dependencyURL
  def mirror                : Boolean                  = instanceMetadata.mirror
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