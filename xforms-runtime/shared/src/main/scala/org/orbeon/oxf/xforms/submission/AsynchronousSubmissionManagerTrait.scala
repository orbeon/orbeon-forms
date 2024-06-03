package org.orbeon.oxf.xforms.submission

import cats.effect.IO
import org.orbeon.oxf.util.CoreCrossPlatformSupport.{executionContext, runtime}
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.{debug, debugResults, error, withDebug}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.xforms.EventNames

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
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
  private var pendingList            = List.empty[PendingCompletion]

  private var requestRunningCount         = new AtomicInteger(0)
  private var requestWithWaitRunningCount = new AtomicInteger(0)
  private var requestWithWaitPendingList  = List.empty[(PendingCompletion, Duration)]

  private val completionQueue = new ConcurrentLinkedQueue[CompletedCompletion]

  protected case class PendingCompletion(
    sequence    : Int,
    description : String,
    future      : Future[Any]
  )

  private case class CompletedCompletion(
    description : String,
    continuation: Try[Any] => Either[Try[Any], Future[Any]],
    promise     : Promise[Any],
    result      : Try[Any],
    sequence    : Int
  )

  protected def addClientPollEventIfNeeded(containingDocument: XFormsContainingDocument, delayMs: Int): Unit =
    containingDocument.addDelayedEvent(
      eventName         = EventNames.XXFormsPoll,
      targetEffectiveId = containingDocument.effectiveId,
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
    continuation         : Try[T] => Either[Try[U], Future[U]],
    awaitInCurrentRequest: Option[Duration]
  ): Future[U] = {

    totalSubmittedCount += 1
    pendingCount += 1

    requestRunningCount.incrementAndGet()

    val mustWaitDurationOpt = awaitInCurrentRequest.collect {
      case duration if duration.gt(Duration.Zero) =>
        requestWithWaitRunningCount.incrementAndGet()
        duration
    }

    runningCount.incrementAndGet()

    // Copy references so they are captured by the closure below
    val localRequestRunningCount         = requestRunningCount
    val localRequestWithWaitRunningCount = requestWithWaitRunningCount

    val p = Promise[U]()

    // This runs asynchronously after the computation completes
    def preProcessFutureCompletion(sequence: Int)(result: Try[T]): Try[T] = {

      // Decrement through copied references so we don't impact future requests
      localRequestRunningCount.decrementAndGet()
      if (mustWaitDurationOpt.isDefined)
        localRequestWithWaitRunningCount.decrementAndGet()

      runningCount.decrementAndGet()
      completionQueue.add(
        CompletedCompletion(
          description,
          continuation.asInstanceOf[Try[Any] => Either[Try[Any], Future[Any]]],
          p.asInstanceOf[Promise[Any]],
          result,
          sequence
        )
      )

      result
    }

    val newPendingCompletion =
      PendingCompletion(
        totalSubmittedCount,
        description,
        computation.unsafeToFuture().transform(preProcessFutureCompletion(totalSubmittedCount)) // this actually schedules the computation
      )

    pendingList ::= newPendingCompletion

    mustWaitDurationOpt.foreach { mustWaitDuration =>
      requestWithWaitPendingList ::= (newPendingCompletion, mustWaitDuration)
    }

    p.future
  }

  def processCompletedAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit = {

    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LoggingCategory)

    withDebug("processing completed asynchronous submissions") {
      var processedCount = 0
      var failedCount = 0

      Iterator.continually(completionQueue.poll()).takeWhile(_ ne null).foreach {
        case CompletedCompletion(description, continuation, callerPromise, resultTry, sequence) =>

          pendingCount -= 1
          pendingList = pendingList.filterNot(_.sequence == sequence)

          debug(s"processing asynchronous result `$description`")
          try {
            continuation(resultTry) match {
              case Left(t) =>
                callerPromise.complete(t)
              case Right(future) =>
                future.onComplete(v => callerPromise.complete(v))
            }

            processedCount += 1
          } catch {
            case NonFatal(t) =>
              // This should be rare, as a failing submission will normally cause a `xforms-submit-error` event but not
              // an exception. We still want to log the exception here, as it's likely to be a bug.
              error(s"error processing asynchronous submission `$description`", t)
              failedCount += 1
              // Fail the caller promise as well instead of throwing here
              callerPromise.failure(t)
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
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LoggingCategory)
    debug("awaiting all pending asynchronous submissions")
    awaitPending(
      containingDocument,
      skipDeferredEventHandling = true, // `xxf:join-submissions` already runs within a deferred action handling context
      () => {
        val r = pendingList.map(p => (p, Duration.Inf))
        pendingList = Nil
        r
      }
    )
  }

  /**
   * Await all pending asynchronous submissions that have been specially marked and started in this current request.
   */
  def awaitAsynchronousSubmissionsForCurrentRequestMaybeSubmitPollEvent(
    containingDocument       : XFormsContainingDocument,
    skipDeferredEventHandling: Boolean
  ): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LoggingCategory)

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

    addClientPollEventIfNeeded(containingDocument, hasRequestPending)
  }

  protected def awaitPending(
    containingDocument       : XFormsContainingDocument,
    skipDeferredEventHandling: Boolean,
    getAndClear              : () => List[(PendingCompletion, Duration)]
  )(implicit
    logger                   : IndentedLogger
  ): Unit

  protected def addClientPollEventIfNeeded(
    containingDocument: XFormsContainingDocument,
    hasRequestPending : Boolean
  ): Unit
}
