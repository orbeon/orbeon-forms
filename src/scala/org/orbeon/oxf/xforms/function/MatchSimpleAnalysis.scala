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
package org.orbeon.oxf.xforms.function

import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis
import org.orbeon.saxon.expr.PathMap


trait MatchSimpleAnalysis {
    def matchSimpleAnalysis(pathMap: PathMap, analysisOption: Option[SimpleAnalysis]): PathMapNodeSet = analysisOption match {
        case Some(simpleAnalysis) if simpleAnalysis.getBindingAnalysis != null && simpleAnalysis.getBindingAnalysis.figuredOutDependencies =>
            // Clone the PathMap first because the nodes returned must belong to this PathMap
            val clonedContextPathMap = simpleAnalysis.getBindingAnalysis.pathmap.clone
            pathMap.addRoots(clonedContextPathMap.getPathMapRoots)
            clonedContextPathMap.findFinalNodes
        case _ =>
            // Either there is no analysis at all or we couldn't figure out binding analysis
            pathMap.setInvalidated(true)
            null
    }
}
