/**
 *   Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.util.PropertyContext
import org.orbeon.oxf.xml.ContentHandlerHelper

/**
 * Abstract representation of an XPath analysis as usable by the XForms engine.
 */
abstract class XPathAnalysis {

    val xpathString: String
    val figuredOutDependencies: Boolean

    val valueDependentPaths: collection.Set[String]
    val returnablePaths: collection.Set[String]

    val dependentModels: collection.Set[String]
    val dependentInstances: collection.Set[String]
    val returnableInstances: collection.Set[String]

    // Return true if any path matches
    // NOTE: For now just check exact paths. Later must be smarter?
    def intersectsBinding(touchedPaths: collection.Set[String]) = valueDependentPaths exists (touchedPaths contains _): Boolean

    // Return true if any path matches
    // NOTE: For now just check exact paths. Later must be smarter?
    def intersectsValue(touchedPaths: collection.Set[String]) = intersectsBinding(touchedPaths) || (returnablePaths exists (touchedPaths contains _)): Boolean

    def intersectsModels(touchedModels: collection.Set[String]) = dependentModels exists (touchedModels contains _): Boolean

    /**
     * Combine this analysis with another one and return a new analysis.
     */
    def combine(other: XPathAnalysis): XPathAnalysis

    def toXML(propertyContext: PropertyContext , helper: ContentHandlerHelper)

    def freeTransientState() {}
}

object XPathAnalysis {

    // Constant analysis, positive or negative
    abstract class ConstantXPathAnalysis(val xpathString: String, val figuredOutDependencies: Boolean) extends XPathAnalysis {

        require(xpathString ne null)

        val returnableInstances = Set.empty[String]
        val dependentInstances = Set.empty[String]
        val dependentModels = Set.empty[String]
        val returnablePaths = Set.empty[String]
        val valueDependentPaths = Set.empty[String]

        def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) =
            helper.element("analysis", Array("expression", xpathString, "analyzed", figuredOutDependencies.toString))
    }

    // Some kind of combination that makes sense (might not exactly match the combined PathMap)
    def combineXPathStrings(s1: String, s2: String) = "(" + s1 + ") | (" + s2 + ")"
}

object ConstantNegativeAnalysis {
    def apply(xpathString: String): XPathAnalysis = new XPathAnalysis.ConstantXPathAnalysis(xpathString, false) {
        override def combine(other: XPathAnalysis) = ConstantNegativeAnalysis(XPathAnalysis.combineXPathStrings(xpathString, other.xpathString))
    }
}

object ConstantPositiveAnalysis {

    private val CONSTANT_ANALYSIS = ConstantPositiveAnalysis("'CONSTANT'")

    def apply(xpathString: String): XPathAnalysis = new XPathAnalysis.ConstantXPathAnalysis(xpathString, true) {
        override def combine(other: XPathAnalysis) = other
    }

    def apply(): XPathAnalysis = CONSTANT_ANALYSIS
}