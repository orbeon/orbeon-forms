package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.XFormsContainingDocument

import scala.concurrent.Future
import scala.concurrent.duration.Duration


class AsynchronousSubmissionManager
  extends AsynchronousSubmissionManagerTrait {

  // This can be called for `xxf:join-submissions` (not supported on JS right now), or for regular submission that must
  // wait the completion of the request. This is checked before sending the response to the client, either upon initial
  // load or upon client requests.
  protected def awaitPending(
    containingDocument       : XFormsContainingDocument,
    skipDeferredEventHandling: Boolean,
    getAndClear              : () => List[(Future[Any], Duration)]
  )(implicit
    logger                   : IndentedLogger
  ): Unit = {
    info(s"skipping `awaitPending()` as it's not implemented in the JS environment")
  }

//  private def sendShortCircuitPollEvent(containingDocument: XFormsContainingDocument): Unit =
//    js.Dynamic.global.window.ORBEON.xforms.AjaxClient.createDelayedPollEvent(
//      0,
//      containingDocument.getNamespacedFormId
//    )
}
