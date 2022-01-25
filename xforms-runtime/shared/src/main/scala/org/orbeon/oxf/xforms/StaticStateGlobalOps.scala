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

import org.orbeon.oxf.xforms.analysis.controls.RepeatControl
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.analysis.{NestedPartAnalysis, PartAnalysis}
import org.orbeon.oxf.xforms.xbl.XBLAssets
import org.orbeon.xforms.HeadElement
import org.orbeon.xforms.xbl.Scope

import scala.collection.{immutable => i}
import scala.jdk.CollectionConverters._

// Global operations on parts including top-level part and descendant parts
class StaticStateGlobalOps(topLevelPart: PartAnalysis) extends PartGlobalOps {

  // Start with top-level part only
  private var parts: List[PartAnalysis] = topLevelPart :: Nil

  // Add part to the list
  def addPart(part: NestedPartAnalysis): Unit =
    parts = part :: parts

  // Remove part from list
  def removePart(part: NestedPartAnalysis): Unit =
    parts = parts filterNot (_ eq part)

  // Find in all parts
  private def findInParts[T <: AnyRef](get: PartAnalysis => T) =
    parts map get find (_ ne null)

  private def findInPartsOpt[T <: AnyRef](get: PartAnalysis => Option[T]) =
    parts flatMap (part => get(part)) headOption

  // Exists in all parts
  private def existsInParts(p: PartAnalysis => Boolean) =
    parts exists p

  // Collect in all parts
  private def collectInParts[T](get: PartAnalysis => Iterable[T]) =
    parts flatMap (part => get(part))

  private def collectInPartsReverse[T](get: PartAnalysis => Iterable[T]) =
    parts.reverse flatMap (part => get(part))

  private def iterateInParts[T](get: PartAnalysis => Iterator[T]) =
    parts.iterator flatMap (part => get(part))

  // Models
  def getModelsForScope(scope: Scope) = collectInParts(_.getModelsForScope(scope))
  def getInstances(modelPrefixedId: String): i.Seq[Instance] = collectInParts(_.getInstances(modelPrefixedId))

  def containingScope(prefixedId: String) = findInParts(_.containingScope(prefixedId)).orNull
  def scopeForPrefixedId(prefixedId: String) = findInParts(_.scopeForPrefixedId(prefixedId)).orNull

  def hasHandlerForEvent(eventName: String) = existsInParts(_.hasHandlerForEvent(eventName))
  def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean) = existsInParts(_.hasHandlerForEvent(eventName, includeAllEvents))
  def keyboardHandlers = collectInParts(_.keyboardHandlers)
//  def getEventHandlersForObserver(observerPrefixedId: String) = collectInParts(_.getEventHandlersForObserver(observerPrefixedId))

  def getMark(prefixedId: String) = findInPartsOpt(_.getMark(prefixedId))

  def findControlAnalysis(prefixedId: String) = findInParts(_.getControlAnalysis(prefixedId))
  def getControlAnalysis(prefixedId: String)  = findControlAnalysis(prefixedId).orNull

  def hasControlByName(controlName: String) = existsInParts(_.hasControlByName(controlName))
  def controlsByName(controlName: String) = collectInParts(_.controlsByName(controlName))

  def repeats = collectInPartsReverse(_.repeats)
  def getRepeatHierarchyString(ns: String) = parts map (_.getRepeatHierarchyString(ns)) mkString "," // just concat the repeat strings from all parts

  def hasAttributeControl(prefixedForAttribute: String) = existsInParts(_.hasAttributeControl(prefixedForAttribute))
  def getAttributeControl(prefixedForAttribute: String, attributeName: String) = findInParts(_.getAttributeControl(prefixedForAttribute, attributeName)).orNull

  def iterateGlobals = iterateInParts(_.iterateGlobals)

  def scriptsByPrefixedId = collectInParts(_.scriptsByPrefixedId) toMap
  def uniqueJsScripts = collectInParts(_.uniqueJsScripts)

  def allXblAssetsMaybeDuplicates: Iterable[XBLAssets] = collectInParts(_.allXblAssetsMaybeDuplicates)

  def baselineResources =
    topLevelPart.baselineResources

  def bindingResources: (List[HeadElement], List[HeadElement]) = {
    val bindings = allXblAssetsMaybeDuplicates
    (XBLAssets.orderedHeadElements(bindings, _.scripts), XBLAssets.orderedHeadElements(bindings, _.styles))
  }

  /**
   * Get prefixed ids of all of the start control's repeat ancestors, stopping at endPrefixedId if not null. If
   * endPrefixedId is a repeat, it is excluded. If the source doesn't exist, return the empty list.
   *
   * @param startPrefixedId   prefixed id of start control or start action within control
   * @param endPrefixedId     prefixed id of end repeat
   * @return                  prefixed ids from leaf to root, or empty
   */
  def getAncestorRepeatIds(startPrefixedId: String, endPrefixedId: Option[String] = None): List[String]  =
    // If element analysis is found, find all its ancestor repeats until the root or until the end prefixed id is
    findControlAnalysis(startPrefixedId).toList flatMap
      (_.ancestorRepeatsAcrossParts) takeWhile
        (a => ! endPrefixedId.contains(a.prefixedId)) map
          (_.prefixedId)

  def getAncestorRepeats(prefixedId: String): List[RepeatControl] =
    getControlAnalysis(prefixedId).ancestorRepeatsAcrossParts

  /**
   * Find the closest common ancestor repeat given two prefixed ids. If the prefixed ids denote repeats, include them
   * as well (as if they were referring to repeat iterations rather than repeats).
   *
   * @param prefixedId1   first control prefixed id
   * @param prefixedId2   second control prefixed id
   * @return              prefixed id of common ancestor repeat
   */
  def findClosestCommonAncestorRepeat(prefixedId1: String, prefixedId2: String): Option[String] = {

    // Starting from the root, find the couples of repeats with identical ids
    val longestPrefix = getAncestorRepeatIds(prefixedId1).reverse zip
      getAncestorRepeatIds(prefixedId2).reverse takeWhile
        { case (left, right) => left == right }

    // Return the id of the last element found
    longestPrefix.lastOption map (_._1)
  }
}