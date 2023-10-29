/**
 * Copyright (C) 2018 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.scaxon.Implicits._


class XXFormsResourceElem extends XFormsFunction {

  import XXFormsResourceSupport._

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context

    val resourceKeyArgument = stringArgument(0)
    val instanceArgumentOpt = stringArgumentOpt(1)

    def findResourcesElement =
      XFormsFunction.resolveOrFindByStaticOrAbsoluteId(instanceArgumentOpt getOrElse "fr-form-resources") collect
        { case instance: XFormsInstance => instance.rootElement }

    val resourceRootOpt =
      for {
        elementAnalysis <- XFormsFunction.elementAnalysisForSource
        resources       <- findResourcesElement
        requestedLang   <- XXFormsLang.resolveXMLangHandleAVTs(XFormsFunction.getContainingDocument, elementAnalysis)
        resourceRoot    <- findResourceElementForLang(resources, requestedLang)
      } yield
        resourceRoot

    // https://github.com/orbeon/orbeon-forms/issues/6016
    resourceRootOpt match {
      case Some(resourceRoot) => pathFromTokens(resourceRoot, splitResourceName(resourceKeyArgument))
      case None               => Nil: List[NodeInfo] // help with the conversion to `SequenceIterator`
    }
  }

  // TODO
//  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = ???
}
