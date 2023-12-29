package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.XFormsContainingDocument

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException}


class AsynchronousSubmissionManager
  extends AsynchronousSubmissionManagerTrait {

  protected def awaitPending(
    containingDocument       : XFormsContainingDocument,
    skipDeferredEventHandling: Boolean,
    getAndClear              : () => List[(Future[Any], Duration)]
  )(implicit
    logger                   : IndentedLogger
  ): Unit = {

    var batch: List[(Future[Any], Duration)] = Nil

    while ({
      batch = getAndClear()
      batch.nonEmpty
    }) {

      val maxDuration = batch.map(_._2).max

      debug(s"awaiting ${batch.size} pending asynchronous submissions for a maximum of $maxDuration")

      // TODO: It would be good to process submissions as soon as one is ready to be processed.
      try {
        Await.ready(Future.sequence(batch.map(_._1)), maxDuration)
        debug(s"batch completed while awaiting pending asynchronous submissions")
      } catch {
        case _: TimeoutException =>
        // Just continue
        debug(s"timeout reached while awaiting pending asynchronous submissions")
      }

      containingDocument.maybeWithOutermostActionHandler(! skipDeferredEventHandling) {
        processCompletedAsynchronousSubmissions(containingDocument)
      }
    }
  }
}
