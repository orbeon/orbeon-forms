package org.orbeon.oxf.xforms.submission

import cats.Eval
import org.orbeon.oxf.xforms.XFormsContainingDocument


class AsynchronousSubmissionManager(val containingDocument: XFormsContainingDocument) {
  def addClientDelayEventIfNeeded(): Unit                           = throw new NotImplementedError("addClientDelayEventIfNeeded")
  def addAsynchronousSubmission(eval: Eval[SubmissionResult]): Unit = throw new NotImplementedError("addAsynchronousSubmission")
  def hasPendingAsynchronousSubmissions: Boolean                    = throw new NotImplementedError("hasPendingAsynchronousSubmissions")
  def processAllAsynchronousSubmissionsForJoin(): Unit              = throw new NotImplementedError("processAllAsynchronousSubmissionsForJoin")
  def processCompletedAsynchronousSubmissions(): Unit               = throw new NotImplementedError("processCompletedAsynchronousSubmissions")
}
