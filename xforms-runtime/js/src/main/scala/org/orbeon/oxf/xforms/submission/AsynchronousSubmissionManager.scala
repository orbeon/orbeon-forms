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
  ): Unit =
    info(s"skipping `awaitPending()` as it's not implemented in the JS environment")

  // If we have pending request submissions, that is they were newly started in this submission, in the JS
  // environment we can't process them immediately as we need the single thread to return to the main event loop.
  // So we schedule an immediate poll event to reduce the latency. For submissions created in previous requests, we
  // schedule a poll event with the regular delay.
  protected def addClientPollEventIfNeeded(containingDocument: XFormsContainingDocument, hasRequestPending: Boolean): Unit =
    if (hasRequestPending)
      addClientPollEventIfNeeded(containingDocument, 0)
    else if (hasPendingAsynchronousSubmissions)
      addClientPollEventIfNeeded(containingDocument, containingDocument.getSubmissionPollDelay)
}
