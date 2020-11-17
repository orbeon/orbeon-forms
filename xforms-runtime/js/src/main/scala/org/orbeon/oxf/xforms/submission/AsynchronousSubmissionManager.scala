package org.orbeon.oxf.xforms.submission

import cats.Eval
import org.orbeon.oxf.xforms.XFormsContainingDocument


class AsynchronousSubmissionManager(val containingDocument: XFormsContainingDocument) {

  def addClientDelayEventIfNeeded(): Unit = ???

  def addAsynchronousSubmission(eval: Eval[SubmissionResult]): Unit = ???

  def hasPendingAsynchronousSubmissions: Boolean = ???

  def processAllAsynchronousSubmissionsForJoin(): Unit = ???

  def processCompletedAsynchronousSubmissions(): Unit = ???
}
