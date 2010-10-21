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
import org.orbeon.oxf.xforms.analysis.{StaticStateContext, SimpleElementAnalysis}
import org.orbeon.oxf.util.PropertyContext
import org.orbeon.oxf.xml.ContentHandlerHelper

/**
 * Trait representing an element supporting LHHA elements.
 */
trait LHHATrait extends SimpleElementAnalysis {

    // Nested LHHA
    val nestedLabel = findNestedLHHA(XFormsConstants.LABEL_QNAME)
    val nestedHelp = findNestedLHHA(XFormsConstants.HELP_QNAME)
    val nestedHint = findNestedLHHA(XFormsConstants.HINT_QNAME)
    val nestedAlert = findNestedLHHA(XFormsConstants.ALERT_QNAME)

    private def findNestedLHHA(qName: QName) = findNestedLHHAElement(qName) match {
        case Some(lhhaElement) => Some(new LHHAAnalysis(staticStateContext, scopeModel.scope, lhhaElement, LHHATrait.this, true))
        case None => None
    }

    protected def findNestedLHHAElement(qName: QName) = Option(element.element(qName))

    // External LHHA
    var externalLabel: Option[LHHAAnalysis] = None
    var externalHelp: Option[LHHAAnalysis] = None
    var externalHint: Option[LHHAAnalysis] = None
    var externalAlert: Option[LHHAAnalysis] = None

    def setExternalLHHA(staticStateContext: StaticStateContext, lhhaElement: Element) {
        require(lhhaElement ne null)

        // TODO: This must be set in the proper parent context as context/variables can be different
        val lhhaAnalysis = Some(new LHHAAnalysis(staticStateContext, scopeModel.scope, lhhaElement, LHHATrait.this, false))

        lhhaElement.getQName match {
            case XFormsConstants.LABEL_QNAME => externalLabel = lhhaAnalysis
            case XFormsConstants.HELP_QNAME => externalHelp = lhhaAnalysis
            case XFormsConstants.HINT_QNAME => externalHint = lhhaAnalysis
            case XFormsConstants.ALERT_QNAME => externalAlert = lhhaAnalysis
        }
    }

    // Java API (allowed to return null)
    def getLabel = if (nestedLabel.isDefined) nestedLabel.get else externalLabel.orNull
    def getHelp = if (nestedHelp.isDefined) nestedHelp.get else externalHelp.orNull
    def getHint = if (nestedHint.isDefined) nestedHint.get else externalHint.orNull
    def getAlert = if (nestedAlert.isDefined) nestedAlert.get else externalAlert.orNull

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

    override def freeTransientState() = {
        getAllLHHA foreach (_.freeTransientState())
    }

    private def getAllLHHA = {
        var lhha: List[LHHAAnalysis] = Nil

        if (getAlert ne null) lhha = getAlert :: lhha
        if (getHint ne null) lhha = getHint :: lhha
        if (getHelp ne null) lhha = getHelp :: lhha
        if (getLabel ne null) lhha = getLabel :: lhha

        lhha
    }
}