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

import analysis.controls.RepeatControl
import org.dom4j.QName
import collection.mutable.LinkedHashSet
import collection.JavaConverters._
import java.util.{List => JList, Map => JMap}

// Global operations on parts including top-level part and descendant parts
class StaticStateGlobalOps(topLevelPart: PartAnalysis) extends PartGlobalOpsImpl {

    // Start with top-level part only
    private var parts = topLevelPart :: Nil

    def addPart(part: PartAnalysis) =
        parts = part :: parts

    def removePart(part: PartAnalysis) =
        parts = parts filterNot (_ eq part)

    // Find in all parts
    private def findInParts[T <: AnyRef](get: PartAnalysis => T) =
        parts map (part => get(part)) filter (_ ne null) headOption

    private def findInPartsOption[T](get: PartAnalysis => Option[T]) =
        parts flatMap (part => get(part)) headOption

    // Exists in all parts
    private def existsInParts(p: PartAnalysis => Boolean) =
        parts exists (p(_))

    // Collect in all parts
    private def collectInPartsJ[T](get: PartAnalysis => java.util.Collection[T]) =
        parts flatMap (part => get(part).asScala)

    private def collectInParts[T](get: PartAnalysis => Traversable[T]) =
        parts flatMap (part => get(part))

    def getInstances(modelPrefixedId: String) = collectInPartsJ(_.getInstances(modelPrefixedId)).asJava

    def getResolutionScopeByPrefix(prefix: String) = findInParts(_.getResolutionScopeByPrefix(prefix)).orNull
    def getResolutionScopeByPrefixedId(prefixedId: String) = findInParts(_.getResolutionScopeByPrefixedId(prefixedId)).orNull

    def hasHandlerForEvent(eventName: String) = existsInParts(_.hasHandlerForEvent(eventName))
    def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean) = existsInParts(_.hasHandlerForEvent(eventName, includeAllEvents))
    def getKeyHandlers = collectInPartsJ(_.getKeyHandlers).asJava

    def getElementMark(prefixedId: String) = findInParts(_.getElementMark(prefixedId)).orNull

    def getControlAnalysis(prefixedId: String) = findInParts(_.getControlAnalysis(prefixedId)).orNull
    def getAncestorControlForAction(prefixedId: String) = findInPartsOption(_.getAncestorControlForAction(prefixedId))
    def getComponentFactory(qName: QName) = findInParts(_.getComponentFactory(qName)) orNull

    def hasControlByName(controlName: String) = existsInParts(_.hasControlByName(controlName))
    def hasControlAppearance(controlName: String, appearance: QName) = existsInParts(_.hasControlAppearance(controlName, appearance))
    def isComponent(binding: QName) = existsInParts(_.isComponent(binding))
    def getBinding(prefixedId: String) = findInParts(_.getBinding(prefixedId)) orNull
    def getBindingId(prefixedId: String) = findInParts(_.getBindingId(prefixedId)) orNull
    def getBindingQNames = collectInParts(_.getBindingQNames)

    def addMissingRepeatIndexes(repeatPrefixedIdToIndex: JMap[String, java.lang.Integer]) =
        parts foreach (_.addMissingRepeatIndexes(repeatPrefixedIdToIndex))

    def getRepeatHierarchyString = parts map(_.getRepeatHierarchyString) mkString "," // just concat the repeat strings from all parts

    def hasAttributeControl(prefixedForAttribute: String) = existsInParts(_.hasAttributeControl(prefixedForAttribute))
    def getAttributeControl(prefixedForAttribute: String, attributeName: String) = findInParts(_.getAttributeControl(prefixedForAttribute, attributeName)).orNull

    def hasInputPlaceholder = existsInParts(_.hasInputPlaceholder)

    def getGlobals = collectInParts(_.getGlobals) toMap

    def getScripts = parts flatMap (_.getScripts) toMap
    def getXBLStyles = collectInParts(_.getXBLStyles)
    def getXBLScripts = collectInParts(_.getXBLScripts)

    def baselineResources = {
        // Do this imperative-style so we can conserve the order of items in sets
        val allScripts = LinkedHashSet[String]()
        val allStyles = LinkedHashSet[String]()

        parts map (_.baselineResources) foreach {
            case (scripts, styles) =>
                allScripts ++= scripts
                allStyles ++= styles
        }

        (allScripts, allStyles)
    }

    // For Java callers
    def getAncestorRepeatsJava(startPrefixedId: String, endPrefixedId: String): JList[String] =
        getAncestorRepeats(startPrefixedId, Option(endPrefixedId)).asJava

    /**
     * Get prefixed ids of all of the start control's repeat ancestors, stopping at endPrefixedId if not null. If
     * endPrefixedId is a repeat, it is excluded. If the source doesn't exist, return the empty list.
     *
     * @param startPrefixedId   prefixed id of start control or start action within control
     * @param endPrefixedId     prefixed id of end repeat
     * @return                  Seq of prefixed id from leaf to root, or empty
     */
    def getAncestorRepeats(startPrefixedId: String, endPrefixedId: Option[String]): Seq[String] = {
        // Try control analysis first, then action ancestor
        val elementAnalysis = getControlAnalysisOption(startPrefixedId) orElse {
            getAncestorControlForAction(startPrefixedId) flatMap
                (getControlAnalysisOption(_))
        }

        // If element analysis is found, find all its ancestor repeats until the root or until the end prefixed id is
        elementAnalysis.toSeq flatMap
            (RepeatControl.getAllAncestorRepeatAcrossParts(_)) takeWhile
                (a => Some(a.prefixedId) != endPrefixedId) map
                    (_.prefixedId)
    }

    /**
     * Find the closest common ancestor repeat given two prefixed ids.
     *
     * @param prefixedId1   first control prefixed id
     * @param prefixedId2   second control prefixed id
     * @return              prefixed id of common ancestor repeat
     */
    def findClosestCommonAncestorRepeat(prefixedId1: String, prefixedId2: String): Option[String] = {

        def ancestorsFromRoot(prefixedId: String) =
            getAncestorRepeats(prefixedId, None).reverse

        // Starting from the root, find the couples of repeats with identical ids
        val longestPrefix = ancestorsFromRoot(prefixedId1) zip
            (ancestorsFromRoot(prefixedId2)) takeWhile
                (z => z._1 == z._2)

        // Return the id of the last element found
        longestPrefix.lastOption map (_._1)
    }
}