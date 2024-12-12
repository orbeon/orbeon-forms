package org.orbeon.oxf.fr

import org.orbeon.connection.StreamedContent
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.submission.SubmissionUtils
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike


class BackgroundProcessTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Background processes") {

    val DocumentId = "b1a724db78a99a060adfd169db1ce31e0eb41f4f"

    def runAndAssert(
      processNameAndPhaseOpt: Option[(String, Option[String])],
      expected              : om.NodeInfo
    ): Unit = {
      val (_, content, response) =
        runFormRunnerReturnContent(
          "issue",
          "6669",
          "new",
          documentId = Some(DocumentId), // so we can compare the output more easily
          query      =
            ("return-data" -> "true") ::
            processNameAndPhaseOpt.collect{ case (name, _)        => "fr-process-name"  -> name  }.toList :::
            processNameAndPhaseOpt.collect{ case (_, Some(phase)) => "fr-process-phase" -> phase }.toList,
          background = true,
          content    = Some(StreamedContent.Empty)
        )

//      val s = new String(SubmissionUtils.inputStreamToByteArray(content.stream), CharsetNames.Utf8)
//      println(s"xxx response: ${response.statusCode}, ${response.headers}")
//      println(s)

      assertXMLElementsIgnoreNamespacesInScope(
        expected,
        XFormsCrossPlatformSupport.readTinyTree(
          XPath.GlobalConfiguration,
            content.stream,
            null,
            handleXInclude = false,
            handleLexical  = false
        ),
      )
    }

    it("must run all processes defined by properties") {

      val expected: om.NodeInfo =
        <response>
          <document-id>{DocumentId}</document-id>
          <data>
            <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
              <section-1>
                <before-data-no-initial-value>before-data</before-data-no-initial-value>
                <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                <after-data-no-initial-value>after-data</after-data-no-initial-value>
                <after-data-with-initial-value>after-data</after-data-with-initial-value>
                <after-controls-no-initial-value>after-controls</after-controls-no-initial-value>
                <after-controls-with-initial-value>after-controls</after-controls-with-initial-value>
              </section-1>
            </form>
          </data>
          <process-success>true</process-success>
        </response>

      runAndAssert(
        processNameAndPhaseOpt = None,
        expected               = expected
      )
    }

    describe("with process passed as parameter") {

      val expectedWithDefaultPhase = List[(String, Set[Option[String]], om.NodeInfo)](
        (
          "my-before-data",
          Set(Some("before-data")),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>my-before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value>after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value>after-controls</after-controls-no-initial-value>
                  <after-controls-with-initial-value>after-controls</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-before-data",
          Set(Some("after-data")),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>my-before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>my-before-data</before-data-with-initial-value>
                  <after-data-no-initial-value></after-data-no-initial-value>
                  <after-data-with-initial-value>initial-value</after-data-with-initial-value>
                  <after-controls-no-initial-value>after-controls</after-controls-no-initial-value>
                  <after-controls-with-initial-value>after-controls</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-before-data",
          Set(Some("after-controls"), None),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>my-before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>my-before-data</before-data-with-initial-value>
                  <after-data-no-initial-value>after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value></after-controls-no-initial-value>
                  <after-controls-with-initial-value>initial-value</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-after-data",
          Set(Some("before-data")),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value></before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value>after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value>after-controls</after-controls-no-initial-value>
                  <after-controls-with-initial-value>after-controls</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-after-data",
          Set(Some("after-data")),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value>my-after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>my-after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value>after-controls</after-controls-no-initial-value>
                  <after-controls-with-initial-value>after-controls</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-after-data",
          Set(Some("after-controls"), None),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value>my-after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>my-after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value></after-controls-no-initial-value>
                  <after-controls-with-initial-value>initial-value</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-after-controls",
          Set(Some("before-data")),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value></before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value>after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value>after-controls</after-controls-no-initial-value>
                  <after-controls-with-initial-value>after-controls</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-after-controls",
          Set(Some("after-data")),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value></after-data-no-initial-value>
                  <after-data-with-initial-value>initial-value</after-data-with-initial-value>
                  <after-controls-no-initial-value>after-controls</after-controls-no-initial-value>
                  <after-controls-with-initial-value>after-controls</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-after-controls",
          Set(Some("after-controls"), None),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value>after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value>my-after-controls</after-controls-no-initial-value>
                  <after-controls-with-initial-value>my-after-controls</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
      )

      for {
        (processName, phases, expected) <- expectedWithDefaultPhase
        phaseOpt                        <- phases
      } locally {
        it(s"must run process `$processName` with phase `$phaseOpt`") {
          runAndAssert(
            processNameAndPhaseOpt = Some(processName -> phaseOpt),
            expected               = expected
          )
        }
      }
    }

    describe("with asynchronous processes") {

      val expectedWithDefaultPhase = List[(String, Set[Option[String]], om.NodeInfo)](
        (
          "my-async-success",
          Set(None),
          <response>
            <document-id>{DocumentId}</document-id>
            <data>
              <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
                <section-1>
                  <before-data-no-initial-value>before-data</before-data-no-initial-value>
                  <before-data-with-initial-value>initial-value</before-data-with-initial-value>
                  <after-data-no-initial-value>after-data</after-data-no-initial-value>
                  <after-data-with-initial-value>after-data</after-data-with-initial-value>
                  <after-controls-no-initial-value>my-after-async-completion</after-controls-no-initial-value>
                  <after-controls-with-initial-value>my-after-async-completion</after-controls-with-initial-value>
                </section-1>
              </form>
            </data>
            <process-success>true</process-success>
          </response>
        ),
        (
          "my-async-failure",
          Set(None),
          <response>
            <document-id>{DocumentId}</document-id>
            <process-success>false</process-success>
          </response>
        ),
      )

      for {
        (processName, phases, expected) <- expectedWithDefaultPhase
        phaseOpt                        <- phases
      } locally {
        it(s"must run process `$processName` with phase `$phaseOpt`") {
          runAndAssert(
            processNameAndPhaseOpt = Some(processName -> phaseOpt),
            expected               = expected
          )
        }
      }
    }
  }
}
