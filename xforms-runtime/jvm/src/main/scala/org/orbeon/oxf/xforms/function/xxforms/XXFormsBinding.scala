/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.analysis.ElementAnalysis.ancestorsIterator
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysisTreeXPathAnalyzer, PathMapXPathAnalysis}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.{PathMap, StringLiteral, XPathContext}
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.scaxon.Implicits._


class XXFormsBinding extends XFormsFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator =
    findControls(0, followIndexes = true)(xpathContext).headOption map (_.bindingEvenIfNonRelevant)

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {

    arguments.head match {
      case s: StringLiteral =>

        val staticId = s.getStringValue

        pathMap.getPathMapContext match {
          case context: ElementAnalysisTreeXPathAnalyzer.SimplePathMapContext =>

            val boundElemOpt =
              ancestorsIterator(context.element, includeSelf = false) collectFirst {
                case c: ComponentControl => c
              } filter { c =>
                c.commonBinding.modeBinding && c.commonBinding.bindingElemId.contains(staticId)
              }

            boundElemOpt.flatMap(_.bindingAnalysis) match {
              case Some(analysis: PathMapXPathAnalysis) =>
                // TODO: review this!
                val clonedVariablePathMap = analysis.pathmap.get.clone
                pathMap.addRoots(clonedVariablePathMap.getPathMapRoots)
                clonedVariablePathMap.findFinalNodes
              case _ =>
                // Invalidate
                pathMap.setInvalidated(true)
                null
            }

          case _ =>
            throw new IllegalStateException("Can't process PathMap because context is not of expected type.")
        }

      case _ =>
        pathMap.setInvalidated(true)
        null
    }
  }
}
