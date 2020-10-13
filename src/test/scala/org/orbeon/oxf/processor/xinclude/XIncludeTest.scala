/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.processor.xinclude

import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.xml.ParserConfiguration.XIncludeOnly
import org.orbeon.oxf.xml.{JXQName, XMLParsing}
import org.orbeon.scaxon.SAXEvents._
import JXQName._
import org.orbeon.scaxon.DocumentAndElementsCollector
import org.scalatestplus.junit.AssertionsForJUnit

class XIncludeTest extends ResourceManagerTestBase with AssertionsForJUnit {

  val XML     = "http://www.w3.org/XML/1998/namespace"
  val XI      = "http://www.w3.org/2001/XInclude"
  val HTML    = "http://www.w3.org/1999/xhtml"
  val XForms  = "http://www.w3.org/2002/xforms"
  val SVG     = "http://www.w3.org/2000/svg"

  val XMLBase = JXQName(XML -> "base")

  @Test def basicInclude(): Unit = {

    val collector = new DocumentAndElementsCollector

    XMLParsing.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include11.xml", collector, XIncludeOnly, true)

    val expected = List(
      StartDocument,
        StartPrefixMapping("", HTML),
        StartPrefixMapping("xf", XForms),
        StartPrefixMapping("xi", XI),
          StartElement(HTML -> "html", Atts(Nil)),
            StartPrefixMapping("xf", ""),
            StartPrefixMapping("xi", ""),
            StartPrefixMapping("svg", SVG),
              StartElement(HTML -> "body", Atts(List((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include12.xml")))),
                StartElement(HTML -> "div", Atts(Nil)),
                EndElement(HTML -> "div"),
              EndElement(HTML -> "body"),
            EndPrefixMapping("xf"),
            EndPrefixMapping("xi"),
            EndPrefixMapping("svg"),
          EndElement(HTML -> "html"),
        EndPrefixMapping(""),
        EndPrefixMapping("xf"),
        EndPrefixMapping("xi"),
      EndDocument
    )

    assert(expected === collector.events)
  }

  @Test def includeAtRoot(): Unit = {

    val collector = new DocumentAndElementsCollector

    XMLParsing.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include21.xml", collector, XIncludeOnly, true)

    val expected = List(
      StartDocument,
        StartPrefixMapping("", HTML),
        StartPrefixMapping("xf", XForms),
        StartPrefixMapping("xi", XI),
          StartElement(HTML -> "html", Atts(Nil)),
            StartPrefixMapping("xf", ""),
            StartPrefixMapping("xi", ""),
              StartElement(HTML -> "body", Atts(List((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include23.xml")))),
                StartElement(HTML -> "div", Atts(Nil)),
                EndElement(HTML -> "div"),
              EndElement(HTML -> "body"),
            EndPrefixMapping("xf"),
            EndPrefixMapping("xi"),
          EndElement(HTML -> "html"),
        EndPrefixMapping(""),
        EndPrefixMapping("xf"),
        EndPrefixMapping("xi"),
      EndDocument
    )

    assert(expected === collector.events)
  }

  @Test def noDuplicateNSEvents(): Unit = {

    val collector = new DocumentAndElementsCollector

    XMLParsing.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include31.xml", collector, XIncludeOnly, true)

    val expected = List(
      StartDocument,
        StartPrefixMapping("", HTML),
        StartPrefixMapping("xf", XForms),
          StartElement(HTML -> "html", Atts(Nil)),
            StartElement(HTML -> "body", Atts(List((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include32.xml")))),
            EndElement(HTML -> "body"),
          EndElement(HTML -> "html"),
        EndPrefixMapping(""),
        EndPrefixMapping("xf"),
      EndDocument
    )

    assert(expected === collector.events)
  }

  @Test def siblingIncludes(): Unit = {

    val collector = new DocumentAndElementsCollector

    XMLParsing.urlToSAX("oxf:/org/orbeon/oxf/processor/xinclude/include41.xml", collector, XIncludeOnly, true)

    val expected = List(
      StartDocument,
        StartPrefixMapping("", HTML),
        StartPrefixMapping("xf", XForms),
          StartElement(HTML -> "html", Atts(Nil)),
            StartPrefixMapping("", ""),
            StartPrefixMapping("xf", ""),
            StartPrefixMapping("xh", HTML),
              StartElement(HTML -> "head", Atts(List((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include42.xml")))),
              EndElement(HTML -> "head"),
            EndPrefixMapping(""),
            EndPrefixMapping("xf"),
            EndPrefixMapping("xh"),
            StartPrefixMapping("xf", ""),
              StartElement(HTML -> "body", Atts(List((XMLBase, "oxf:/org/orbeon/oxf/processor/xinclude/include43.xml")))),
              EndElement(HTML -> "body"),
            EndPrefixMapping("xf"),
          EndElement(HTML -> "html"),
        EndPrefixMapping(""),
        EndPrefixMapping("xf"),
      EndDocument
    )

    assert(expected === collector.events)
  }
}
