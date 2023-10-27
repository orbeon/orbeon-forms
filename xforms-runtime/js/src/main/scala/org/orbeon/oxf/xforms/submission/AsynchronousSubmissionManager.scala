package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.XFormsContainingDocument

import scala.concurrent.Future


class AsynchronousSubmissionManager
  extends AsynchronousSubmissionManagerTrait {

  protected def awaitPending(
    containingDocument: XFormsContainingDocument,
    get               : () => List[Future[ConnectResult]],
    clear             : () => Unit
  )(implicit
    logger            : IndentedLogger
  ): Unit =
    info(s"skipping `awaitPending()` as it's not implemented in the JS environment")

//  private def sendShortCircuitPollEvent(containingDocument: XFormsContainingDocument): Unit =
//    js.Dynamic.global.window.ORBEON.xforms.AjaxClient.createDelayedPollEvent(
//      0,
//      containingDocument.getNamespacedFormId
//    )
}
