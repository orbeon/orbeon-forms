package org.orbeon.oxf.xforms.submission

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.orbeon.connection.ConnectionResultT
import org.orbeon.oxf.util.CoreCrossPlatformSupport.executionContext
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.{debug, debugResults, error, withDebug}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.xforms.EventNames

import java.io.{ByteArrayInputStream, InputStream}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


// An instance of this class can become no longer referenced if the `XFCD` becomes passivated/serialized. If the
// `XFCD` is reactivated/deserialized, a new `AsynchronousSubmissionManager` will be recreated. Running futures can
// eventually complete, but their results will be ignored, and once all futures have completed, this instance will
// eventually be garbage-collected. This is not ideal, but it's the best we can do for now.
trait AsynchronousSubmissionManagerTrait {

  private var totalSubmittedCount = 0
  private var pendingCount = 0

  private val runningCount           = new AtomicInteger(0)
  private val completionQueue        = new ConcurrentLinkedQueue[(String, SubmissionParameters, Try[ConnectResult])]
  private var pendingList            = List.empty[Future[ConnectResult]]

  private val requestRunningCount    = new AtomicInteger(0)
  private var requestPendingList     = List.empty[(Future[ConnectResult], Duration)]

  def addClientDelayEventIfNeeded(containingDocument: XFormsContainingDocument): Unit =
    if (hasPendingAsynchronousSubmissions)
      containingDocument.addDelayedEvent(
        eventName         = EventNames.XXFormsPoll,
        targetEffectiveId = containingDocument.getEffectiveId,
        bubbles           = false,
        cancelable        = false,
        time              = System.currentTimeMillis + containingDocument.getSubmissionPollDelay,
        showProgress      = false, // could get from submission, but default must be `false`
        allowDuplicates   = false, // no need for duplicates
        properties        = Nil    // poll event doesn't need properties
      )

  def addAsynchronousSubmission(
    submissionEffectiveId: String,
    future               : Future[AsyncConnectResult],
    submissionParameters : SubmissionParameters,
    awaitInCurrentRequest: Option[Duration]
  ): Unit = {

    totalSubmittedCount += 1
    pendingCount += 1

    val inputStreamF =
      future.flatMap(convertConnectResult)

    val mustWait = awaitInCurrentRequest match {
      case Some(duration) if duration.gt(Duration.Zero) =>
        requestRunningCount.incrementAndGet()
        requestPendingList ::= inputStreamF -> duration
        true
      case None =>
        false
    }

    runningCount.incrementAndGet()
    pendingList ::= inputStreamF

    inputStreamF.onComplete { result =>
      if (mustWait)
        requestRunningCount.decrementAndGet()

      runningCount.decrementAndGet()
      completionQueue.add((submissionEffectiveId, submissionParameters, result))
    }
  }

  // Convert the `fs2.Stream` in the `ConnectResult` to an `InputStream`. Of course, We'd like to stream all the way
  // ideally, but this is a first step. We cannot use `fs2.io.toInputStream` because it requires running two threads,
  // which doesn't work in JavaScript. So we go through an in-memory `Array` for now. Note that sending data also
  // works with `Array`s. Also, note that we use `Future` as that's currently what's submitted to this manager.
  private def fs2StreamToInputStreamInMemory(s: fs2.Stream[IO, Byte]): Future[InputStream] =
    s.compile.to(Array).map(new ByteArrayInputStream(_)).unsafeToFuture()

  private def convertConnectResult(fs2Cr: AsyncConnectResult): Future[ConnectResult] =
    fs2Cr match {
      case ConnectResultT(_, Success(t @ (_, fs2Cxr @ ConnectionResultT(_, _, _, fs2Content, _, _)))) =>
        for(is <- fs2StreamToInputStreamInMemory(fs2Content.stream))
        yield
          fs2Cr.copy(
            result = Success(
              t.copy(
                _2 = fs2Cxr.copy(
                  content = fs2Content.copy(stream = is)
                )
              )
            )
          )
      case ConnectResultT(submissionEffectiveId, Failure(t)) =>
        Future.successful(ConnectResultT(submissionEffectiveId, Failure(t)))
    }

  def hasPendingAsynchronousSubmissions: Boolean = pendingCount > 0

  def processCompletedAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit = {

    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)

    withDebug("processing completed asynchronous submissions") {
      var processedCount = 0
      var failedCount = 0

      Iterator.continually(completionQueue.poll()).takeWhile(_ ne null).foreach {
        case (submissionEffectiveId, submissionParameters, connectResultTry) =>

          pendingCount -= 1

          debug(s"processing asynchronous submission `$submissionEffectiveId`")
          try {
            containingDocument
              .getObjectByEffectiveId(submissionEffectiveId).asInstanceOf[XFormsModelSubmission]
              .processAsyncSubmissionResponse(
                connectResultTry,
                submissionParameters
              )
            processedCount += 1
          } catch {
            case NonFatal(t) =>
              // This should be rare, as a failing submission will normally cause a `xforms-submit-error` event but not
              // an exception. We still want to log the exception here, as it's likely to be a bug.
              error(s"error processing asynchronous submission `$submissionEffectiveId`", t)
              failedCount += 1
              throw t
          }
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
  def awaitAllAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
    debug("awaiting all pending asynchronous submissions")
    awaitPending(containingDocument, () => pendingList.map(_ -> Duration.Inf), () => pendingList = Nil)
  }

  /**
   * Await all pending asynchronous submissions that have been specially marked and started in this current request.
   */
  def awaitAsynchronousSubmissionsForCurrentRequest(containingDocument: XFormsContainingDocument): Unit = {
    implicit val logger: IndentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)
    debug("awaiting all pending asynchronous submissions for current request")
    awaitPending(containingDocument, () => requestPendingList, () => requestPendingList = Nil)
  }

  protected def awaitPending(
    containingDocument: XFormsContainingDocument,
    get               : () => List[(Future[ConnectResult], Duration)],
    clear             : () => Unit
  )(implicit
    logger            : IndentedLogger
  ): Unit
}
