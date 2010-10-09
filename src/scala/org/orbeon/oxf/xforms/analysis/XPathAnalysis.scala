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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.saxon.expr.PathMap
import org.orbeon.oxf.util.PropertyContext
import org.orbeon.oxf.xml.ContentHandlerHelper

trait XPathAnalysis {
    def pathmap: PathMap

    def xpathString: String
    def figuredOutDependencies: Boolean

    def valueDependentPaths: collection.Set[String]
    def returnablePaths: collection.Set[String]

    def dependentModels: collection.Set[String]
    def dependentInstances: collection.Set[String]
    def returnableInstances: collection.Set[String]

    // Return true if any path matches
    // TODO: for now naively just check exact paths
    def intersectsBinding(touchedPaths: collection.Set[String]) = valueDependentPaths exists (touchedPaths contains _): Boolean

    // Return true if any path matches
    // TODO: for now naively just check exact paths
    def intersectsValue(touchedPaths: collection.Set[String]) = intersectsBinding(touchedPaths) || (returnablePaths exists (touchedPaths contains _)): Boolean

    def intersectsModels(touchedModels: collection.Set[String]) = dependentModels exists (touchedModels contains _): Boolean

    def combine(other: XPathAnalysis): Unit

    def toXML(propertyContext: PropertyContext , helper: ContentHandlerHelper)

    def freeTransientState()
}