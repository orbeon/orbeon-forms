package org.orbeon.oxf.xforms.submission

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.orbeon.connection.AsyncConnectionResult
import org.orbeon.oxf.externalcontext.ExternalContext


object XFormsModelSubmissionSupport extends XFormsModelSubmissionSupportTrait {

  // Currently this blocks, but we should be looking into not blocking a thread.
  def runDeferredSubmissionForUpdate(computation: IO[AsyncConnectResult], response: ExternalContext.Response): Unit = {

    def forwardResultToResponseAsync(fs2Cxr: AsyncConnectionResult): IO[Unit] = {
      SubmissionUtils.forwardStatusContentTypeAndHeaders(fs2Cxr, response)
      fs2Cxr.content.stream.through(fs2.io.writeOutputStream(IO(response.getOutputStream))).compile.drain
    }

    def replace(t: (Replacer, AsyncConnectionResult)): IO[Unit] = t match {
      case (replacer, cxr)  =>
        replacer match {
          case AllReplacer      => forwardResultToResponseAsync(cxr)
          case RedirectReplacer => IO.pure(SubmissionUtils.forwardStatusAndHeaders(cxr, response))
          case NoneReplacer     => IO.pure(())
          case r                => throw new IllegalArgumentException(r.getClass.getName)
        }
      }

    computation
      .flatMap(fs2Cr => IO.fromTry(fs2Cr.result))
      .bracket(replace)(t => IO(t._2.close()))
      .unsafeRunSync()
  }
}
