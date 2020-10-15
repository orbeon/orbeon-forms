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

  // Combine this analysis with another one and return a new analysis
  def combine(other: XPathAnalysis): XPathAnalysis

  // Convert this analysis into the same analysis, where values have become dependencies
  def makeValuesDependencies: XPathAnalysis

  def freeTransientState(): Unit = ()
}

/**
 * Represent a path into an instance.
 */
case class InstancePath(instancePrefixedId: String, path: String)

object XPathAnalysis {

  // Constant analysis, positive or negative
  abstract class ConstantXPathAnalysis(val xpathString: String, val figuredOutDependencies: Boolean) extends XPathAnalysis {

    require(xpathString ne null)

    val dependentInstances  : Set[String] = Set.empty
    val dependentModels     : Set[String] = Set.empty
    val returnablePaths     : MapSet[String, String] = MapSet.empty
    val valueDependentPaths : MapSet[String, String] = MapSet.empty

    def makeValuesDependencies: XPathAnalysis = this
  }

  // Some kind of combination that makes sense (might not exactly match the combined PathMap)
  def combineXPathStrings(s1: String, s2: String): String = "(" + s1 + ") | (" + s2 + ")"
}

object NegativeAnalysis {
  def apply(xpathString: String): XPathAnalysis =
    new XPathAnalysis.ConstantXPathAnalysis(xpathString, false) {
      def combine(other: XPathAnalysis): XPathAnalysis =
        NegativeAnalysis(XPathAnalysis.combineXPathStrings(xpathString, other.xpathString))
    }
}

object StringAnalysis {

  private val ConstantAnalysis = new XPathAnalysis.ConstantXPathAnalysis("'CONSTANT'", true) {
    def combine(other: XPathAnalysis): XPathAnalysis = other
  }

  def apply(): XPathAnalysis = ConstantAnalysis
}