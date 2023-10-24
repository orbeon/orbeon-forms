package org.orbeon.oxf.xforms.submission

import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.ExternalContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}


object XFormsModelSubmissionSupport extends XFormsModelSubmissionSupportTrait {

  // Currently this blocks, but we should be looking into not blocking a thread.
  def runDeferredSubmission(future: Future[ConnectResult], response: ExternalContext.Response): Unit =
    Await.result(future, Duration.Inf).result match {
      case Success((replacer, cxr)) =>
        useAndClose(cxr) { _ =>
          replacer match {
            case AllReplacer      => AllReplacer.forwardResultToResponse(cxr, response)
            case RedirectReplacer => RedirectReplacer.updateResponse(cxr, response)
            case NoneReplacer     => ()
            case r                => throw new IllegalArgumentException(r.getClass.getName)
          }
        }
      case Failure(throwable) =>
        // Propagate throwable, which might have come from a separate thread
        throw throwable
    }
}
