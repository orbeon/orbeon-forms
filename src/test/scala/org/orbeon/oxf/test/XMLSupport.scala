/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.orbeon.dom.io.XMLWriter
import org.orbeon.dom.{Document => JDocument, Element => JElement}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.io.IOUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom.Comparator
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.scalatestplus.junit.AssertionsForJUnit

trait XMLSupport extends AssertionsForJUnit {

  def readURLAsImmutableXMLDocument(url: String): DocumentInfo =
    useAndClose(URLFactory.createURL(url).openStream()) { is =>
      TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, null, false, false)
    }

  def assertXMLDocumentsIgnoreNamespacesInScope(left: DocumentInfo, right: DocumentInfo): Unit =
    assertXMLDocumentsIgnoreNamespacesInScope(tinyTreeToDom4j(left), tinyTreeToDom4j(right))

  def assertXMLElementsIgnoreNamespacesInScope(left: NodeInfo, right: NodeInfo): Unit =
    assertXMLDocumentsIgnoreNamespacesInScope(tinyTreeToDom4j(left), tinyTreeToDom4j(right))

  def assertXMLDocumentsIgnoreNamespacesInScope(left: JDocument, right: JDocument): Unit = {

    val result = Comparator.compareDocumentsIgnoreNamespacesInScope(left, right)

    // Produce a nicer message
    if (! result) {
      assert(
        left.getRootElement.serializeToString(XMLWriter.PrettyFormat) ===
          right.getRootElement.serializeToString(XMLWriter.PrettyFormat)
      )
      assert(false)
    }
  }

  def assertXMLElementsIgnoreNamespacesInScopeCollapse(left: JElement, right: JElement): Unit = {

    val result = Comparator.compareElementsIgnoreNamespacesInScopeCollapse(left, right)

    // Produce a nicer message
    if (! result) {
      assert(
        left.serializeToString(XMLWriter.PrettyFormat) ===
          right.serializeToString(XMLWriter.PrettyFormat)
      )
      assert(false)
    }
  }
}
