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
import org.orbeon.oxf.xforms.event.{XFormsEventTarget, XFormsEvents, XFormsEvent}
import java.lang.String
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.saxon.om.SingletonIterator
import org.orbeon.saxon.value.StringValue
import org.orbeon.oxf.common.{OXFException, ValidationException}

class XXFormsActionErrorEvent(containingDocument: XFormsContainingDocument, targetObject: XFormsEventTarget, val e: Throwable)
    extends XFormsEvent(containingDocument, XFormsEvents.XXFORMS_ACTION_ERROR, targetObject, true, true) {

    private lazy val rootLocationData = ValidationException.getRootLocationData(e)
    private def rootMessage = OXFException.getRootThrowable(e).getMessage

    private def string(value: String) = SingletonIterator.makeIterator(StringValue.makeStringValue(value))

    override def getAttribute(name: String) = name match {
        case "element" if rootLocationData.isInstanceOf[ExtendedLocationData] =>
            string(rootLocationData.asInstanceOf[ExtendedLocationData].getElementDebugString)
        case "system-id" => string(rootLocationData.getSystemID)
        case "line" => string(rootLocationData.getLine.toString)
        case "column" => string(rootLocationData.getCol.toString)
        case "message" => string(rootMessage)
        case _ => super.getAttribute(name)
    }
}