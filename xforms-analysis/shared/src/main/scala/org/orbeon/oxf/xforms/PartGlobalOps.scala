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

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, EventHandler, Global}
import org.orbeon.oxf.xforms.xbl.XBLAssets
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.xbl.Scope

trait PartGlobalOps {

  // Global
  def getMark(prefixedId: String): Option[SAXStore#Mark]

  // Models
  def getModelsForScope(scope: Scope): collection.Seq[Model]
  def getInstances(modelPrefixedId: String): Seq[Instance]

  // Controls
  def getControlAnalysis(prefixedId: String): ElementAnalysis
  def findControlAnalysis(prefixedId: String): Option[ElementAnalysis]
  def controlsByName(controlName: String): Iterable[ElementAnalysis]

  // Events
  def hasHandlerForEvent(eventName: String): Boolean
  def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean
  def keyboardHandlers: Seq[EventHandler]
//  def getEventHandlersForObserver(observerPrefixedId: String): List[EventHandler]

  // XBL
  def iterateGlobals: Iterator[Global]
  def allXblAssetsMaybeDuplicates: Iterable[XBLAssets]

  // Return the scope associated with the given prefixed id (the scope is directly associated with the prefix of the id)
  def containingScope(prefixedId: String): Scope
  def scopeForPrefixedId(prefixedId: String): Scope

  // Repeats
  def repeats: Iterable[RepeatControl]
  def getRepeatHierarchyString(ns: String): String

  // AVTs
  def hasAttributeControl(prefixedForAttribute: String): Boolean
  def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl

  // Client-side assets
  def scriptsByPrefixedId: Map[String, StaticScript]
  def uniqueJsScripts: List[ShareableScript]
  def baselineResources: (List[String], List[String])

  // Functions derived from getControlAnalysis
  def hasBinding(prefixedId: String): Boolean = findControlAnalysis(prefixedId) exists (_.hasBinding)

  def getControlPosition(prefixedId: String): Option[Int] = findControlAnalysis(prefixedId) collect {
    case viewTrait: ViewTrait => viewTrait.index
  }

  def appendClasses(sb: java.lang.StringBuilder, controlAnalysis: ElementAnalysis): Unit = {
    val controlClasses = controlAnalysis.classes
    if (controlClasses.nonAllBlank) {
      if (sb.length > 0)
        sb.append(' ')
      sb.append(controlClasses)
    }
  }

  // LHHA
  def hasLHHA(prefixedId: String, lhha: LHHA): Boolean =
    collectByErasedType[StaticLHHASupport](getControlAnalysis(prefixedId)) exists (_.hasLHHA(lhha))
}