package org.orbeon.oxf.fr

import cats.implicits.catsSyntaxOptionId
import org.orbeon.connection.{BufferedContent, StreamedContent}
import org.orbeon.oxf.externalcontext.SimpleSession
import org.orbeon.oxf.fr.FormRunner.InternalValidateSelectionControlsChoicesParam
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport, XMLSupport}
import org.orbeon.oxf.util.CoreUtils.{BooleanOps, PipeOps}
import org.orbeon.oxf.util.{SecureUtils, XPath}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike


class FormRunnerImportValidationTest
  extends DocumentTestBase
     with AnyFunSpecLike
     with ResourceManagerSupport
     with XMLSupport
     with XFormsSupport
     with FormRunnerSupport {

  describe("Excel import validation") {

    val Expected = List(
      (("Orbeon Demo_ Feedback Form (en) (headings).xlsx",                    true,  "excel-headings"),     (6, 6, 2)),
      (("Orbeon Demo_ Feedback Form (en) (headings).xlsx",                    false, "excel-headings"),     (6, 6, 3)),
      (("Orbeon Demo_ Feedback Form (en) (ranges, missing required).xlsx",    true,  "excel-named-ranges"), (1, 1, 0)),
      (("Orbeon Demo_ Feedback Form (en) (ranges, incorrect selection).xlsx", true,  "excel-named-ranges"), (1, 1, 0)),
      (("Orbeon Demo_ Feedback Form (en) (ranges, incorrect selection).xlsx", false, "excel-named-ranges"), (1, 1, 1)),
      (("Orbeon Demo_ Feedback Form (en) (ranges, incorrect email).xlsx",     true,  "excel-named-ranges"), (1, 1, 0)),
      (("Orbeon Demo_ Feedback Form (en) (ranges, valid).xlsx",               true,  "excel-named-ranges"), (1, 1, 1)),
    )

    for (((filename, validateSelectionControls, format), (total, processed, succeeded)) <- Expected)
      it(s"must correctly validate and import `$filename` in `$format` format, validate selection controls = $validateSelectionControls") {
        withTestExternalContext { _ =>

          val requestContent =
            BufferedContent(
              body =
                <upload>
                  <file
                    filename={filename}
                    mediatype="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    size="12345">oxf:/forms/issue/7660/form/{filename}</file>
                  <file-format>{format}</file-format>
                </upload>
                .toString()
                .getBytes("UTF-8"),
              contentType = "application/xml;charset=UTF-8".some,
              title       = None
            )

          // Because `validate` stores into the session `org.orbeon.fr.import.invalid-rows`, and `import` relies on this
          val session =
            new SimpleSession(SecureUtils.randomHexId) |!>
              XFormsStateManager.sessionCreated

          for (mode <- List("validate", "import")) {
            val (_, _, _, response) =
              runFormRunnerReturnAll(
                app             = "issue",
                form            = "7660",
                mode            = mode,
                documentId      = None,
                content         = StreamedContent(requestContent).some,
                background      = true,
                credentials     = None,
                query           =
                  InternalValidateSelectionControlsChoicesParam -> FormRunnerOperationsEncryption.encryptString(validateSelectionControls.toString) ::
                  (mode == "import").list("import-invalid-data" -> false.toString),
                providedSession = session.some
              )

            val responseRootElem =
              XFormsCrossPlatformSupport.readTinyTree(
                XPath.GlobalConfiguration,
                response.content.stream,
                systemId       = "",
                handleXInclude = false,
                handleLexical  = false
              ).rootElement

            assert(StatusCode.isSuccessCode(response.statusCode))

            assert(responseRootElem.elemValue("total").toInt == total)
            assert(responseRootElem.elemValue("processed").toInt == processed)
            assert(responseRootElem.elemValue("succeeded").toInt == succeeded)
          }
        }
    }
  }
}
