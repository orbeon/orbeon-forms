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

import org.orbeon.oxf.xforms.analysis.model.Model
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

        def assertVaryTypes(oldControlName: QName, oldDatatype: QName, newDatatype: QName)(result: Option[QName]) =
            for {
                oldT ← List(oldDatatype, Model.getVariationTypeOrKeep(oldDatatype))
                newT ← List(newDatatype, Model.getVariationTypeOrKeep(newDatatype))
            } locally {
                assert(result === newElementName(oldControlName,  oldT,  newT,  Bindings))
            }

        assertVaryTypes(XF → "input",  XS → "string",  XS → "decimal")(Some(FR → "number"))
        assertVaryTypes(FR → "number", XS → "string",  XS → "string" )(None)
        assertVaryTypes(FR → "number", XS → "decimal", XS → "string" )(Some(XF → "input"))
        assertVaryTypes(FR → "number", XS → "decimal", XS → "boolean")(Some(XF → "input"))
        assertVaryTypes(XF → "input",  XS → "string",  XS → "boolean")(None)
        assertVaryTypes(XF → "input",  XS → "boolean", XS → "boolean")(None)
    }
}
