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
package org.orbeon.oxf.xforms.control

import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.NodeInfoConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatestplus.junit.AssertionsForJUnit

class BindingTest extends DocumentTestBase with AssertionsForJUnit {

  @Test def singleNodeBinding(): Unit = {

    val xmlDoc = {
      val elem =
        <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
             xmlns:xh="http://www.w3.org/1999/xhtml"
             xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <xh:head>
            <xf:model>

              <xf:instance id="fr-form-instance">
                <form>
                  <foo bar="43">42</foo>
                  44
                  <!-- Nice day -->
                  <?xml-stylesheet type="text/xsl" href="style.xsl"?>
                </form>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <xf:input id="element-input" ref="foo"/>
            <xf:input id="attribute-input" ref="foo/@bar"/>
            <xf:input id="text-input"    ref="text()[normalize-space()]"/>
            <xf:input id="comment-input" ref="comment()"/>
            <xf:input id="pi-input" ref="processing-instruction()"/>
            <xf:input id="complex-input" ref="."/>
            <xf:input id="document-input" ref="/"/>
          </xh:body>
        </xh:html>

      elemToDocumentInfo(elem, readonly = false)
    }

    // Check source document
    val instanceRoot = xmlDoc descendant "form" head

    val tests = Seq(*, Text, Comment, PI)

    tests foreach { test =>
      assert(instanceRoot / test nonEmpty)
    }

    // Check relevance and reading control bindings
    this setupDocument unsafeUnwrapElement(xmlDoc.rootElement).getDocument

    val initialNameValues = Seq(
      "element-input"     -> "42",
      "attribute-input"   -> "43",
      "text-input"        -> "44",
      "comment-input"     -> "Nice day",
      "pi-input"          -> """type="text/xsl" href="style.xsl""""
    )

    initialNameValues foreach { case (name, value) =>
      assert(isRelevant(name))
      assert(getValueControl(name).getValue(EventCollector.Throw).trimAllToEmpty === value)
    }

    assert(! isRelevant("complex-input"))
    assert(! isRelevant("document-input"))

    // Check writing control bindings and reading back
    val newNameValues = Seq(
      "element-input"     -> "Mercury",
      "attribute-input"   -> "Mars",
      "text-input"        -> "Venus",
      "comment-input"     -> "Jupiter",
      "pi-input"          -> "Saturn"
    )

    newNameValues foreach { case (name, value) =>
      setControlValue(name, value)
      assert(getValueControl(name).getValue(EventCollector.Throw) === value)
    }

    // Set empty value on text node
    setControlValue("text-input", "")
    assert(! isRelevant("text-input"))
  }
}
