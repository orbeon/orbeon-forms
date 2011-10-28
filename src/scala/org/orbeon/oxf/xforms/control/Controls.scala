/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control

import controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.{XFormsControls, StaticStateGlobalOps, XFormsUtils}
import org.orbeon.oxf.xforms.XFormsConstants._


object Controls {
    /**
     * Find an effective control id based on a source and a control static id, following XBL scoping and the repeat
     * structure.
     *
     * @param sourceControlEffectiveId  reference to source control, e.g. "list$age.3"
     * @param targetControlStaticId     reference to target control, e.g. "xf-10"
     * @return effective control id, or null if not found
     */
    def findEffectiveControlId(ops: StaticStateGlobalOps, controls: XFormsControls, sourceEffectiveId: String, targetPrefixedId: String): String = {
        
        val tree = controls.getCurrentControlTree 
        
        // Don't do anything if there are no controls
        if (tree.getChildren eq null)
            return null
        
        // NOTE: The implementation tries to do a maximum using the static state. One reason is that the source
        // control's effective id might not yet have an associated control during construction. E.g.:
        //
        // <xf:group id="my-group" ref="employee[index('employee-repeat')]">
        //
        // In that case, the XFormsGroupControl does not yet exist when its binding is evaluated. However, its
        // effective id is known and passed as source, and can be used for resolving the id passed to the index()
        // function.
        //
        // We trust the caller to pass a valid source effective id. That value is always internal, i.e. not created by a
        // form author. On the other hand, the target id cannot be trusted as it is typically specified by the form
        // author.
        
        // 1: Check preconditions
        require(sourceEffectiveId ne null, "Source effective id is required.")
        
        // 3: Implement XForms 1.1 "4.7.1 References to Elements within a repeat Element" algorithm
        
        // Find closest common ancestor repeat

        val sourcePrefixedId = XFormsUtils.getPrefixedId(sourceEffectiveId)
        val sourceParts = XFormsUtils.getEffectiveIdSuffixParts(sourceEffectiveId)

        val targetIndexBuilder = new StringBuilder

        def appendIterationToSuffix(iteration: Int) {
            if (targetIndexBuilder.length == 0)
                targetIndexBuilder.append(REPEAT_HIERARCHY_SEPARATOR_1)
            else if (targetIndexBuilder.length != 1)
                targetIndexBuilder.append(REPEAT_HIERARCHY_SEPARATOR_2)

            targetIndexBuilder.append(iteration.toString)
        }

        val ancestorRepeatPrefixedId = ops.findClosestCommonAncestorRepeat(sourcePrefixedId, targetPrefixedId)

        ancestorRepeatPrefixedId foreach { ancestorRepeatPrefixedId ⇒
            // There is a common ancestor repeat, use the current common iteration as starting point
            for (i ← 0 to ops.getAncestorRepeats(ancestorRepeatPrefixedId).size)
                appendIterationToSuffix(sourceParts(i))
        }

        // Find list of ancestor repeats for destination WITHOUT including the closest ancestor repeat if any
        // NOTE: make a copy because the source might be an immutable wrapped Scala collection which we can't reverse
        val targetAncestorRepeats = ops.getAncestorRepeats(targetPrefixedId, ancestorRepeatPrefixedId).reverse

        // Follow repeat indexes towards target
        for (repeatPrefixedId ← targetAncestorRepeats) {
            val repeatControl = tree.getControl(repeatPrefixedId + targetIndexBuilder.toString).asInstanceOf[XFormsRepeatControl]
            // Control might not exist
            if (repeatControl eq null)
                return null
            // Update iteration suffix
            appendIterationToSuffix(repeatControl.getIndex)
        }

        // Return target
        targetPrefixedId + targetIndexBuilder.toString
    }
}