package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.XFormsContainingDocument

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


// An instance of this class can become no longer referenced if the `XFCD` becomes passivated/serialized. If the
// `XFCD` is reactivated/deserialized, a new `AsynchronousSubmissionManager` will be recreated. Running futures can
// eventually complete, but their results will be ignored, and once all futures have completed, this instance will
// eventually be garbage-collected. This is not ideal, but it's the best we can do for now.
class AsynchronousSubmissionManager
  extends AsynchronousSubmissionManagerTrait {

  private var totalSubmittedCount = 0
  private var pendingCount = 0

  private val runningCount           = new AtomicInteger(0)
  private val completionQueue        = new ConcurrentLinkedQueue[(String, Try[SubmissionResult])]
  private var pendingList            = List.empty[Future[SubmissionResult]]

  private val requestRunningCount    = new AtomicInteger(0)
  private var requestPendingList     = List.empty[Future[SubmissionResult]]

  def addAsynchronousSubmission(submissionEffectiveId: String, future: Future[SubmissionResult], awaitInCurrentRequest: Boolean): Unit = {

    totalSubmittedCount += 1
    pendingCount += 1

    if (awaitInCurrentRequest) {
      requestRunningCount.incrementAndGet()
      requestPendingList ::= future
    }

    runningCount.incrementAndGet()
    pendingList ::= future

    future.onComplete { result =>
      if (awaitInCurrentRequest)
        requestRunningCount.decrementAndGet()
      runningCount.decrementAndGet()
      completionQueue.add(submissionEffectiveId -> result)
    }
  }

  def hasPendingAsynchronousSubmissions: Boolean = pendingCount > 0

  def processCompletedAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit = {

    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)

    withDebug("processing completed asynchronous submissions") {
      var processedCount = 0
      var failedCount = 0

      Iterator.continually(completionQueue.poll()).takeWhile(_ ne null).foreach {
        case (submissionEffectiveId, Success(result)) =>

          pendingCount -= 1

          debug(s"processing asynchronous submission `$submissionEffectiveId`")
          try {
            containingDocument
              .getObjectByEffectiveId(submissionEffectiveId).asInstanceOf[XFormsModelSubmission]
              .processAsyncSubmissionResponse(result._1, result._2)
            processedCount += 1
          } catch {
            case NonFatal(t) =>
              // This should be rare, as a failing submission will normally cause a `xforms-submit-error` event but not
              // an exception here. We still want to log the exception here, as it's likely to be a bug.
              error(s"error processing asynchronous submission `$submissionEffectiveId`", t)
              failedCount += 1
              throw t
          }

        case (submissionEffectiveId, Failure(t)) =>
          // This should be rare, because we already encapsulate the result in a `Try`. We still want to log the
          // exception here, as it's likely to be a bug.
          error(s"asynchronous submission has failed `Future` for `$submissionEffectiveId`", t)
          pendingCount -= 1
          failedCount += 1
      }

      debugResults(
        List(
          "processed"          -> processedCount.toString,
          "failed"             -> failedCount.toString,
          "total submitted"    -> totalSubmittedCount.toString,
          "pending"            -> pendingCount.toString,
          "running"            -> runningCount.toString,
          "running in request" -> requestRunningCount.toString,
        )
      )
    }
  }

  /**
    * Await all pending asynchronous submissions if any. If processing of a particular submission causes new
    * asynchronous submissions to be started, also wait for the completion of those.
    */
  def awaitAllAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit =
    awaitPending(containingDocument, () => pendingList, () => pendingList = Nil)

  /**
   * Await all pending asynchronous submissions that have been specially marked and started in this current request.
   */
  def awaitAsynchronousSubmissionsForCurrentRequest(containingDocument: XFormsContainingDocument): Unit =
    awaitPending(containingDocument, () => requestPendingList, () => requestPendingList = Nil)

  private def awaitPending(containingDocument: XFormsContainingDocument, get: () => List[Future[SubmissionResult]], clear: () => Unit): Unit =
    while (get().nonEmpty) {

      val batch = get()
      clear()

      // TODO: It would be good to process submissions as soon as one is ready to be processed.
      Await.result(Future.sequence(batch), Duration.Inf)
      processCompletedAsynchronousSubmissions(containingDocument)
    }
}
