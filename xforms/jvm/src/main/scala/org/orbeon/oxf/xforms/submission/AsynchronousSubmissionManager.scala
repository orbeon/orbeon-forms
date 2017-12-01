/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.submission

import java.io.{Externalizable, ObjectInput, ObjectOutput}
import java.util.concurrent._
import javax.enterprise.concurrent.ManagedExecutorService
import javax.naming.{InitialContext, NamingException}

import org.orbeon.oxf.externalcontext.{AsyncRequest, LocalExternalContext}
import org.orbeon.oxf.pipeline.InitUtils._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{IndentedLogger, NetUtils}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.event.XFormsEvents

/**
  * Handle asynchronous submissions.
  *
  * The `CompletionService` is stored in the session, indexed by document UUID.
  *
  * See https://doc.orbeon.com/xforms/submission-asynchronous.html
  * See http://java.sun.com/j2se/1.5.0/docs/api/java/util/concurrent/ExecutorCompletionService.html
  */
class AsynchronousSubmissionManager(val containingDocument: XFormsContainingDocument) {

  import AsynchronousSubmissionManager._

  private implicit def indentedLogger: IndentedLogger =
    containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY)

  /**
    * Add a special delay event to the containing document if there are pending submissions.
    *
    * This should be called just before sending an Ajax response.
    */
  def addClientDelayEventIfNeeded(): Unit =
    if (hasPendingAsynchronousSubmissions)
      containingDocument.addDelayedEvent(
        eventName         = XFormsEvents.XXFORMS_POLL,
        targetEffectiveId = containingDocument.getEffectiveId,
        bubbles           = false,
        cancelable        = false,
        time              = System.currentTimeMillis + containingDocument.getSubmissionPollDelay,
        discardable       = true,
        showProgress      = false, // could get from submission, but default must be `false`
        allowDuplicates   = false  // no need for duplicates
      )

  def addAsynchronousSubmission(callable: Callable[SubmissionResult]): Unit = {
    val asynchronousSubmissionsOpt =
      findAsynchronousSubmissions(
        create = true,
        sessionKey(containingDocument)
      )

    // NOTE: If we want to re-enable foreground async submissions, we must:
    // - do a better detection: !(xf-submit-done/xf-submit-error listener) && replace="none"
    // - OR provide an explicit hint on xf:submission
    asynchronousSubmissionsOpt foreach {
      _.submit(
        {
          // Make sure this is created at the time `submit` is called
          // Should we use `AsyncExternalContext` here?
          val newExternalContext = {

              val currentExternalContext = NetUtils.getExternalContext

              new LocalExternalContext(
                currentExternalContext.getWebAppContext,
                new AsyncRequest(currentExternalContext.getRequest),
                currentExternalContext.getResponse
              )
            }

          () ⇒ withPipelineContext { pipelineContext ⇒
            pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, newExternalContext)
            callable.call()
          }
        }
      )
    }
  }

  def hasPendingAsynchronousSubmissions: Boolean = {

    val asynchronousSubmissionsOpt =
      findAsynchronousSubmissions(
        create = false,
        sessionKey(containingDocument)
      )

    asynchronousSubmissionsOpt exists (_.pendingCount > 0)
  }

  /**
    * Process all pending asynchronous submissions if any. If processing of a particular submission causes new
    * asynchronous submissions to be started, also wait for the completion of those.
    *
    * Submissions are processed in the order in which they are made available upon termination by the completion
    * service.
    */
  def processAllAsynchronousSubmissions(): Unit = {

    val asynchronousSubmissionOpt =
      findAsynchronousSubmissions(
        create = false,
        sessionKey(containingDocument)
      )

    asynchronousSubmissionOpt filter (_.pendingCount > 0) foreach { asynchronousSubmission ⇒

      withDebug("processing all background asynchronous submissions") {
        var processedCount = 0
        try {
          while (asynchronousSubmission.pendingCount > 0) {

            // Handle next completed task
            val result = asynchronousSubmission.take().get()

            // Process response by dispatching an event to the submission
            val submission =
              containingDocument.getObjectByEffectiveId(result.getSubmissionEffectiveId).asInstanceOf[XFormsModelSubmission]

            submission.doSubmitReplace(result)

            processedCount += 1
          }
        } finally
          debugResults(List("processed" → processedCount.toString))
      }
    }
  }

  /**
    * Process all completed asynchronous submissions if any. This method returns as soon as no completed submission is
    * available.
    *
    * Submissions are processed in the order in which they are made available upon termination by the completion
    * service.
    */
  def processCompletedAsynchronousSubmissions(): Unit = {

    val asynchronousSubmissionsOpt =
      findAsynchronousSubmissions(
        create = false,
        sessionKey(containingDocument)
      )

    asynchronousSubmissionsOpt filter (_.pendingCount > 0) foreach { asynchronousSubmissions ⇒
      withDebug("processing completed background asynchronous submissions") {
        var processedCount = 0
        try {
          var future = asynchronousSubmissions.poll()
          while (future.isDefined) {

            val result = future.get.get()

            val submission =
              containingDocument.getObjectByEffectiveId(result.getSubmissionEffectiveId).asInstanceOf[XFormsModelSubmission]

            submission.doSubmitReplace(result)

            processedCount += 1

            future = asynchronousSubmissions.poll()
          }
        } finally
          debugResults(
            List(
              "processed" → processedCount.toString,
              "pending"  → asynchronousSubmissions.pendingCount.toString
            )
          )
      }
    }
  }
}

