package org.orbeon.oxf.xforms.submission

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.{debug, debugResults, error, withDebug}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.xforms.EventNames

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.control.NonFatal


// An instance of this class can become no longer referenced if the `XFCD` becomes passivated/serialized. If the
// `XFCD` is reactivated/deserialized, a new `AsynchronousSubmissionManager` will be recreated. Running futures can
// eventually complete, but their results will be ignored, and once all futures have completed, this instance will
// eventually be garbage-collected. This is not ideal, but it's the best we can do for now.
trait AsynchronousSubmissionManagerTrait {

  private var totalSubmittedCount = 0
  private var pendingCount        = 0

  private val runningCount           = new AtomicInteger(0)
  private val completionQueue        = new ConcurrentLinkedQueue[(String, Try[Any] => Any, Try[Any])]
  private var pendingList            = List.empty[Future[Any]]

  private var requestRunningCount         = new AtomicInteger(0)

  private var requestWithWaitRunningCount = new AtomicInteger(0)
  private var requestWithWaitPendingList  = List.empty[(Future[Any], Duration)]

  protected def addClientDelayEventIfNeeded(containingDocument: XFormsContainingDocument, delayMs: Int): Unit =
    containingDocument.addDelayedEvent(
      eventName         = EventNames.XXFormsPoll,
      targetEffectiveId = containingDocument.getEffectiveId,
      bubbles           = false,
      cancelable        = false,
      time              = System.currentTimeMillis + delayMs,
      showProgress      = false, // could get from submission, but default must be `false`
      allowDuplicates   = false, // no need for duplicates
      properties        = Nil    // poll event doesn't need properties
    )

  def hasPendingAsynchronousSubmissions: Boolean = pendingCount > 0

  def addAsynchronousCompletion[T, U](
    description          : String,
    computation          : IO[T],
    continuation         : Try[T] => U,
    awaitInCurrentRequest: Option[Duration]
  ): Unit = {

    val future = computation.unsafeToFuture()

    totalSubmittedCount += 1
    pendingCount += 1

    requestRunningCount.incrementAndGet()

    val mustWait = awaitInCurrentRequest match {
      case Some(duration) if duration.gt(Duration.Zero) =>
        requestWithWaitRunningCount.incrementAndGet()
        requestWithWaitPendingList ::= future -> duration
        true
      case None =>
        false
    }

    runningCount.incrementAndGet()
    pendingList ::= future

    // Copy references so they are captured by the closure below
    val localRequestRunningCount         = requestRunningCount
    val localRequestWithWaitRunningCount = requestWithWaitRunningCount

    future.onComplete { result =>
      // This runs asynchronously

      // Decrement through copied references so we don't impact future requests
      localRequestRunningCount.decrementAndGet()
      if (mustWait)
        localRequestWithWaitRunningCount.decrementAndGet()

      runningCount.decrementAndGet()
      completionQueue.add((description, continuation.asInstanceOf[Try[Any] => Any], result))
    }
  }

  def processCompletedAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit = {

    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)

    withDebug("processing completed asynchronous submissions") {
      var processedCount = 0
      var failedCount = 0

      Iterator.continually(completionQueue.poll()).takeWhile(_ ne null).foreach {
        case (description, continuation, resultTry) =>

          pendingCount -= 1

          debug(s"processing asynchronous result `$description`")
          try {
            continuation(resultTry)
            processedCount += 1
          } catch {
            case NonFatal(t) =>
              // This should be rare, as a failing submission will normally cause a `xforms-submit-error` event but not
              // an exception. We still want to log the exception here, as it's likely to be a bug.
              error(s"error processing asynchronous submission `$description`", t)
              failedCount += 1
              throw t
          }
      }

      debugResults(
        List(
          "processed"                  -> processedCount.toString,
          "failed"                     -> failedCount.toString,
          "total submitted"            -> totalSubmittedCount.toString,
          "pending"                    -> pendingCount.toString,
          "running"                    -> runningCount.toString,
          "running in request"         -> requestRunningCount.toString,
          "running in request waiting" -> requestWithWaitRunningCount.toString,
        )
      )
    }
  }

  /**
    * Await all pending asynchronous submissions if any. If processing of a particular submission causes new
    * asynchronous submissions to be started, also wait for the completion of those.
    */
  def awaitAllAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
    debug("awaiting all pending asynchronous submissions")
    awaitPending(
      containingDocument,
      skipDeferredEventHandling = true, // `xxf:join-submissions` already runs within a deferred action handling context
      () => {
        val r = pendingList.map(_ -> Duration.Inf)
        pendingList = Nil
        r
      })
  }

  /**
   * Await all pending asynchronous submissions that have been specially marked and started in this current request.
   */
  def awaitAsynchronousSubmissionsForCurrentRequestMaybeSubmitPollEvent(
    containingDocument       : XFormsContainingDocument,
    skipDeferredEventHandling: Boolean
  ): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
    debug("awaiting all pending asynchronous submissions for current request")
    awaitPending(
      containingDocument,
      skipDeferredEventHandling,
      () => {
        val r = requestWithWaitPendingList
        requestWithWaitPendingList = Nil
        r
      }
    )
    // Reset request information as there won't be any new updates
    val hasRequestPending = requestRunningCount.get() > 0

    requestRunningCount         = new AtomicInteger(0)
    requestWithWaitPendingList  = Nil
    requestWithWaitRunningCount = new AtomicInteger(0)

    addClientDelayEventIfNeeded(containingDocument, hasRequestPending)
  }

  protected def awaitPending(
    containingDocument       : XFormsContainingDocument,
    skipDeferredEventHandling: Boolean,
    getAndClear              : () => List[(Future[Any], Duration)]
  )(implicit
    logger                   : IndentedLogger
  ): Unit

  protected def addClientDelayEventIfNeeded(
    containingDocument: XFormsContainingDocument,
    hasRequestPending : Boolean
  ): Unit
}
