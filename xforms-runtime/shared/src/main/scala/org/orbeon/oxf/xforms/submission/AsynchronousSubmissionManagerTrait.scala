package org.orbeon.oxf.xforms.submission

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.xforms.EventNames

import scala.concurrent.Future


trait AsynchronousSubmissionManagerTrait {

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

  def addAsynchronousSubmission(submissionEffectiveId: String, future: Future[SubmissionResult]): Unit
  def hasPendingAsynchronousSubmissions: Boolean
  def processAllAsynchronousSubmissionsForJoin(containingDocument: XFormsContainingDocument): Unit
  def processCompletedAsynchronousSubmissions(containingDocument: XFormsContainingDocument): Unit
}
