/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.junit.Test
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatestplus.junit.AssertionsForJUnit

class XXFormsLHHATest extends DocumentTestBase with AssertionsForJUnit {

  @Test def lhhaFunctions(): Unit = {

    this setupDocument
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
         xmlns:xf="http://www.w3.org/2002/xforms"
         xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
         xmlns:ev="http://www.w3.org/2001/xml-events">
        <xh:head>
          <xf:model>
            <xf:instance>
              <value label="My label" help="My help" hint="My hint" alert="My alert"/>
            </xf:instance>

            <xf:bind ref="instance()" constraint="false()"/>

            <xf:instance id="results">
              <results label="" help="" hint="" alert=""/>
            </xf:instance>

            <xf:action ev:event="xforms-ready" context="instance('results')">
              <xf:setvalue ref="@label" value="xxf:label('my-input')"/>
              <xf:setvalue ref="@help" value="xxf:help('my-input')"/>
              <xf:setvalue ref="@hint" value="xxf:hint('my-input')"/>
              <xf:setvalue ref="@alert" value="xxf:alert('my-input')"/>
            </xf:action>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:input id="my-input" ref=".">
            <xf:label ref="@label"/>
            <xf:help ref="@help"/>
            <xf:hint ref="@hint"/>
            <xf:alert ref="@alert"/>
          </xf:input>

          <xf:output id="label" ref="instance('results')/@label"/>
          <xf:output id="help" ref="instance('results')/@help"/>
          <xf:output id="hint" ref="instance('results')/@hint"/>
          <xf:output id="alert" ref="instance('results')/@alert"/>
        </xh:body>
      </xh:html>.toDocument

    // NOTE: Make the control invalid so that the alert message is not empty
    for (lhha <- Seq("label", "help", "hint", "alert"))
      assert(getValueControl(lhha).getValue(EventCollector.Throw) === "My " + lhha)
  }

  @Test def externalLhhaFunctionsAndClasses(): Unit = {

    this setupDocument
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
         xmlns:xf="http://www.w3.org/2002/xforms"
         xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
        <xh:head>
          <xf:model>
            <xf:instance>
              <_>
                <val required="true" readonly="false"/>
                <label>&lt;b&gt;External label&lt;/b&gt;</label>
                <help>External help</help>
                <hint>External hint</hint>
                <alert>External alert</alert>
              </_>
            </xf:instance>

            <xf:bind ref="val" required="@required = 'true'" readonly="@readonly = 'true'" constraint="false()"/>

            <xf:instance id="results">
              <results label="" help="" hint="" alert=""/>
            </xf:instance>

            <xf:action event="xforms-ready" context="instance('results')">
              <xf:setvalue ref="@label" value="xxf:label('my-input')"/>
              <xf:setvalue ref="@help"  value="xxf:help('my-input')"/>
              <xf:setvalue ref="@hint"  value="xxf:hint('my-input')"/>
              <xf:setvalue ref="@alert" value="xxf:alert('my-input')"/>
            </xf:action>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:label id="ext-label" for="my-input" ref="label" mediatype="text/html"/>
          <xf:help  id="ext-help"  for="my-input" ref="help"/>
          <xf:hint  id="ext-hint"  for="my-input" ref="hint"/>
          <xf:alert id="ext-alert" for="my-input" ref="alert"/>

          <xf:input id="my-input" ref="val"/>

          <xf:output id="label" ref="instance('results')/@label"/>
          <xf:output id="help"  ref="instance('results')/@help"/>
          <xf:output id="hint"  ref="instance('results')/@hint"/>
          <xf:output id="alert" ref="instance('results')/@alert"/>
        </xh:body>
      </xh:html>.toDocument

    assert(getValueControl("label").getValue(EventCollector.Throw) === "<b>External label</b>")
    assert(getValueControl("help").getValue(EventCollector.Throw)  === "External help")
    assert(getValueControl("hint").getValue(EventCollector.Throw)  === "External hint")
    assert(getValueControl("alert").getValue(EventCollector.Throw) === "External alert")

    val inputControl = getSingleNodeControl("my-input")
    assert(inputControl.getLabel(EventCollector.Throw).contains("<b>External label</b>"))
    assert(inputControl.isHTMLLabel)

    val extLabelControl = getSingleNodeControl("ext-label").asInstanceOf[org.orbeon.oxf.xforms.control.controls.XFormsLHHAControl]
    assert(extLabelControl.associatedControlsRequired)
    assert(extLabelControl.associatedControlsEmpty.contains(true))
    assert(! extLabelControl.associatedControlsReadonly)

    setControlValueWithEventSearchNested("my-input", "Alice")
    val extLabelAfterFill = getSingleNodeControl("ext-label").asInstanceOf[org.orbeon.oxf.xforms.control.controls.XFormsLHHAControl]
    assert(extLabelAfterFill.associatedControlsRequired)
    assert(extLabelAfterFill.associatedControlsEmpty.contains(false))
  }
}