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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatest.funspec.AnyFunSpecLike


class RepeatsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("repeat ancestors") {

    val TestDoc =
      <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
           xmlns:xf="http://www.w3.org/2002/xforms"
           xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
           xmlns:ev="http://www.w3.org/2001/xml-events">
        <head>
          <xf:model id="model">
            <xf:instance id="instance">
              <instance xmlns="">
                <department>
                  <employee/>
                  <employee/>
                  <office/>
                </department>
                <department>
                  <employee/>
                  <employee/>
                  <employee/>
                  <office/>
                  <office/>
                  <office/>
                  <office/>
                </department>
              </instance>
            </xf:instance>
          </xf:model>
        </head>
        <body>
          <xf:repeat ref="department" id="repeat-department">
            <xf:repeat ref="employee" id="repeat-employee">

              <xf:output id="my-output"/>

              <xf:group>
                <xf:action id="action1" ev:event="xforms-enabled">
                  <xf:action id="action2"/>
                </xf:action>
              </xf:group>

              <xf:action id="action3" ev:event="xforms-enabled">
                <xf:action id="action4"/>
              </xf:action>

            </xf:repeat>

            <xf:repeat ref="office" id="repeat-office"/>

          </xf:repeat>

          <xf:repeat ref="department" id="repeat-building"/>
        </body>
      </xh:html>.toDocument

    it("must pass all checks") {
      withTestExternalContext { _ =>
        withActionAndDoc(setupDocument(TestDoc)) {
          // Test hierarchy string
          assert(document.staticOps.getRepeatHierarchyString("") == "repeat-department,repeat-employee repeat-department,repeat-office repeat-department,repeat-building")

          val repeatAncestors = Map(
            "department" -> Seq(),
            "employee"   -> Seq("repeat-department"),
            "office"     -> Seq("repeat-department"),
            "building"   -> Seq()
          )

          // Test ancestors of repeat controls
          for {
            (name, ancestors) <- repeatAncestors
            id = "repeat-" + name
          } yield
            assert(document.staticOps.getAncestorRepeatIds(id) == ancestors)

          // Test ancestors of other controls and actions
          for {
            id <- Seq("my-output", "action1", "action2")
          } yield
            assert(document.staticOps.getAncestorRepeatIds(id) == Seq("repeat-employee", "repeat-department"))

          // Test closest common ancestor
          assert(document.staticOps.findClosestCommonAncestorRepeat("repeat-employee", "repeat-office").contains("repeat-department"))

          // TODO: test combination of ancestors for all controls
          // TODO: test with ancestor parts

          // TODO: actions directly nested within repeat do not report the correct ancestors
      //        assert(getDocument.getStaticOps.getAncestorRepeats("action1", null).asScala == Seq("repeat-employee", "repeat-department"))
      //        assert(getDocument.getStaticOps.getAncestorRepeats("action2", null).asScala == Seq("repeat-employee", "repeat-department"))
        }
      }
    }
  }
}