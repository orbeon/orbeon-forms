/**
 *  Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.oxf.xforms.XFormsConstants
import org.dom4j.QName
import org.orbeon.oxf.xml.ContentHandlerHelper
import collection.mutable.LinkedHashMap
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

/**
 * Trait representing an element supporting LHHA elements (nested or external).
 */
trait LHHATrait extends SimpleElementAnalysis {

    // All LHHA, nested or external
    private val lhha = LinkedHashMap.empty[String, LHHAAnalysis]

    // Process nested LHHA
    for (qName <- List(XFormsConstants.LABEL_QNAME, XFormsConstants.HELP_QNAME, XFormsConstants.HINT_QNAME, XFormsConstants.ALERT_QNAME))
        findNestedLHHAElement(qName) match  {
            case Some(lhhaElement) => lhha += (lhhaElement.getName -> new LocalLHHAAnalysis(staticStateContext, lhhaElement, LHHATrait.this, None, getChildElementScope(lhhaElement)))
            case None =>
        }

    protected def findNestedLHHAElement(qName: QName) = Option(element.element(qName))

    // Set external LHHA
    def setExternalLHHA(lhhaAnalysis: LHHAAnalysis): Unit =
        lhha += (lhhaAnalysis.element.getName -> lhhaAnalysis)

    // Java API (allowed to return null)
    def getLHHA(lhhaType: String): LHHAAnalysis = lhha.get(lhhaType).orNull

    def getLHHAValueAnalysis(lhhaType: String) = lhha.get(lhhaType) match {
        case Some(analysis) => analysis.getValueAnalysis
        case None => None
    }

    override def analyzeXPath() = {
        super.analyzeXPath()
        // Only analyze local LHHA as external LHHA are analyzed like controls
        getAllLHHA filter (_.isLocal) foreach (_.analyzeXPath())
    }

    override def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: => Unit) {
        super.toXML(helper, attributes) {
            for (analysis <- getAllLHHA) {
                helper.startElement(analysis.element.getName)
                if (analysis.getValueAnalysis.isDefined)
                    analysis.getValueAnalysis.get.toXML(helper)
                helper.endElement()
            }

            content
        }
    }

    override def freeTransientState() {
        super.freeTransientState()
        getAllLHHA foreach (_.freeTransientState())
    }

    private def getAllLHHA = lhha.values
}