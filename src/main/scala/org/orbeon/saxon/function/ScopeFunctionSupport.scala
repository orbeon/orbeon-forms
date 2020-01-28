/**
  * Copyright (C) 2009 Orbeon, Inc.
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

import java.io.StringReader

import org.exolab.castor.mapping.Mapping
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.processor.scope.ScopeGenerator
import org.orbeon.oxf.xml.SaxonUtils.StringValueWithEquals
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.{AtomicValue, StringValue}
import org.xml.sax.InputSource

import scala.util.control.NonFatal

object ScopeFunctionSupport {

  def storeAttribute(put: (String, AnyRef) => Any, attributeName: String, item: Item): Unit = {
    if (item eq null) {
      // Clear value
      // TODO: Shouldn't this use `remove()`?
      put(attributeName, null)
    } else {
      // NOTE: In theory, we could store any item()* into the session. This would work fine with atomic values,
      // but we would need to copy over trees as NodeInfo can back trees that may change over time. So for now,
      // we only accept storing a single item(), and we convert trees into SAXStore.
      // Prepare value
      val value =
        item match {
          case v: StringValue => new StringValueWithEquals(v.getStringValueCS)
          case v: AtomicValue => v
          case v: NodeInfo    => TransformerUtils.tinyTreeToSAXStore(v)
          case _ => throw new OXFException(s"xxf:set-*-attribute() does not support storing objects of type: ${item.getClass.getName}")
        }
      // Store value
      // TODO: It seems that Jetty sometimes fails down the line here by calling equals() on the value.
      put(attributeName, value)
    }
  }

  def convertAttributeValue(
    valueOpt        : Option[Any],
    contentTypeOpt  : Option[String],
    key             : String)(implicit
    xpathContext    : XPathContext
  ): SequenceIterator =
    valueOpt match {
      case Some(v: Item) =>
        // NOTE: This can be a `StringValueWithEquals`
        SingletonIterator.makeIterator(v)
      case Some(v) =>
        val saxStore =
          try {
            // We don't have any particular mappings to pass to serialize objects
            val mapping = new Mapping
            mapping.loadMapping(new InputSource(new StringReader("<mapping/>")))
            ScopeGenerator.getSAXStore(v, mapping, contentTypeOpt.orNull, key)
          } catch {
            case NonFatal(t) =>
              throw new OXFException(t)
          }
        // Convert to DocumentInfo
        val documentInfo = TransformerUtils.saxStoreToTinyTree(xpathContext.getConfiguration, saxStore)
        SingletonIterator.makeIterator(documentInfo)
      case None =>
        EmptyIterator.getInstance
    }
}