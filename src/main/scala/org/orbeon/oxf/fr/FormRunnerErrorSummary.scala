/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._


trait FormRunnerErrorSummary {
    // Update the iteration in a control's absolute id
    def updateIteration(absoluteId: String, repeatAbsoluteId: String, fromIterations: Array[Int], toIterations: Array[Int]): String = {

        val effectiveId = absoluteIdToEffectiveId(absoluteId)
        val prefixedId  = getPrefixedId(effectiveId)

        val repeatEffectiveId = absoluteIdToEffectiveId(repeatAbsoluteId)
        val repeatPrefixedId  = getPrefixedId(repeatEffectiveId)

        val ancestorRepeats = containingDocument.getStaticOps.getAncestorRepeats(prefixedId, null)

        if (ancestorRepeats exists (_ == repeatPrefixedId)) {
            // Control is a descendant of the repeat so might be impacted

            val idIterationPairs = getEffectiveIdSuffixParts(effectiveId) zip ancestorRepeats
            val iterationsMap    = fromIterations zip toIterations toMap

            val newIterations = idIterationPairs map {
                case (fromIteration, `repeatPrefixedId`) if iterationsMap.contains(fromIteration) ⇒ iterationsMap(fromIteration).toString.asInstanceOf[AnyRef]
                case (iteration, _)                                                               ⇒ iteration.toString.asInstanceOf[AnyRef]
            }

            val newEffectiveId = buildEffectiveId(prefixedId, newIterations)

            effectiveIdToAbsoluteId(newEffectiveId)

        } else
            absoluteId // id is not impacted
    }

    // Return a sorting string for the given control absolute id, taking repeats into account
    def controlSortString(absoluteId: String, repeatsDepth: Int): String = {

        val effectiveId = absoluteIdToEffectiveId(absoluteId)
        val prefixedId  = getPrefixedId(effectiveId)

        def paddedRepeats =
            getEffectiveIdSuffixParts(effectiveId).reverse.padTo(repeatsDepth, 0).reverse

        def controlPositionAsList =
            containingDocument.getStaticOps.getControlPosition(prefixedId).toList

        val digits = 5
        paddedRepeats ++ controlPositionAsList map (_.formatted(s"%0${digits}d")) mkString "-"
    }
}
