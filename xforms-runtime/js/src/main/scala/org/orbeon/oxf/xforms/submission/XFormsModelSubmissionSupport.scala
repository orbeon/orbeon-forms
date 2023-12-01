package org.orbeon.oxf.xforms.submission

import cats.effect.IO
import org.orbeon.oxf.externalcontext.ExternalContext


object XFormsModelSubmissionSupport extends XFormsModelSubmissionSupportTrait {

  // 2023-10-23: We don't yet implement `replace="all"` in the JS environment.
  def runDeferredSubmissionForUpdate(computation: IO[AsyncConnectResult], response: ExternalContext.Response): Unit =
    throw new UnsupportedOperationException("not implemented")
}
