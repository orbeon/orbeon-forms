/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xml.RuntimeDependentFunction
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om.{EmptyIterator, SequenceIterator}

/**
 * 2.4 Accessing Context Information for Events
 *
 * This is the event() function which returns "context specific information" for an event.
 */
class Event extends XFormsFunction with RuntimeDependentFunction {

  private var namespaceMappings: Map[String, String] = null

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    Option(getContainingDocument) flatMap (_.currentEventOpt) match {
      case Some(event) => getEventAttribute(event, stringArgument(0))
      case None        => EmptyIterator.getInstance
    }
  }

  private def getEventAttribute(event: XFormsEvent, attributeName: String): SequenceIterator = {

    // As an extension, we allow a QName

    // NOTE: Here the idea is to find the namespaces in scope. We assume that the expression occurs on an XForms
    // element. There are other ways of obtaining the namespaces, for example we could extract them from the static
    // state.
//        final Element element = getContextStack(xpathContext).getCurrentBindingContext().getControlElement();
//        final Map namespaceMappings = containingDocument(xpathContext).getStaticState().getNamespaceMappings(element);
    val attributeQName = Dom4jUtils.extractTextValueQName(namespaceMappings, attributeName, unprefixedIsNoNamespace = true)
    event.getAttribute(attributeQName.clarkName)
  }

  // The following copies StaticContext namespace information
  // See also Saxon Evaluate.java
  override def checkArguments(visitor: ExpressionVisitor): Unit =
    if (namespaceMappings eq null) {
      val env = visitor.getStaticContext
      super.checkArguments(visitor)
      namespaceMappings = Map()

      val namespaceResolver = env.getNamespaceResolver

      val iterator = namespaceResolver.iteratePrefixes
      while (iterator.hasNext) {
        val prefix = iterator.next().asInstanceOf[String]
        if (prefix != "") {
          val uri = namespaceResolver.getURIForPrefix(prefix, true)
          namespaceMappings += prefix -> uri
        }
      }
    }
}