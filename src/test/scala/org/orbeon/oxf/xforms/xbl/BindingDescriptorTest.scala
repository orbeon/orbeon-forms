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
package org.orbeon.oxf.xforms.xbl

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.scaxon.XML._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xml.XMLConstants._
import org.dom4j.QName
import org.orbeon.oxf.fb.FormBuilder._

class BindingDescriptorTest extends AssertionsForJUnit {

    val ComponentsDocument: NodeInfo =
        <components xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    xmlns:xf="http://www.w3.org/2002/xforms"
                    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                    xmlns:xbl="http://www.w3.org/ns/xbl">
            <xbl:xbl>
                <xbl:binding element="xf|input"/>
                <xbl:binding element="fr|number, xf|input:xxf-type('xs:decimal')"/>
            </xbl:xbl>
        </components>


    val XF  = XFORMS_NAMESPACE_URI
    val XS  = XSD_URI
    val XBL = XBL_NAMESPACE_URI
    val FR  = "http://orbeon.org/oxf/xml/form-runner"

    val Bindings = ComponentsDocument.rootElement child (XBL → "xbl") child (XBL → "binding")

    @Test def testNewElementName(): Unit = {
        assert(Some(FR → "number": QName) === newElementName(XF → "input",  XS → "decimal",  Bindings))
        assert(Some(FR → "number": QName) === newElementName(XF → "input",  XF → "decimal",  Bindings))
        assert(Some(XF → "input" : QName) === newElementName(FR → "number", XS → "string",   Bindings))
        assert(Some(XF → "input" : QName) === newElementName(FR → "number", XS → "string",   Bindings))
        assert(None                       === newElementName(XF → "input",  XS → "boolean",  Bindings))
    }
}
