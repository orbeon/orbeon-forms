package org.orbeon.oxf.fr

import org.orbeon.oxf.test.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.oxf.fr.process.SimpleProcess
import org.scalatest.funspec.AnyFunSpecLike
import org.orbeon.oxf.fr.FormRunnerPersistence.*

class SendRelevantTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport
     with XFormsSupport
     with XMLSupport {

  describe("""`fr:relevant="false"` in 4.0.0 format""") {

    val processDefinition =
      s"""
        send(
          uri                    = "/fr/service/custom/orbeon/echo",
          content                = "xml",
          method                 = "post",
          replace                = "instance",
          annotate               = "relevant=fr:relevant",
          nonrelevant            = "keep",
          $DataFormatVersionName = "${DataFormatVersion.V400.entryName}"
        )
      """

    def withForm(action: () => Unit, expected: NodeInfo): Unit = {
      val (processorService, docOpt, _) =
        runFormRunner("tests", "send-relevant", "new")
      withTestExternalContext { _ =>
        withFormRunnerDocument(processorService, docOpt.get) {
          action()
          SimpleProcess.runProcess("tests", processDefinition)
          val result = instance("fr-send-submission-response").get.root
          assertXMLDocumentsIgnoreNamespacesInScope(expected.root, result)
        }
      }
    }

    it("must have no annotation for the default case") {
      withForm(
        action   = { () => },
        expected =
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
            <config-section>
              <show-section>true</show-section>
              <show-grid>true</show-grid>
            </config-section>
            <section>
              <control/>
            </section>
          </form>
      )
    }

    it("""must have `fr:relevant="false"` on section""") {
      withForm(
        action = { () =>
          val showSectionEffId =
            resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("show-section-control").get.effectiveId
          setControlValueWithEventSearchNested(showSectionEffId, "false")
        },
        expected =
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
            <config-section>
              <show-section>false</show-section>
              <show-grid>true</show-grid>
            </config-section>
            <section fr:relevant="false">
              <control/>
            </section>
          </form>
      )
    }

    it("""must have `fr:relevant="false"` on grid""") {
      withForm(
        action = { () =>
          val showGridEffId =
            resolveObject[org.orbeon.oxf.xforms.control.XFormsControl]("show-grid-control").get.effectiveId
          setControlValueWithEventSearchNested(showGridEffId, "false")
        },
        expected =
          <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
            <config-section>
              <show-section>true</show-section>
              <show-grid>false</show-grid>
            </config-section>
            <section>
              <control fr:relevant="false"/>
            </section>
          </form>
      )
    }
  }
}
