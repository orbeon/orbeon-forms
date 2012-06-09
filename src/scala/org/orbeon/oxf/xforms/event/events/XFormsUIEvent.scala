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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.XFormsEvent
import collection.JavaConverters._
import org.orbeon.saxon.om.{ListIterator, EmptyIterator, SequenceIterator}
import XFormsUIEvent._

/**
 * Base class for UI events.
 */
abstract class XFormsUIEvent(
        containingDocument: XFormsContainingDocument,
        eventName: String,
        val targetControl: XFormsControl,
        bubbles: Boolean,
        cancelable: Boolean)
    extends XFormsEvent(
        containingDocument,
        eventName,
        targetControl,
        bubbles,
        cancelable) {

    def this(containingDocument: XFormsContainingDocument, eventName: String, targetControl: XFormsControl) =
        this(containingDocument, eventName, targetControl, true, false)

    override def getAttribute(name: String): SequenceIterator =
        XFormsEvent.attribute(this, name, Deprecated, StandardAttributes) getOrElse super.getAttribute(name)
}

object XFormsUIEvent {

    import XFormsEvent._
    
    private val Deprecated = Map(
        "target-ref"        → "xxforms:binding",
        "alert"             → "xxforms:alert",
        "label"             → "xxforms:label",
        "hint"              → "xxforms:hint",
        "help"              → "xxforms:help"
    )
    
    private val StandardAttributes = Map[String, XFormsUIEvent ⇒ SequenceIterator](
        "target-ref"                    → binding,
        xxformsName("binding")          → binding,
        "label"                         → label,
        xxformsName("label")            → label,
        "help"                          → help,
        xxformsName("help")             → help,
        "hint"                          → hint,
        xxformsName("hint")             → hint,
        "alert"                         → alert,
        xxformsName("alert")            → alert,
        xxformsName("control-position") → controlPosition
    )

    private def binding(e: XFormsUIEvent) = new ListIterator(e.targetControl.binding.asJava)

    private def controlPosition(e: XFormsUIEvent) = {
        val controlStaticPosition = e.targetControl.container.getPartAnalysis.getControlPosition(e.targetControl.getPrefixedId)
        if (controlStaticPosition >= 0)
            longIterator(controlStaticPosition)
        else
            EmptyIterator.getInstance
    }

    private def label(e: XFormsUIEvent) =
        Option(e.targetControl.getLabel) map (stringIterator(_)) getOrElse EmptyIterator.getInstance

    private def help(e: XFormsUIEvent) =
        Option(e.targetControl.getHelp) map (stringIterator(_)) getOrElse EmptyIterator.getInstance

    private def hint(e: XFormsUIEvent) =
        Option(e.targetControl.getHint) map (stringIterator(_)) getOrElse EmptyIterator.getInstance

    private def alert(e: XFormsUIEvent) =
        Option(e.targetControl.getAlert) map (stringIterator(_)) getOrElse EmptyIterator.getInstance
}