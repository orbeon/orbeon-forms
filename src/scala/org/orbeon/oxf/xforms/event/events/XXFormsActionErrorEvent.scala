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

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.xforms.event.{XFormsEvent, XFormsEventTarget, XFormsEvents}

class XXFormsActionErrorEvent(containingDocument: XFormsContainingDocument, target: XFormsEventTarget, val throwable: Throwable)
    extends XFormsEvent(containingDocument, XFormsEvents.XXFORMS_ACTION_ERROR, target, bubbles = true, cancelable = false) with EventAttributes {

    private lazy val rootLocationData = ValidationException.getRootLocationData(throwable)
    private def rootMessage = OXFException.getRootThrowable(throwable).getMessage

    override val attributes = Map(
        "element" ->    (() => rootLocationData match {
                            case rootLocationData: ExtendedLocationData => rootLocationData.getElementDebugString
                            case _ => null
                        }),
        "system-id" ->  (() => rootLocationData.getSystemID),
        "line" ->       (() => rootLocationData.getLine.toString),
        "column" ->     (() => rootLocationData.getCol.toString),
        "message" ->    (() => rootMessage),
        "throwable" ->  (() => OXFException.throwableToString(throwable))
    )
}