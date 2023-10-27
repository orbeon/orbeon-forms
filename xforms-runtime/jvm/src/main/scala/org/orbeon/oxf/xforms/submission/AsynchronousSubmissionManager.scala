package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.XFormsContainingDocument

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


class AsynchronousSubmissionManager
  extends AsynchronousSubmissionManagerTrait {

  protected def awaitPending(
    containingDocument: XFormsContainingDocument,
    get               : () => List[Future[ConnectResult]],
    clear             : () => Unit
  )(implicit
    logger            : IndentedLogger
  ): Unit =
    while (get().nonEmpty) {

      val batch = get()
      clear()

      debug(s"awaiting ${batch.size} pending asynchronous submissions")

      // TODO: It would be good to process submissions as soon as one is ready to be processed.
      Await.ready(Future.sequence(batch), Duration.Inf)
      processCompletedAsynchronousSubmissions(containingDocument)
    }
}