private object AsynchronousSubmissionManager {

  val AsyncSubmissionsSessionKeyPrefix = "oxf.xforms.state.async-submissions."

  // Global thread pool if none provided by the app server
  private lazy val threadPool = Executors.newCachedThreadPool

  private def getExecutorService: ExecutorService =
    try {
      // If the app server gives us an `ExecutorService` (e.g. with WildFly), use it
      // (See §EE.5.21, page 146 of the Java EE 7 spec)
      InitialContext.doLookup[ManagedExecutorService]("java:comp/DefaultManagedExecutorService")
    } catch {
      case _: NamingException ⇒
        // If no `ExecutorService` is provided by the app server (e.g. with Tomcat), use our global thread pool
        threadPool
    }

  def sessionKey(doc: XFormsContainingDocument): String = AsyncSubmissionsSessionKeyPrefix + doc.getUUID

  def findAsynchronousSubmissions(create: Boolean, sessionKey: String): Option[AsynchronousSubmissions] = {

    val session = NetUtils.getExternalContext.getRequest.getSession(true)

    session.getAttribute(sessionKey) map (_.asInstanceOf[AsynchronousSubmissions]) orElse {
      if (create) {
        val asynchronousSubmissions = new AsynchronousSubmissions
        session.setAttribute(sessionKey, asynchronousSubmissions)
        Some(asynchronousSubmissions)
      } else
        None
    }
  }

  class AsynchronousSubmissions extends Externalizable {

    private val completionService = new ExecutorCompletionService[SubmissionResult](getExecutorService)

    private var _pendingCount = 0
    def pendingCount = _pendingCount

    def submit(task: () ⇒ SubmissionResult): Future[SubmissionResult] = {

      val future = completionService.submit(
        new Callable[SubmissionResult]() {
          def call() = task()
        }
      )

      _pendingCount += 1
      future
    }

    def poll(): Option[Future[SubmissionResult]] =
      Option(completionService.poll()) map { f ⇒
        _pendingCount -= 1
        f
      }

    def take(): Future[SubmissionResult] = {
      val f = completionService.take()
      _pendingCount -= 1
      f
    }

    // So that this can be stored in session that require `Serializable`
    def writeExternal(out: ObjectOutput) = ()
    def readExternal(in: ObjectInput)    = ()
  }

}

