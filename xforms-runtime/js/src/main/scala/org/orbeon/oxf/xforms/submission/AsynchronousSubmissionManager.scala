package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms.XFormsContainingDocument

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


class AsynchronousSubmissionManager
  extends AsynchronousSubmissionManagerTrait {

  private var totalSubmittedCount = 0
  private var pendingCount = 0
  private val runningCount = new AtomicInteger(0)
  private val completionQueue = new ConcurrentLinkedQueue[(String, Try[SubmissionResult])]

  def addAsynchronousSubmission(submissionEffectiveId: String, future: Future[SubmissionResult], awaitInCurrentRequest: Boolean): Unit = {
    totalSubmittedCount += 1
    pendingCount += 1
    runningCount.incrementAndGet()
    future.onComplete { result =>
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
          "processed" -> processedCount.toString,
          "failed"    -> failedCount.toString,
          "pending"   -> pendingCount.toString,
          "running"   -> runningCount.toString,
        )
      )
    }
  }

  def awaitAllAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
    info(s"skipping `awaitAllAsynchronousSubmissions()` as it's not implemented in the JS environment")
  }

  def awaitAsynchronousSubmissionsForCurrentRequest(containingDocument: XFormsContainingDocument): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
    info(s"skipping `awaitAsynchronousSubmissionsForCurrentRequest()` as it's not implemented in the JS environment")
  }
}
