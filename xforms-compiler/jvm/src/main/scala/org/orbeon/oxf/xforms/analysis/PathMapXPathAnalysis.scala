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
import org.orbeon.xforms.XFormsId


case class PathMapXPathAnalysis(
  xpathString            : String,
  figuredOutDependencies : Boolean,
  valueDependentPaths    : MapSet[String, String],
  returnablePaths        : MapSet[String, String],
  dependentModels        : collection.Set[String],
  dependentInstances     : collection.Set[String])(
  var pathmap            : Option[PathMap], // this is used when used as variables and context and can be freed afterwards
) extends XPathAnalysis {

  override def freeTransientState(): Unit = pathmap = None
}
