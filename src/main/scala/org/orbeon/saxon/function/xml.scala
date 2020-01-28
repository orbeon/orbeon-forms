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
package org.orbeon.saxon.function

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.{DefaultFunctionSupport, DependsOnContextItemIfSingleArgumentMissing}
import org.orbeon.saxon.expr.{StaticProperty, XPathContext}
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import scala.collection.compat._

class HasClass extends ClassSupport {
  override def evaluateItem(xpathContext: XPathContext): BooleanValue =
    classes(1)(xpathContext)(stringArgument(0)(xpathContext))

  // Needed otherwise xpathContext.getContextItem doesn't return the correct value
  // See also `DependsOnContextItemIfSingleArgumentMissing`
   override def getIntrinsicDependencies =
     if (argument.size == 1) StaticProperty.DEPENDS_ON_CONTEXT_ITEM else 0
}

class Classes extends ClassSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def iterate(xpathContext: XPathContext): SequenceIterator =
    stringSeqToSequenceIterator(classes(0)(xpathContext).to(List))
}

protected trait ClassSupport extends DefaultFunctionSupport {
  protected def classes(i: Int)(implicit xpathContext: XPathContext): Set[String] =
    itemArgumentOrContextOpt(i) match {
      case Some(node: NodeInfo) if node.isElement =>
        val classCode = xpathContext.getNamePool.allocate("", "", "class")
        val value     = node.getAttributeValue(classCode)

        value.tokenizeToSet
      case _ =>
        Set.empty
    }
}

class CreateDocument extends DefaultFunctionSupport  {
  // Create a new DocumentWrapper. If we use a global one, the first document ever created is wrongly returned!
  override def evaluateItem(xpathContext: XPathContext): DocumentWrapper =
    new DocumentWrapper(dom.Document(), null, XPath.GlobalConfiguration)
}