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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.analysis.controls.*
import org.orbeon.oxf.xforms.analysis.model.*
import org.orbeon.oxf.xforms.xbl.{AbstractBinding, XBLSupport}
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

import scala.collection.mutable


trait PartAnalysisContextImmutable {

  def parent: Option[PartAnalysis]
  def startScope: Scope
  def isTopLevelPart: Boolean

  def staticProperties: XFormsStaticStateStaticProperties

  def metadata: Metadata // NOTE: Using this will have side-effects like XBL registrations!

  def scopeForPrefixedId(prefixedId: String): Scope
  def getNamespaceMapping(prefixedId: String): Option[NamespaceMapping]
}

trait PartAnalysisContextMutable extends TransientState {

  def functionLibrary: FunctionLibrary

  def controlAnalysisMap: mutable.LinkedHashMap[String, ElementAnalysis]
  def controlTypes: mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]]

  def initializeScopes(): Unit
  def newScope(parent: Scope, scopeId: String): Scope
  def mapScopeIds(staticId: String, prefixedId: String, scope: Scope, ignoreIfPresent: Boolean): Unit

  def abstractBindingsWithGlobals: mutable.Buffer[AbstractBinding]
  def allGlobals: mutable.ArrayBuffer[Global]
  def iterateGlobals: Iterator[Global]

  def findControlAnalysis(prefixedId: String): Option[ElementAnalysis] =
    controlAnalysisMap.get(prefixedId)

  def wrapElement(elem: dom.Element): om.NodeInfo =
    new DocumentWrapper(elem.getDocument, null, StaticXPath.GlobalConfiguration).wrap(elem)

  def reportStaticXPathError(expression: String, throwable: Option[Throwable], details: XPathErrorDetails): Unit

  def freeTransientState(): Unit
}

trait PartAnalysisForXblSupport
  extends PartAnalysisContextImmutable
     with PartAnalysisForStaticMetadataAndProperties{

  def xblSupport: Option[XBLSupport]
  def functionLibrary: FunctionLibrary
}

trait PartAnalysisContextForTree
  extends PartAnalysisContextImmutable
     with PartAnalysisContextMutable
     with PartAnalysisForXblSupport

trait PartAnalysisContextAfterTree extends PartAnalysisContextForTree {

  def indexModel(model: Model): Unit
  def registerEventHandlers(eventHandlers: Iterable[EventHandler])(implicit logger: IndentedLogger): Unit
  def gatherScripts(): Unit
  def indexAttributeControls(attributes: Iterable[AttributeControl]): Unit

  def iterateControlsNoModels: Iterator[ElementAnalysis]
  def iterateModels: Iterator[Model]
  def getModel(prefixedId: String): Model
  def getModelByScopeAndBind(scope: Scope, bindStaticId: String): Model
  def getModelByInstancePrefixedId(prefixedId: String): Model
  def findDefaultModelForScope(scope: Scope): Option[Model]
  def findInstancePrefixedId(startScope: Scope, instanceStaticId: String): Option[String]

  def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl
}

trait NestedPartAnalysis
  extends PartAnalysisContextAfterTree
     with PartAnalysis {
  def deindexTree(tree: ElementAnalysis, self: Boolean): Unit
  def deregisterScope(scope: Scope): Unit
  def unmapScopeIds(control: ElementAnalysis): Unit
}

// Context for building the tree, including immutable and mutable parts
case class StaticPartAnalysisImpl(
  staticProperties : XFormsStaticStateStaticProperties,
  parent           : Option[PartAnalysis],
  startScope       : Scope,
  metadata         : Metadata,
  xblSupport       : Option[XBLSupport],
  functionLibrary  : FunctionLibrary
) extends PartAnalysis
     with TopLevelPartAnalysis
     with NestedPartAnalysis
     with PartModelAnalysis
     with PartEventHandlerAnalysis
     with PartEventHandlerScripts
     with PartControlsAnalysis
     with PartControlsAnalysisForUpdate
     with PartXBLAnalysis {

  def isTopLevelPart: Boolean = startScope.isTopLevelScope
  def getNamespaceMapping(prefixedId: String): Option[NamespaceMapping] = metadata.getNamespaceMapping(prefixedId)

  def hasControls: Boolean =
    getTopLevelControls.nonEmpty || iterateGlobals.nonEmpty

  def bindingIncludes: Set[String] =
    metadata.bindingIncludes

  def bindingsIncludesAreUpToDate: Boolean =
    metadata.bindingsIncludesAreUpToDate

  def debugOutOfDateBindingsIncludes: String =
    metadata.debugOutOfDateBindingsIncludes

  /**
   * Return the namespace mappings for a given element. If the element does not have an id, or if the mapping is not
   * cached, compute the mapping on the fly. Note that in this case, the resulting mapping is not added to the cache
   * as the mapping is considered transient and not sharable among pages.
   */
  def getNamespaceMapping(prefix: String, element: dom.Element): NamespaceMapping = {

    val id = element.idOrThrow
    val prefixedId = if (prefix ne null) prefix + id else id

    metadata.getNamespaceMapping(prefixedId) getOrElse
      (throw new IllegalStateException(s"namespace mappings not cached for prefix `$prefix` on element `${element.toDebugString}`"))
  }

  def getMark(prefixedId: String): Option[SAXStore#Mark] = metadata.getMark(prefixedId)

  private var staticXPathErrors: List[(String, Option[Throwable], XPathErrorDetails)] = Nil

  def reportStaticXPathError(expression: String, throwable: Option[Throwable], details: XPathErrorDetails): Unit = {
    staticXPathErrors ::= (expression, throwable, details)
  }

  def getStaticXPathErrors: List[(String, Option[Throwable], XPathErrorDetails)] = staticXPathErrors

  override def freeTransientState(): Unit = super.freeTransientState()
}
