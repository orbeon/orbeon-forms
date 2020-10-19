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

import org.orbeon.oxf.xforms.MapSet

/**
 * Abstract representation of an XPath analysis as usable by the XForms engine.
 */
abstract class XPathAnalysis {

  val xpathString: String
  val figuredOutDependencies: Boolean

  val valueDependentPaths: MapSet[String, String] // instance prefixed id -> paths
  val returnablePaths: MapSet[String, String]     // instance prefixed id -> paths

  val dependentModels: collection.Set[String]
  val dependentInstances: collection.Set[String]

  def returnableInstances: Iterable[String] = returnablePaths.map.keys

  def freeTransientState(): Unit = ()
}

// Constant analysis, positive or negative
sealed abstract class ConstantXPathAnalysis(val xpathString: String, val figuredOutDependencies: Boolean)
  extends XPathAnalysis {

  require(xpathString ne null)

  val dependentInstances  : Set[String] = Set.empty
  val dependentModels     : Set[String] = Set.empty
  val returnablePaths     : MapSet[String, String] = MapSet.empty
  val valueDependentPaths : MapSet[String, String] = MapSet.empty
}

class NegativeAnalysis(xpathString: String)
  extends ConstantXPathAnalysis(xpathString, figuredOutDependencies = false)

object StringAnalysis
  extends ConstantXPathAnalysis("'CONSTANT'", figuredOutDependencies = true)
