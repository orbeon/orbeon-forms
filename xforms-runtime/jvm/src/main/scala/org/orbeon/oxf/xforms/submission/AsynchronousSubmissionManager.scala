package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.xforms.XFormsContainingDocument

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, TimeoutException}


class AsynchronousSubmissionManager
  extends AsynchronousSubmissionManagerTrait {

  protected def awaitPending(
    containingDocument       : XFormsContainingDocument,
    skipDeferredEventHandling: Boolean,
    getAndClear              : () => List[(PendingCompletion, Duration)]
  )(implicit
    logger                   : IndentedLogger
  ): Unit = {

    val initialTime = System.currentTimeMillis()

    var batch: List[(PendingCompletion, Duration)] = getAndClear()

    def computeExpirationTime(durations: List[Duration]): Option[Long] =
      durations.nonEmpty.option(durations.max).flatMap {
        maxDuration => (maxDuration != Duration.Inf).option(initialTime + maxDuration.toMillis)
      }

    var expirationTimeOpt = computeExpirationTime(batch.map(_._2))

    while (batch.nonEmpty) {

      debug(s"awaitPending: processing batch of ${batch.size} pending asynchronous submissions, expirationTime = $expirationTimeOpt")

      val currentTime = System.currentTimeMillis()

      if (expirationTimeOpt.exists(currentTime > _)) {
        debug(s"awaitPending: timeout reached before awaiting pending asynchronous submissions, ${batch.size} pending submissions")
        return
      }

      val maxDuration =
        expirationTimeOpt
          .map(expirationTime =>  Duration(expirationTime - currentTime, "ms"))
          .getOrElse(Duration.Inf)

      debug(s"awaitPending: awaiting ${batch.size} pending asynchronous submissions for a maximum of $maxDuration")
      try {
        Await.ready(Future.firstCompletedOf(batch.map(_._1.future)), maxDuration)
      } catch {
        case _: TimeoutException =>
          debug(s"awaitPending: timeout reached while awaiting pending asynchronous submissions, ${batch.size} pending submissions")
          return
        case _: InterruptedException =>
          // TODO: anything to do here?
          debug(s"awaitPending: interrupted while awaiting pending asynchronous submissions")
        case t: Throwable =>
          debug(s"awaitPending: error while awaiting pending asynchronous submissions: $t")
      }

      // `Future`s in the batch that are completed at this instant are already in the completion queue, since we
      // carefully sequence operations to add the `Future` to the completion queue before completing it. It is
      // therefore save to remove them from the batch just before calling `processCompletedAsynchronousSubmissions()`.
      // In the meanwhile, there can be other async operations that have completed, and that add themselves to the
      // queue before we process them or not, but we will find them in the next iteration. Note that it is ok to call
      // `processCompletedAsynchronousSubmissions()` if the completion queue is empty, since it will just return
      // immediately.
      batch = batch.filterNot(_._1.future.isCompleted)
      debug(s"awaitPending: filtering out completed asynchronous submissions from batch, ${batch.size} remaining")

      // There must be at least one completed `Future` to process, since the `Future` adds itself to the completion
      // queue before completing.
      containingDocument.maybeWithOutermostActionHandler(! skipDeferredEventHandling) {
        debug(s"awaitPending: processing completed asynchronous submissions")
        processCompletedAsynchronousSubmissions(containingDocument)
      }

      // Calling `processCompletedAsynchronousSubmissions()` might have caused additions to the pending list, so we
      // update our list. Those adds can only happen synchronously in this thread, so we can just prepend them to the
      // remaining batch.
      debug(s"awaitPending: getting new batch of pending asynchronous submissions after processing completed ones")
      batch = getAndClear() ::: batch

      expirationTimeOpt =
        (expirationTimeOpt, computeExpirationTime(batch.map(_._2))) match {
          case (_, None)                                                                             => None
          case (Some(expirationTime), Some(newExpirationTime)) if newExpirationTime > expirationTime => Some(newExpirationTime)
          case _                                                                                     => expirationTimeOpt
        }
    }
    debug(s"awaitPending: all pending asynchronous submissions completed")
  }

  // In the JVM environment we don't need to schedule an immediate poll event as we have the ability to process
  // already-completed submissions immediately, or to wait on those that require waiting. So we just schedule a
  // poll event with the regular delay.
  protected def addClientPollEventIfNeeded(containingDocument: XFormsContainingDocument, hasRequestPending: Boolean): Unit =
    if (hasPendingAsynchronousSubmissions)
      addClientPollEventIfNeeded(containingDocument, containingDocument.getSubmissionPollDelay)
}
