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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.xforms._
import XFormsControl._
import org.orbeon.oxf.xforms.XFormsConstants.LHHA
import LHHASupport._

trait ControlLHHASupport {

    self: XFormsControl ⇒

    // Label, help, hint and alert (evaluated lazily)
    private var lhha = new Array[LHHAProperty](XFormsConstants.LHHACount)

    def markLHHADirty() {
        for (currentLHHA ← lhha)
            if (currentLHHA ne null)
                currentLHHA.handleMarkDirty()
    }

    // Copy LHHA if not null
    def updateLHHACopy(copy: XFormsControl) {
        copy.lhha = new Array[LHHAProperty](XFormsConstants.LHHACount)
        for {
            i ← 0 to lhha.size - 1
            currentLHHA = lhha(i)
            if currentLHHA ne null
        } yield {
            // Evaluate lazy value before copying
            currentLHHA.value()

            // Copy
            copy.lhha(i) = currentLHHA.copy.asInstanceOf[LHHAProperty]
        }
    }

    def getLHHA(lhhaType: XFormsConstants.LHHA) = {
        val index = lhhaType.ordinal
        Option(lhha(index)) getOrElse {
            val lhhaElement = container.getPartAnalysis.getLHHA(getPrefixedId, lhhaType.name)
            val result = Option(lhhaElement) map (new MutableLHHAProperty(self, _, lhhaHTMLSupport(index))) getOrElse NullLHHA
            lhha(index) = result
            result
        }
    }

    // Whether we support HTML LHHA
    def lhhaHTMLSupport = DefaultLHHAHTMLSupport

    def compareLHHA(other: XFormsControl): Boolean = {
        for (lhhaType ← LHHA.values)
            if (getLHHA(lhhaType).value() != other.getLHHA(lhhaType).value())
                return false

        true
    }

    // Convenience accessors
    final def getLabel = getLHHA(LHHA.label).value()
    final def getEscapedLabel = getLHHA(LHHA.label).escapedValue()
    final def isHTMLLabel = getLHHA(LHHA.label).isHTML
    final def getHelp = getLHHA(LHHA.help).value()
    final def getEscapedHelp = getLHHA(LHHA.help).escapedValue()
    final def isHTMLHelp = getLHHA(LHHA.help).isHTML
    final def getHint = getLHHA(LHHA.hint).value()
    final def getEscapedHint = getLHHA(LHHA.hint).escapedValue()
    final def isHTMLHint = getLHHA(LHHA.hint).isHTML
    final def getAlert = getLHHA(LHHA.alert).value()
    final def isHTMLAlert = getLHHA(LHHA.alert).isHTML
    final def getEscapedAlert = getLHHA(LHHA.alert).escapedValue()
}

// NOTE: Use name different from trait so that the Java compiler is happy
object LHHASupport {

    val NullLHHA = new NullLHHAProperty

    // By default all controls support HTML LHHA
    val DefaultLHHAHTMLSupport = Array.fill(4)(true)

    // Control property for LHHA
    trait LHHAProperty extends ControlProperty[String] {
        def escapedValue(): String
        def isHTML: Boolean
    }

    // Immutable null LHHA property
    class NullLHHAProperty extends ImmutableControlProperty(null: String) with LHHAProperty {
        def escapedValue(): String = null
        def isHTML = false
    }

    // Whether a given control has an associated xf:label element.
    def hasLabel(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getLabel(prefixedId) ne null

    // Whether a given control has an associated xf:hint element.
    def hasHint(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getHint(prefixedId) ne null

    // Whether a given control has an associated xf:help element.
    def hasHelp(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getHelp(prefixedId) ne null

    // Whether a given control has an associated xf:alert element.
    def hasAlert(containingDocument: XFormsContainingDocument, prefixedId: String) =
        containingDocument.getStaticOps.getAlert(prefixedId) ne null
}