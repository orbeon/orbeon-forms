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

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xforms.MapSet
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.oxf.xml.XMLReceiverSupport._


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

object XPathAnalysis {

  def buildInstanceString(instanceId: String): String =
    s"instance('${instanceId.replace("'", "''")}')"

  def mapSetToIterable(mapSet: MapSet[String, String]): Iterable[String] =
    mapSet map (entry => buildInstanceString(entry._1) + "/" + entry._2)

  def writeXPathAnalysis(
    xpa      : XPathAnalysis,
    flagPath : String => Boolean = _ => false)(implicit
    receiver : XMLReceiver
  ): Unit =
    xpa match {
      case a: ConstantXPathAnalysis =>
        element("analysis", atts = List("expression" -> a.xpathString, "analyzed" -> a.figuredOutDependencies.toString))
      case a =>
        withElement("analysis", atts = List("expression" -> a.xpathString, "analyzed" -> a.figuredOutDependencies.toString)) {

          def write(iterable: Iterable[String], enclosingElemName: String, elemName: String): Unit =
            if (iterable.nonEmpty)
              withElement(enclosingElemName) {
                for (value <- iterable) {
                  val displayPath = getDisplayPath(value)
                  element(elemName, atts = flagPath(displayPath) list ("flag" -> "true"), text = displayPath)
                }
              }

          write(mapSetToIterable(a.valueDependentPaths), "value-dependent",      "path")
          write(mapSetToIterable(a.returnablePaths),     "returnable",           "path")

          write(a.dependentModels,                  "dependent-models",     "model")
          write(a.dependentInstances,               "dependent-instances",  "instance")
          write(a.returnablePaths.map.keys,         "returnable-instances", "instance")
        }
    }

  /**
   * Given an internal path, get a display path (for debugging/logging).
   */
  def getDisplayPath(path: String): String = {

    // Special case of empty path
    if (path.isEmpty) return path

    val pool = StaticXPath.GlobalConfiguration.getNamePool

    {
      for (token <- path split '/') yield {
        if (token.startsWith("instance(")) {
          // instance(...)
          token
        } else {
          val (optionalAt, number) = if (token.startsWith("@")) ("@", token.substring(1)) else ("", token)

          optionalAt + {
            try {
              // Obtain QName
              pool.getDisplayName(number.toInt)
            } catch {
              // Shouldn't happen, right? But since this is for debugging we output the token.
              case e: NumberFormatException => token
            }
          }
        }
      }
    } mkString "/"
  }
}