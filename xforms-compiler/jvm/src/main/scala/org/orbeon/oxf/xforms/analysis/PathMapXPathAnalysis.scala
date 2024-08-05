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

import org.orbeon.oxf.xforms._
import org.orbeon.saxon.expr._


case class PathMapXPathAnalysis(
  xpathString            : String,
  figuredOutDependencies : Boolean,
  valueDependentPaths    : MapSet[String, String],
  returnablePaths        : MapSet[String, String],
  dependentModels        : collection.Set[String],
  dependentInstances     : collection.Set[String]
)(
  var pathmap            : Option[PathMap], // use `Option` as transient state can be freed
) extends XPathAnalysis {
  def pathMapOrThrow: PathMap = pathmap.getOrElse(throw new IllegalStateException("transient state already freed"))
  override def freeTransientState(): Unit = pathmap = None
}

object PathMapXPathAnalysis {

  def apply(
    xpathString           : String,
    figuredOutDependencies: Boolean,
    valueDependentPaths   : MapSet[String, String],
    returnablePaths       : MapSet[String, String],
    dependentModels       : collection.Set[String],
    dependentInstances    : collection.Set[String],
    pathmap               : PathMap
  ): PathMapXPathAnalysis =
    PathMapXPathAnalysis(
      xpathString,
      figuredOutDependencies,
      valueDependentPaths,
      returnablePaths,
      dependentModels,
      dependentInstances)(
      Some(pathmap)
    )
}