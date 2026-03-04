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

import org.orbeon.oxf.xforms.analysis.{ConstantXPathAnalysis, ElementAnalysis, PathMapXPathAnalysis, XPathAnalysis}
import org.orbeon.saxon.expr.PathMap
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet


object XFormsPathMapSupport {

  def updateWithBindingAnalysis(pathMap: PathMap, elementAnalysisOpt: Option[ElementAnalysis]): PathMapNodeSet =
    elementAnalysisOpt
      .flatMap(_.bindingAnalysis)
      .map(updateWithXPathAnalysis(pathMap, _))
      .getOrElse(invalidatePathMap(pathMap))

  def updateWithXPathAnalysis(pathMap: PathMap, xpathAnalysis: XPathAnalysis): PathMapNodeSet =
    if (xpathAnalysis.figuredOutDependencies)
      xpathAnalysis match {
        case pathMapXPathAnalysis: PathMapXPathAnalysis =>
          val clonedContextPathMap = pathMapXPathAnalysis.pathMapOrThrow.clone // clone the `PathMap` first because the nodes returned must belong to this `PathMap`
          pathMap.addRoots(clonedContextPathMap.getPathMapRoots)
          clonedContextPathMap.findFinalNodes
        case bindingAnalysis: ConstantXPathAnalysis if bindingAnalysis.figuredOutDependencies =>
          null
        case _ =>
          invalidatePathMap(pathMap)
      }
  else
    invalidatePathMap(pathMap)

  def invalidatePathMap(pathMap: PathMap): PathMapNodeSet = {
    pathMap.setInvalidated(true)
    null
  }
}
