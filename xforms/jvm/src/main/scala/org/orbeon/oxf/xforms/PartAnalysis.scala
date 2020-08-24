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
package org.orbeon.oxf.xforms

import java.util.{List => JList}

import org.orbeon.dom.Element
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, Metadata}
import org.orbeon.oxf.xforms.event.EventHandler
import org.orbeon.xforms.xbl.Scope

trait PartAnalysis extends PartGlobalOps with PartStaticAnalysisOps {

  def getIndentedLogger: IndentedLogger

  def isTopLevel: Boolean
  val parent: Option[PartAnalysis]

  def findInstanceInScope(scope: Scope, instanceStaticId: String): Option[Instance]

  def ancestorIterator: Iterator[PartAnalysis]
  def ancestorOrSelfIterator: Iterator[PartAnalysis]

  def startScope: Scope

  def isExposeXPathTypes: Boolean

  def getEventHandlers(observerPrefixedId: String): Seq[EventHandler]
  def observerHasHandlerForEvent(observerPrefixedId: String, eventName: String): Boolean

  def hasControls: Boolean
  def getTopLevelControls: Seq[ElementAnalysis]
  def getTopLevelControlElements: JList[Element]

  def staticState: XFormsStaticState

  def metadata: Metadata

  def dumpAnalysis()

  // The element in our parent that created the current part
  def elementInParent: Option[ElementAnalysis] =
    parent map (_.getControlAnalysis(startScope.fullPrefix.init)) // `.init` removes the trailing component separator

  def repeatDepthAcrossParts: Int =
    if (repeats.isEmpty) 0 else (repeats map (_.ancestorRepeatsAcrossParts.size)).max + 1
}