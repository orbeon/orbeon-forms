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

import XXFormsNodesetChangedEvent._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl
import org.orbeon.oxf.xforms.event.XFormsEvents.XXFORMS_NODESET_CHANGED
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.saxon.value.Int64Value
import org.orbeon.oxf.xforms.event.XFormsEvent

class XXFormsNodesetChangedEvent(
        containingDocument: XFormsContainingDocument,
        targetObject: XFormsControl,
        val newIterations: Seq[XFormsRepeatIterationControl],
        val oldIterationPositions: Seq[Int],
        val newIterationPositions: Seq[Int])
    extends XFormsUIEvent(containingDocument, XXFORMS_NODESET_CHANGED, targetObject) {

    override def getStandardAttribute(name: String) = StandardAttributes.get(name) orElse super.getStandardAttribute(name)
}

object XXFormsNodesetChangedEvent {

    import XFormsEvent._

    val StandardAttributes = Map[String, XXFormsNodesetChangedEvent ⇒ SequenceIterator](
        xxformsName("new-positions")    → (e ⇒ listIterator(e.newIterations         map (i ⇒ new Int64Value(i.iterationIndex)))),
        xxformsName("from-positions")   → (e ⇒ listIterator(e.oldIterationPositions map (i ⇒ new Int64Value(i)))),
        xxformsName("to-positions")     → (e ⇒ listIterator(e.newIterationPositions map (i ⇒ new Int64Value(i))))
    )
}