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
import org.dom4j.{QName, Element}
import org.orbeon.oxf.util.PropertyContext
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xforms.analysis.{StaticStateContext, SimpleElementAnalysis}
import collection.mutable.LinkedHashMap

/**
 * Trait representing an element supporting LHHA elements (children or external).
 */
trait LHHATrait extends SimpleElementAnalysis {

    private val lhha = LinkedHashMap.empty[String, LHHAAnalysis]
    for (qName <- List(XFormsConstants.LABEL_QNAME, XFormsConstants.HELP_QNAME, XFormsConstants.HINT_QNAME, XFormsConstants.ALERT_QNAME))
        findNestedLHHAElement(qName) match  {
            case Some(lhhaElement) => lhha += (lhhaElement.getName -> new LHHAAnalysis(staticStateContext, scopeModel.scope, lhhaElement, LHHATrait.this, true))
            case None =>
        }

    protected def findNestedLHHAElement(qName: QName) = Option(element.element(qName))

    def setExternalLHHA(staticStateContext: StaticStateContext, lhhaElement: Element) {
        lhha += (lhhaElement.getName -> new LHHAAnalysis(staticStateContext, scopeModel.scope, lhhaElement, LHHATrait.this, false))
        // TODO: This must be set in the proper parent context as context/variables can be different
    }

    // Java API (allowed to return null)
    def getLHHA(lhhaType: String): LHHAAnalysis = lhha.get(lhhaType).orNull

    def getLHHAValueAnalysis(lhhaType: String) = lhha.get(lhhaType) match {
        case Some(analysis) => analysis.getValueAnalysis
        case None => None
    }

    override def analyzeXPath() = {
        super.analyzeXPath()
        getAllLHHA foreach (_.analyzeXPath())
    }

    override def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper, attributes: List[String] = Nil)(content: => Unit = {}) {
        super.toXML(propertyContext, helper) {
            for (analysis <- getAllLHHA) {
                helper.startElement(analysis.element.getName)
                if (analysis.getValueAnalysis.isDefined)
                    analysis.getValueAnalysis.get.toXML(propertyContext, helper)
                helper.endElement()
            }
        }
    }

    override def freeTransientState(): Unit = getAllLHHA foreach (_.freeTransientState())

    private def getAllLHHA = lhha.values
}