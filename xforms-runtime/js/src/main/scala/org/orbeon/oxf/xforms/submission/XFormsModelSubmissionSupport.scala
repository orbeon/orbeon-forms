package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.externalcontext.ExternalContext

import scala.concurrent.Future


object XFormsModelSubmissionSupport extends XFormsModelSubmissionSupportTrait {

  // 2023-10-23: We don't yet implement `replace="all"` in the JS environment.
  def runDeferredSubmissionForUpdate(future: Future[AsyncConnectResult], response: ExternalContext.Response): Unit =
    throw new UnsupportedOperationException("not implemented")
}
