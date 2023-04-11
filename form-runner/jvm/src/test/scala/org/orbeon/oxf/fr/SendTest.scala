/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.test._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

class SendTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XFormsSupport
     with XMLSupport {

  import FormRunnerPersistence._

  describe("The `send` action") {

    val Expected =
      List(
        (DataFormatVersion.V400, false, true) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
            <section-1>
              <control-1.1/>
              <grid-1>
                <control-1.2/>
              </grid-1>
            </section-1>
            <section-2>
              <control-2.1/>
              <grid-2>
                <control-2.2/>
              </grid-2>
            </section-2>
          </form>,
        (DataFormatVersion.V400, true, true) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
            <section-1>
              <control-1.1/>
              <grid-1>
                <control-1.2/>
              </grid-1>
            </section-1>
          </form>,
        (DataFormatVersion.Edge, false, true) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="2019.1.0">
            <section-1>
              <control-1.1/>
              <grid-1>
                <grid-1-iteration>
                  <control-1.2/>
                </grid-1-iteration>
              </grid-1>
            </section-1>
            <section-2>
              <control-2.1/>
              <grid-2>
                <grid-2-iteration>
                  <control-2.2/>
                </grid-2-iteration>
              </grid-2>
            </section-2>
          </form>,
        (DataFormatVersion.Edge, true, true) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="2019.1.0">
            <section-1>
              <control-1.1/>
              <grid-1>
                <grid-1-iteration>
                  <control-1.2/>
                </grid-1-iteration>
              </grid-1>
            </section-1>
          </form>,
        (DataFormatVersion.V400, false, false) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
            <section-1>
              <control-1.1 fr:foo="42"/>
              <grid-1>
                <control-1.2 fr:foo="43"/>
              </grid-1>
            </section-1>
            <section-2>
              <control-2.1 fr:foo="44"/>
              <grid-2>
                <control-2.2 fr:foo="45"/>
              </grid-2>
            </section-2>
            <fr:metadata/>
          </form>,
        (DataFormatVersion.V400, true, false) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
            <section-1>
              <control-1.1 fr:foo="42"/>
              <grid-1>
                <control-1.2 fr:foo="43"/>
              </grid-1>
            </section-1>
            <fr:metadata/>
          </form>,
        (DataFormatVersion.Edge, false, false) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="2019.1.0">
            <section-1>
              <control-1.1 fr:foo="42"/>
              <grid-1>
                <grid-1-iteration>
                  <control-1.2 fr:foo="43"/>
                </grid-1-iteration>
              </grid-1>
            </section-1>
            <section-2>
              <control-2.1 fr:foo="44"/>
              <grid-2>
                <grid-2-iteration>
                  <control-2.2 fr:foo="45"/>
                </grid-2-iteration>
              </grid-2>
            </section-2>
            <fr:metadata/>
          </form>,
        (DataFormatVersion.Edge, true, false) ->
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="2019.1.0">
            <section-1>
              <control-1.1 fr:foo="42"/>
              <grid-1>
                <grid-1-iteration>
                  <control-1.2 fr:foo="43"/>
                </grid-1-iteration>
              </grid-1>
            </section-1>
            <fr:metadata/>
          </form>
      )

    for (((dataFormatVersion, prune, pruneMetadata), expected) <- Expected) {
      it(s"""must pass with `$DataFormatVersionName = "${dataFormatVersion.entryName}"`, `prune = "$prune"` and `$PruneMetadataName = "$pruneMetadata"`""") {

        val (processorService, docOpt, _) =
          runFormRunner("tests", "send-action", "new", document = "", initialize = true)

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, docOpt.get) {

            XFormsAPI.dispatch(
              name       = "my-run-process",
              targetId   = Names.FormModel,
              properties = Map(
                "process" -> Some(
                  s"""
                    send(
                      uri                    = "/fr/service/custom/orbeon/echo",
                      $DataFormatVersionName = "${dataFormatVersion.entryName}",
                      content                = "xml",
                      method                 = "post",
                      replace                = "instance",
                      prune                  = "$prune",
                      $PruneMetadataName     = "$pruneMetadata"
                    )
                  """
                )
              )
            )

            val result = instance("fr-send-submission-response").get.root
            assertXMLDocumentsIgnoreNamespacesInScope((expected: NodeInfo).root, result)
          }
        }

      }
    }

    it("must send metadata for relevant controls") {

      val ExpectedMetadata: NodeInfo =
        <metadata>
          <control for="56259ab2f1a2484d960fa1caf7dc3682f77f4f9e" name="section-1" type="section">
            <resources lang="en">
              <label>Section 1</label>
            </resources>
          </control>
          <control for="603fc7fe572459bec642cfb5c867916fff656d8c" name="control-1.1" type="input">
            <resources lang="en">
              <label>Control 1.1</label>
              <hint/>
            </resources>
            <value/>
          </control>
          <control for="1c5e5c4a4cb44407ba52d43adf303239915a96ce" name="control-1.2" type="tinymce">
            <resources lang="en">
              <label>Control 1.2</label>
              <hint/>
            </resources>
            <value/>
          </control>
        </metadata>

      val (processorService, docOpt, _) =
        runFormRunner("tests", "send-action", "new", document = "", initialize = true)

      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, docOpt.get) {

          XFormsAPI.dispatch(
            name       = "my-run-process",
            targetId   = Names.FormModel,
            properties = Map(
              "process" -> Some(
                s"""
                  send(
                    uri     = "/fr/service/custom/orbeon/echo",
                    content = "metadata",
                    method  = "post",
                    replace = "instance"
                  )
                """
              )
            )
          )

          val result = instance("fr-send-submission-response").get.root
          assertXMLDocumentsIgnoreNamespacesInScope(ExpectedMetadata.root, result)
        }
      }

    }
  }
}
