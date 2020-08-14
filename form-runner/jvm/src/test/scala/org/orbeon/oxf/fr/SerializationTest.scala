/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.state.{DynamicState, XFormsState}
import org.orbeon.oxf.xforms.{Loggers, XFormsContainingDocument}
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.scalatest.funspec.AnyFunSpecLike

class SerializationTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Issue #2197: Restore ids of bound controls") {

    it("must resolve object by id before and after serialization/deserialization") {

      val xhtml =
        <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
             xmlns:xh="http://www.w3.org/1999/xhtml"
             xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
             xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="instance">
                <value/>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:number id="my-number" ref="instance()">
              <xf:label>Number</xf:label>
            </fr:number>
          </xh:body>
        </xh:html>

      withXFormsDocument(xhtml) { doc =>

        assert(doc.resolveObjectByIdInScope("#document", "my-number", None).isDefined)

        val serializedState = XFormsState(
          staticStateDigest = Option(doc.staticState.digest),
          staticState       = Option(doc.staticState.encodedState),
          dynamicState      = Some(DynamicState(doc))
        )

        val restoredDoc = XFormsContainingDocument(serializedState, disableUpdates = false, forceEncryption = false)(Loggers.getIndentedLogger("state"))

        assert(restoredDoc.resolveObjectByIdInScope("#document", "my-number", None).isDefined)
      }
    }
  }

  describe("Issue #2197: Restore inline XBL") {

    it("must resolve object by id before and after serialization/deserialization") {

      val xhtml =
        <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
             xmlns:xh="http://www.w3.org/1999/xhtml"
             xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
             xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
             xmlns:xbl="http://www.w3.org/ns/xbl">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="instance">
                <value/>
              </xf:instance>
            </xf:model>
            <xbl:xbl>
              <xbl:binding id="fr-foo" element="fr|foo">
                <xbl:template>
                  <xbl:content/>
                </xbl:template>
              </xbl:binding>
            </xbl:xbl>
            <xbl:xbl>
              <xbl:binding id="fr-bar" element="fr|bar">
                <xbl:template>
                  <xbl:content/>
                </xbl:template>
              </xbl:binding>
            </xbl:xbl>
          </xh:head>
          <xh:body>
            <fr:bar id="my-bar">
              <fr:foo id="my-foo">
                <fr:number id="my-number" ref="instance()">
                  <xf:label>Number</xf:label>
                </fr:number>
              </fr:foo>
            </fr:bar>
          </xh:body>
        </xh:html>

      withXFormsDocument(xhtml) { doc =>

        assert(doc.resolveObjectByIdInScope("#document", "my-number", None).isDefined)

        val serializedState = XFormsState(
          staticStateDigest = Option(doc.staticState.digest),
          staticState       = Option(doc.staticState.encodedState),
          dynamicState      = Some(DynamicState(doc))
        )

        val restoredDoc = XFormsContainingDocument(serializedState, disableUpdates = false, forceEncryption = false)(Loggers.getIndentedLogger("state"))

        for (id <- List("my-foo", "my-bar", "my-number"))
          assert(restoredDoc.resolveObjectByIdInScope("#document", id, None).isDefined)
      }
    }
  }
}
