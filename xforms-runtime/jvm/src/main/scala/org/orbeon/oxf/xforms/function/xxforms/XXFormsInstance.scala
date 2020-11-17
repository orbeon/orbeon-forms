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

import org.orbeon.oxf.xforms.function.{XFormsFunction, XXFormsInstanceTrait}
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om._
import org.orbeon.xforms.XFormsId


/**
 * xxf:instance() function. This function operates like the standard instance() function, except that it looks for
 * instances globally instead of using the current model.
 */
class XXFormsInstance extends XFormsFunction with XXFormsInstanceTrait {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    val instanceId = stringArgument(0)

    val rootElementOpt =
      XFormsInstance.findInAncestorScopes(XFormsFunction.context.container, instanceId)

    // Return or warn
    rootElementOpt match {
      case Some(root) =>
        SingletonIterator.makeIterator(root)
      case None =>
        EmptyIterator.getInstance
    }
  }

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet) = {
    argument(0).addToPathMap(pathMap, pathMapNodeSet)
    new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this))
  }
}
