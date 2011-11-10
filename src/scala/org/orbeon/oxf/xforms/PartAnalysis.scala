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

import event.{XFormsEventHandler, XFormsEventHandlerImpl}
import org.orbeon.oxf.xml.dom4j.LocationData
import java.util.{List => JList}
import org.orbeon.oxf.xml.{ContentHandlerHelper, XMLUtils}
import analysis.Metadata
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.oxf.util.IndentedLogger
import xbl.{XBLBindingsBase, XBLBindings}
import org.dom4j.Element

trait PartAnalysis extends PartGlobalOps with PartStaticAnalysisOps with XMLUtils.DebugXML {

    val parent: Option[PartAnalysis]

    def ancestors: Stream[PartAnalysis]
    def ancestorOrSelf: Stream[PartAnalysis]

    def startScope: XBLBindingsBase.Scope

    def getEventHandlers(observerPrefixedId: String): JList[XFormsEventHandler]
    def observerHasHandlerForEvent(observerPrefixedId: String, eventName: String): Boolean

    def hasControls: Boolean
    def getTopLevelControlElements: JList[Element]

    def staticState: XFormsStaticState
    def locationData: LocationData
    def getIndentedLogger: IndentedLogger

    def metadata: Metadata
    def getXBLBindings: XBLBindings

    // TODO: These two methods should probably not be part of this trait
    def extractXFormsScripts(documentInfo: DocumentWrapper, prefix: String)
    def extractEventHandlers(documentInfo: DocumentInfo, innerScope: XBLBindingsBase.Scope, isControls: Boolean): JList[XFormsEventHandlerImpl]

    def dumpAnalysis()
    def toXML(helper: ContentHandlerHelper)
}