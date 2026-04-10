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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.saxon.om
import org.scalatest.funspec.AnyFunSpecLike


class XFormsSelectControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Update multiple value selection") {

    def updateValueSelection(
      dataValues     : Set[String],
      itemsetValues  : Set[String],
      incomingValues : Set[String]
    ): (Set[String], Set[String], Set[String]) = {

      def setToList(s: Set[String]): List[Left[String, Nothing]] =
        (s map Left.apply).to(List)

      def listToSet(l: List[Item.Value[om.Item]]): Set[String] =
        (
          l collect {
            case Left(s) => s
            case _       => throw new IllegalStateException
          }
        ).to(Set)

      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) = XFormsSelectControl.updateSelection(
        setToList(dataValues),
        setToList(itemsetValues),
        setToList(incomingValues),
        _ => true,
        excludeWhitespaceTextNodes = false
      )

      (listToSet(newlySelectedValues), listToSet(newlyDeselectedValues), listToSet(newInstanceValue))
    }

    it("must deselect one value") {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateValueSelection(dataValues = Set("a", "b", "c"), itemsetValues = Set("a", "b"), incomingValues = Set("a"))

      assert(Set() == newlySelectedValues)
      assert(Set("b") == newlyDeselectedValues)
      assert(Set("a", "c") == newInstanceValue)
    }

    it("must keep all values") {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateValueSelection(dataValues = Set("a", "b", "c"), itemsetValues = Set("a", "b"), incomingValues = Set("a", "b"))

      assert(Set() == newlySelectedValues)
      assert(Set() == newlyDeselectedValues)
      assert(Set("a", "b", "c") == newInstanceValue)
    }

    it("must deselect all values") {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateValueSelection(dataValues = Set("a", "b", "c"), itemsetValues = Set("a", "b"), incomingValues = Set())

      assert(Set() == newlySelectedValues)
      assert(Set("a", "b") == newlyDeselectedValues)
      assert(Set("c") == newInstanceValue)
    }

    it("must both deselect and select values") {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateValueSelection(dataValues = Set("a", "b", "c"), itemsetValues = Set("a", "b", "d"), incomingValues = Set("a", "d"))

      assert(Set("d") == newlySelectedValues)
      assert(Set("b") == newlyDeselectedValues)
      assert(Set("a", "c", "d") == newInstanceValue)
    }

    it("must select multiple values") {
      val (newlySelectedValues, newlyDeselectedValues, newInstanceValue) =
        updateValueSelection(dataValues = Set("a", "b", "c"), itemsetValues = Set("d", "e"), incomingValues = Set("d", "e"))

      assert(Set("d", "e") == newlySelectedValues)
      assert(Set() == newlyDeselectedValues)
      assert(Set("a", "b", "c", "d", "e") == newInstanceValue)
    }
  }

  // See https://github.com/orbeon/orbeon-forms/issues/2517
  describe("Allow setvalue upon xforms-select/xforms-deselect #2517") {
    it(s"must run `xf:setvalue` upon `xforms-deselect`") {
      withTestExternalContext { _ =>
        this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms">

            <xh:head>
              <xf:model id="model" xxf:encrypt-item-values="false">
                <xf:instance id="instance" xxf:exclude-result-prefixes="#all">
                  <instance/>
                </xf:instance>
              </xf:model>
            </xh:head>
            <xh:body>
              <xf:select ref="." appearance="full" id="select">
                <xf:item>
                    <xf:label>Do you have children?</xf:label>
                    <xf:value>true</xf:value>
                </xf:item>
                <xf:setvalue
                    event="xforms-select"
                    ref="."
                    value="true()"/>
                <xf:setvalue
                    event="xforms-deselect"
                    ref="."
                    value="false()"/>
              </xf:select>
            </xh:body>
          </xh:html>.toDocument

        assert("" == getControlValue("select"))

        setControlValue("select", "true")
        assert("true" == getControlValue("select"))

        setControlValue("select", "")
        assert("false" == getControlValue("select"))

        setControlValue("select", "true")
        assert("true" == getControlValue("select"))
      }
    }
  }

  describe("#7586: `xf:select`/`xf:copy` with attributes") {

    it(s"must handle distinct attributes") {
      withTestExternalContext { _ =>
        this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            xmlns:xbl="http://www.w3.org/ns/xbl"
            xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          >
            <xh:head>
              <xf:model
                xxf:xpath-analysis="true"
                xxf:expose-xpath-types="true"
                xxf:analysis.calculate="true"
                id="model">

                  <xf:instance id="instance">
                    <_>
                      <value/>
                    </_>
                  </xf:instance>

                <xf:insert
                  event="insert-nodes"
                  context="value[xs:integer(event('position'))]"
                  origin="xf:attribute(event('name'), event('value'))"/>

                <xf:delete
                  event="delete-nodes"
                  ref="value[xs:integer(event('position'))]/@*[name() = event('name')]"/>

              </xf:model>
          </xh:head>
          <xh:body>
            <xf:select
              id="select-distinct-attributes"
              appearance="full"
              ref="value[1]"
            >
              <xf:item>
                <xf:label>Item 1</xf:label>
                <xf:copy ref="xf:attribute('att1', 'v1')"/>
              </xf:item>
              <xf:item>
                <xf:label>Item 2</xf:label>
                <xf:copy ref="xf:attribute('att2', 'v2')"/>
              </xf:item>
              <xf:item>
                <xf:label>Item 3</xf:label>
                <xf:copy ref="xf:attribute('att3', 'v3')"/>
              </xf:item>
              <xf:item>
                <xf:label>Item 4</xf:label>
                <xf:copy ref="xf:attribute('att4', 'v4')"/>
              </xf:item>
            </xf:select>
          </xh:body>
        </xh:html>.toDocument

        assert(getControlExternalValue("select-distinct-attributes") == "")

        for (bitset <- 0 until 16) {
          document.withOutermostActionHandler {
            for (bit <- 0 until 4)
              if ((bitset & (1 << bit)) != 0)
                dispatch(name = "insert-nodes", effectiveId = "model", Map("position" -> Some("1"), "name" -> Some(s"att${bit+1}"), "value" -> Some(s"v${bit+1}")))
              else
                dispatch(name = "delete-nodes", effectiveId = "model", Map("position" -> Some("1"), "name" -> Some(s"att${bit+1}")))
          }

          val expected = (0 until 4).filter(i => (bitset & (1 << i)) != 0).map(_.toString).mkString(" ")
          assert(getControlExternalValue("select-distinct-attributes") == expected)
        }
      }
    }

    it(s"must handle a combination of attribute and element") {
      withTestExternalContext { _ =>
        this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            xmlns:xbl="http://www.w3.org/ns/xbl"
            xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          >
            <xh:head>
              <xf:model
                xxf:xpath-analysis="true"
                xxf:expose-xpath-types="true"
                xxf:analysis.calculate="true"
                id="model">

                  <xf:instance id="instance">
                    <_>
                      <value/>
                    </_>
                  </xf:instance>

                <xf:insert
                  event="insert-nodes"
                  context="value[xs:integer(event('position'))]"
                  origin="
                    xf:attribute(
                      concat('att', event('suffix')), event('value')
                    ),
                    xf:element(
                      concat('elem', event('suffix')), event('value')
                    )"/>

                <xf:delete
                  event="delete-nodes"
                  ref="
                    value[
                      xs:integer(event('position'))
                    ]/(
                      @*[name() = concat('att', event('suffix'))] |
                      *[name() = concat('elem', event('suffix'))]
                    )"/>

              </xf:model>
          </xh:head>
          <xh:body>
            <xf:select
              id="select-distinct-attributes"
              appearance="full"
              ref="value[1]"
            >
              <xf:item>
                <xf:label>Item 1</xf:label>
                <xf:copy ref="xf:attribute('att1', 'v1'), xf:element('elem1', 'v1')"/>
              </xf:item>
              <xf:item>
                <xf:label>Item 2</xf:label>
                <xf:copy ref="xf:attribute('att2', 'v2'), xf:element('elem2', 'v2')"/>
              </xf:item>
              <xf:item>
                <xf:label>Item 3</xf:label>
                <xf:copy ref="xf:attribute('att3', 'v3'), xf:element('elem3', 'v3')"/>
              </xf:item>
              <xf:item>
                <xf:label>Item 4</xf:label>
                <xf:copy ref="xf:attribute('att4', 'v4'), xf:element('elem4', 'v4')"/>
              </xf:item>
            </xf:select>
          </xh:body>
        </xh:html>.toDocument

        assert(getControlExternalValue("select-distinct-attributes") == "")

        for (bitset <- 0 until 16) {
          document.withOutermostActionHandler {
            for (bit <- 0 until 4)
              if ((bitset & (1 << bit)) != 0)
                dispatch(name = "insert-nodes", effectiveId = "model", Map("position" -> Some("1"), "suffix" -> Some(s"${bit+1}"), "value" -> Some(s"v${bit+1}")))
              else
                dispatch(name = "delete-nodes", effectiveId = "model", Map("position" -> Some("1"), "suffix" -> Some(s"${bit+1}")))
          }

          val expected = (0 until 4).filter(i => (bitset & (1 << i)) != 0).map(_.toString).mkString(" ")
          assert(getControlExternalValue("select-distinct-attributes") == expected)
        }
      }
    }
  }
}
