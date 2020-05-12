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
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.saxon.om
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.compat._

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
        setToList(dataValues), setToList(itemsetValues), setToList(incomingValues), excludeWhitespaceTextNodes = false
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
          </xh:html>

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
}
