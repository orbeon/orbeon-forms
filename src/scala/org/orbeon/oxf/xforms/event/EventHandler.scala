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
package org.orbeon.oxf.xforms.event

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml.dom4j.LocationData
import java.util.{Set ⇒ JSet}

// Static event handler
trait EventHandler {

    def staticId: String
    def prefixedId: String
    def locationData: LocationData

    def isCapturePhaseOnly: Boolean
    def isTargetPhase: Boolean
    def isBubblingPhase: Boolean

    def isPropagate: Boolean
    def isPerformDefaultAction: Boolean

    val isPhantom: Boolean

    def jObserversPrefixedIds: JSet[String]
    def eventNames: Set[String]

    def isMatchEventName(eventName: String): Boolean
    def isMatch(event: XFormsEvent): Boolean

    def getKeyModifiers: String
    def getKeyText: String

    def handleEvent(containingDocument: XFormsContainingDocument, eventObserver: XFormsEventObserver, event: XFormsEvent)
}