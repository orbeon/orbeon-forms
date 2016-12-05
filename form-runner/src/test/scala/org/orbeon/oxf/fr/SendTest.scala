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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport, XMLSupport}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.scalatest.FunSpecLike
import org.orbeon.scaxon.XML._

class SendTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with FunSpecLike
     with FormRunnerSupport
     with XFormsSupport
     with XMLSupport {


  // TODO: remove fr:metadata

  val DataFormatVersion400  = "4.0.0"
  val DataFormatVersionEdge = "edge"

  val Expected =
    List(
      (DataFormatVersion400, false, true) →
        <form>
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
      (DataFormatVersion400, true, true) →
        <form>
          <section-1>
            <control-1.1/>
            <grid-1>
              <control-1.2/>
            </grid-1>
          </section-1>
        </form>,
      (DataFormatVersionEdge, false, true) →
        <form>
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
      (DataFormatVersionEdge, true, true) →
        <form>
          <section-1>
            <control-1.1/>
            <grid-1>
              <grid-1-iteration>
                <control-1.2/>
              </grid-1-iteration>
            </grid-1>
          </section-1>
        </form>,
      (DataFormatVersion400, false, false) →
        <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
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
      (DataFormatVersion400, true, false) →
        <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <section-1>
            <control-1.1 fr:foo="42"/>
            <grid-1>
              <control-1.2 fr:foo="43"/>
            </grid-1>
          </section-1>
          <fr:metadata/>
        </form>,
      (DataFormatVersionEdge, false, false) →
        <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
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
      (DataFormatVersionEdge, true, false) →
        <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
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

  describe("The `send` action") {
    for (((dataFormatVersion, prune, pruneMetadata), expected) ← Expected) {
      it(s"""must pass with `data-format-version = "$dataFormatVersion"`, `prune = "$prune"` and `prune-metadata = "$pruneMetadata"`""") {

        val (processorService, docOpt, _) =
          runFormRunner("tests", "send-action", "new", document = "", noscript = false, initialize = true)

        setupResourceManagerTestPipelineContext()

        withFormRunnerDocument(processorService, docOpt.get) {

          XFormsAPI.dispatch(
            name       = "my-run-process",
            targetId   = FormRunner.FormModel,
            properties = Map(
              "process" → Some(
                s"""
                  send(
                    uri                 = "/fr/service/custom/orbeon/echo",
                    data-format-version = "$dataFormatVersion",
                    content             = "xml",
                    method              = "post",
                    replace             = "instance",
                    prune               = "$prune",
                    prune-metadata      = "$pruneMetadata"
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
}
