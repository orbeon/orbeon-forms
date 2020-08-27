/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import org.junit._
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatestplus.junit.AssertionsForJUnit

class VariableDependenciesTest extends DocumentTestBase with AssertionsForJUnit {

  @Test def variableWithIntermediateControl(): Unit = {
    Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

    this setupDocument
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:ev="http://www.w3.org/2001/xml-events"
           xmlns:xs="http://www.w3.org/2001/XMLSchema"
           xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
        <xh:head>
          <xf:model id="model" xxf:encrypt-item-values="false" xxf:xpath-analysis="true">
            <xf:instance id="instance">
              <form>
                <lang>en</lang>
                <label lang="en">Name</label>
                <label lang="fr">Nom</label>
              </form>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:var id="my-var" name="lang" value="label[@lang = ../lang]"/>

          <xf:input id="my-input" ref="lang"/>

          <xf:output id="my-output" value="''">
            <xf:label ref="$lang"/>
          </xf:output>
        </xh:body>
      </xh:html>.toDocument

    assert(getControl("my-output").getLabel === "Name")

    setControlValue("my-input", "fr")

    assert(getControl("my-output").getLabel === "Nom")
  }
}
