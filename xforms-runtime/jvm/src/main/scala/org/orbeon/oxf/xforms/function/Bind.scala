/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xforms.model.RuntimeBind
import org.orbeon.oxf.xml.DependsOnContextItem
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{EmptyIterator, ListIterator, SequenceIterator}

import scala.collection.compat._


class Bind extends XFormsFunction with DependsOnContextItem {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context

    val bindId          = stringArgument(0)
    val searchAncestors = booleanArgument(1, default = false)

    val contextItemOpt =
      Option(xpathContext.getContextItem)

    val startContainer = xfc.container

    val startContainerIt =
      startContainer.searchContainedModelsInScope(XFormsFunction.getSourceEffectiveId, bindId, contextItemOpt).iterator

    val searchIt =
      if (searchAncestors)
        startContainerIt ++
          startContainer.ancestorsIterator.drop(1).flatMap(_.searchContainedModels(bindId, contextItemOpt))
      else
        startContainerIt

    searchIt.nextOption() match {
      case Some(bind: RuntimeBind) => new ListIterator(bind.items)
      case _                       => EmptyIterator.getInstance
    }
  }

  // TODO: PathMap
}
