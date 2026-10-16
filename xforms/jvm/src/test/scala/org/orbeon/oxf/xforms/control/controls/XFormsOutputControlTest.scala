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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl.hmacURL
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.xforms.Constants.DummyImageUri
import org.scalatest.funspec.AnyFunSpecLike


class XFormsOutputControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Proxying of `xf:output` values with an image mediatype") {

    val signedTmpFile = hmacURL("file:/foo/tmp1.tmp", Some("a.png"), Some("image/png"), Some("1234"))

    val doc =
      <xh:html xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xh="http://www.w3.org/1999/xhtml">
        <xh:head>
          <xf:model>
            <xf:instance id="instance">
              <values>
                <oxf>oxf:/config/properties-base.xml</oxf>
                <data>{DummyImageUri}</data>
                <http>http://example.org/foo/bar.png</http>
                <path>/foo/bar.png</path>
                <unsigned-file>file:/foo/tmp1.tmp</unsigned-file>
                <signed-file>{signedTmpFile}</signed-file>
              </values>
            </xf:instance>
          </xf:model>
        </xh:head>
        <xh:body>
          <xf:output id="oxf-output"           ref="oxf"           mediatype="image/*"/>
          <xf:output id="data-output"          ref="data"          mediatype="image/*"/>
          <xf:output id="http-output"          ref="http"          mediatype="image/*"/>
          <xf:output id="path-output"          ref="path"          mediatype="image/*"/>
          <xf:output id="unsigned-file-output" ref="unsigned-file" mediatype="image/*"/>
          <xf:output id="signed-file-output"   ref="signed-file"   mediatype="image/*"/>
        </xh:body>
      </xh:html>.toDocument

    it("must refuse `oxf:` and unsigned `file:` values and proxy the other values") {
      withTestExternalContext { _ =>
        withXFormsDocument(doc) { _ =>
          assert(getControlExternalValue("oxf-output")           == DummyImageUri)
          assert(getControlExternalValue("unsigned-file-output") == DummyImageUri)
          for (id <- List("data-output", "http-output", "path-output", "signed-file-output"))
            assert(getControlExternalValue(id).startsWith("/xforms-server/dynamic/"), id)
        }
      }
    }
  }
}
