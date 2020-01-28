/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import org.junit.Test
import org.orbeon.oxf.fr.persistence.relational.rest.RequestReader
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.xml.JXQName._
import org.orbeon.oxf.xml.XMLParsing.ParserConfiguration._
import org.orbeon.scaxon.DocumentAndElementsCollector
import org.orbeon.scaxon.SAXEvents._
import org.scalatestplus.junit.AssertionsForJUnit

class FormRunnerFilterReceiverTest extends ResourceManagerTestBase with AssertionsForJUnit {

  @Test def resultingEvents(): Unit = {

    val collector = new DocumentAndElementsCollector

    // Test extraction of the Form Runner <metadata> element
    val metadataFilter =
      new FilterReceiver(
        collector,
        RequestReader.isMetadataElement
      )

    XMLParsing.urlToSAX("oxf:/org/orbeon/oxf/fr/form-with-metadata.xhtml", metadataFilter, XINCLUDE_ONLY, false)

    val XMLLang    = JXQName("http://www.w3.org/XML/1998/namespace" -> "lang")
    val Operations = JXQName("operations")

    val expected = List(
      StartDocument,
        StartPrefixMapping("sql", "http://orbeon.org/oxf/xml/sql"),
        StartPrefixMapping("fr", "http://orbeon.org/oxf/xml/form-runner"),
        StartPrefixMapping("ev", "http://www.w3.org/2001/xml-events"),
        StartPrefixMapping("xxf", "http://orbeon.org/oxf/xml/xforms"),
        StartPrefixMapping("xs", "http://www.w3.org/2001/XMLSchema"),
        StartPrefixMapping("xh", "http://www.w3.org/1999/xhtml"),
        StartPrefixMapping("exf", "http://www.exforms.org/exf/1-0"),
        StartPrefixMapping("xml", "http://www.w3.org/XML/1998/namespace"),
        StartPrefixMapping("saxon", "http://saxon.sf.net/"),
        StartPrefixMapping("soap", "http://schemas.xmlsoap.org/soap/envelope/"),
        StartPrefixMapping("fb", "http://orbeon.org/oxf/xml/form-builder"),
        StartPrefixMapping("xxi", "http://orbeon.org/oxf/xml/xinclude"),
        StartPrefixMapping("xi", "http://www.w3.org/2001/XInclude"),
        StartPrefixMapping("xsi", "http://www.w3.org/2001/XMLSchema-instance"),
        StartPrefixMapping("xf", "http://www.w3.org/2002/xforms"),
        StartElement("metadata", Atts(Nil)),
          StartElement("application-name", Atts(Nil)),
          EndElement("application-name"),
          StartElement("form-name", Atts(Nil)),
          EndElement("form-name"),
          StartElement("title", Atts(List(XMLLang -> "en"))),
          EndElement("title"),
          StartElement("description", Atts(List(XMLLang -> "en"))),
          EndElement("description"),
          StartElement("title", Atts(List(XMLLang -> "fr"))),
          EndElement("title"),
          StartElement("description", Atts(List(XMLLang -> "fr"))),
          EndElement("description"),
          StartElement("permissions", Atts(Nil)),
            StartElement("permission", Atts(List(Operations -> "read update delete"))),
              StartElement("group-member", Atts(Nil)),
              EndElement("group-member"),
            EndElement("permission"),
            StartElement("permission", Atts(List(Operations -> "read update delete"))),
              StartElement("owner", Atts(Nil)),
              EndElement("owner"),
            EndElement("permission"),
            StartElement("permission", Atts(List(Operations -> "create"))),
            EndElement("permission"),
          EndElement("permissions"),
        StartElement("available", Atts(Nil)),
        EndElement("available"),
        EndElement("metadata"),
        EndPrefixMapping("sql"),
        EndPrefixMapping("fr"),
        EndPrefixMapping("ev"),
        EndPrefixMapping("xxf"),
        EndPrefixMapping("xs"),
        EndPrefixMapping("xh"),
        EndPrefixMapping("exf"),
        EndPrefixMapping("xml"),
        EndPrefixMapping("saxon"),
        EndPrefixMapping("soap"),
        EndPrefixMapping("fb"),
        EndPrefixMapping("xxi"),
        EndPrefixMapping("xi"),
        EndPrefixMapping("xsi"),
        EndPrefixMapping("xf"),
      EndDocument
    )

    assert(expected === collector.events)
  }
}
