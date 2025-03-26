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

import cats.syntax.option.*
import org.orbeon.oxf.xforms.analysis.{LangRef, PathMapXPathAnalysis}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.{PathMap, XPathContext}
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits.*


class XXFormsLang extends XFormsFunction {

  import XXFormsLang.*

  override def evaluateItem(xpathContext: XPathContext): StringValue = {
    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context
    XFormsFunction.elementAnalysisForSource flatMap (XXFormsLangSupport.resolveXMLangHandleAVTs(XFormsFunction.getContainingDocument, _))
  }

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
    addXMLLangDependency(pathMap)
    null
  }
}

object XXFormsLang {

  def addXMLLangDependency(pathMap: PathMap): Unit = {

    // Dependency on language
    val avtLangAnalysisOpt = XFormsFunction.sourceElementAnalysis(pathMap).getLangUpdateIfUndefined match {
      case ref: LangRef.AVT => ref.att.valueAnalysis.get.some
      case _ => None
    }

    // Only add the dependency if xml:lang is not a literal
    avtLangAnalysisOpt foreach {
      case analysis: PathMapXPathAnalysis =>
        // There is a pathmap for the `xml:lang` AVT, so add the new roots
        pathMap.addRoots(analysis.pathMapOrThrow.clone.getPathMapRoots)
        //pathMap.findFinalNodes // FIXME: needed?
        //pathMap.updateFinalNodes(finalNodes)
      case analysis if ! analysis.figuredOutDependencies =>
        // Dependencies not found
        pathMap.setInvalidated(true)
      case _ => // NOP
    }
  }
}
