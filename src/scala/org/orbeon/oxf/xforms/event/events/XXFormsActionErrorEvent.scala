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
package org.orbeon.oxf.xforms.event.events

import org.orbeon.exception._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget, XFormsEvents}
import org.orbeon.saxon.om.SequenceIterator
import XXFormsActionErrorEvent._
import org.orbeon.oxf.common.ValidationException

class XXFormsActionErrorEvent(containingDocument: XFormsContainingDocument, target: XFormsEventTarget, val throwable: Throwable)
    extends XFormsEvent(containingDocument, XFormsEvents.XXFORMS_ACTION_ERROR, target, bubbles = true, cancelable = false) {

    private lazy val rootLocationData = ValidationException.getRootLocationData(throwable)

    override def getStandardAttribute(name: String) =
        StandardAttributes.get(name) orElse super.getStandardAttribute(name)
}

private object XXFormsActionErrorEvent {

    import XFormsEvent._

    val StandardAttributes = Map[String, XXFormsActionErrorEvent ⇒ SequenceIterator](

        "element"   → (e ⇒ e.rootLocationData match {
                            case rootLocationData: ExtendedLocationData ⇒ stringIterator(rootLocationData.getElementDebugString)
                            case _ ⇒ emptyIterator
                       }),
        "system-id" →  (e ⇒ stringIterator(e.rootLocationData.getSystemID,      e.rootLocationData ne null)),
        "line"      →  (e ⇒ stringIterator(e.rootLocationData.getLine.toString, e.rootLocationData ne null)),
        "column"    →  (e ⇒ stringIterator(e.rootLocationData.getCol.toString,  e.rootLocationData ne null)),
        "message"   →  (e ⇒ stringIterator(Exceptions.getRootThrowable(e.throwable).getMessage)),
        "throwable" →  (e ⇒ stringIterator(Formatter.format(e.throwable)))
    )
}