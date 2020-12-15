package org.orbeon.oxf.fr

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.NodeInfo


trait FormRunnerEmailBackend {

  // Only used from `email-form.xsl` and not needed offline.
  //@XPathFunction
  def emailAttachmentFilename(
    data           : NodeInfo,
    attachmentType : String,
    app            : String,
    form           : String
  ): Option[String] = {

    // NOTE: We don't use `FormRunnerParams()` for that this works in tests.
    // Callees only require, as of 2018-05-31, `app` and `form`.
    implicit val params =
      FormRunnerParams(
        app         = app,
        form        = form,
        formVersion = 1,
        document    = None,
        mode        = "email"
      )

    for {
      (expr, mapping) <- formRunnerPropertyWithNs(s"oxf.fr.email.$attachmentType.filename")
      trimmedExpr     <- expr.trimAllToOpt
      name            = process.SimpleProcess.evaluateString(trimmedExpr, data, mapping)
    } yield {
      // This appears necessary for non-ASCII characters to make it through.
      // Verified that this works with GMail.
      javax.mail.internet.MimeUtility.encodeText(name, CharsetNames.Utf8, null)
    }
  }
}
