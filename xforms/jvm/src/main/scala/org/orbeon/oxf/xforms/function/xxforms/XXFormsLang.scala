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

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.{AVTLangRef, ElementAnalysis, LiteralLangRef, PathMapXPathAnalysis}
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.{PathMap, XPathContext}
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits._

class XXFormsLang extends XFormsFunction {

  import XXFormsLang._

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    elementAnalysisForSource flatMap (resolveXMLangHandleAVTs(getContainingDocument, _))
  }

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
    addXMLLangDependency(pathMap)
    null
  }
}

object XXFormsLang {

  def resolveXMLangHandleAVTs(containingDocument: XFormsContainingDocument, element: ElementAnalysis): Option[String] =
    element.lang match {
      case Some(LiteralLangRef(value)) =>
        Some(value)
      case Some(AVTLangRef(att)) =>
        // TODO: resolve concrete ancestor XXFormsAttributeControl instead of just using static id
        val attributeControl = containingDocument.getControlByEffectiveId(att.staticId).asInstanceOf[XXFormsAttributeControl]
        Option(attributeControl.getExternalValue())
      case None =>
        None
    }

  def addXMLLangDependency(pathMap: PathMap): Unit = {
    // Dependency on language
    val avtLangAnalysis = XFormsFunction.sourceElementAnalysis(pathMap).lang collect {
      case ref: AVTLangRef => ref.att.getValueAnalysis.get
    }

    // Only add the dependency if xml:lang is not a literal
    avtLangAnalysis foreach {
      case analysis: PathMapXPathAnalysis =>
        // There is a pathmap for the xml:lang AVT, so add the new roots
        pathMap.addRoots(analysis.pathmap.get.clone.getPathMapRoots)
        //pathMap.findFinalNodes // FIXME: needed?
        //pathMap.updateFinalNodes(finalNodes)
      case analysis if ! analysis.figuredOutDependencies =>
        // Dependencies not found
        pathMap.setInvalidated(true)
      case _ => // NOP
    }
  }
}
