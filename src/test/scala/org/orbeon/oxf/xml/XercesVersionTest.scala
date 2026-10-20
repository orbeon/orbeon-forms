/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import org.orbeon.oxf.test.ResourceManagerSupport
import org.orbeon.oxf.xml.dom.IOSupport
import org.scalatest.funspec.AnyFunSpecLike


// Guards the checked-in Xerces fork (`lib/runtime/xerces-2.12-orbeon.jar`, rebuilt with `ant jarjar`): an older
// base version must not come back silently. Update the expected version when upgrading the fork.
class XercesVersionTest extends AnyFunSpecLike with ResourceManagerSupport {

  describe("Forked Xerces") {

    it("must be built from Xerces-J 2.12.2") {
      assert(org.orbeon.apache.xerces.impl.Version.getVersion === "Xerces-J 2.12.2")
    }

    it("must be the parser behind `XMLParsing`") {
      val reader = XMLParsing.newXMLReader(ParserConfiguration.Plain)
      assert(reader.getClass.getName.startsWith("org.orbeon.oxf.xml.xerces."))
      assert(reader.isInstanceOf[org.orbeon.apache.xerces.parsers.SAXParser])
    }

    it("must parse a document with an internal subset, a predefined entity and a character reference") {
      val doc = IOSupport.readOrbeonDom("""<!DOCTYPE r [<!ELEMENT r (#PCDATA)>]><r>&amp;&#65;b</r>""")
      assert(doc.getRootElement.getName === "r")
      assert(doc.getRootElement.getText === "&Ab")
    }

    it("must handle carriage returns in entity declarations without hanging (CVE-2022-23437)") {
      val doc = IOSupport.readOrbeonDom(
        """<!DOCTYPE r [
          |  <!ENTITY cr "&#13;">
          |  <!ENTITY line "line1&cr;line2">
          |]><r>&line;</r>""".stripMargin
      )
      assert(doc.getRootElement.getName === "r")
    }
  }
}
