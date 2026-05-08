package org.orbeon.oxf.fr.process

import scala.util.{Success, Try}


object SimpleProcess
  extends SimpleProcessCommon
     with FormRunnerActions {

  // NOTE: Clear the PDF/TIFF URLs *before* the process, because if we clear it after, it will be already cleared
  // during the second pass of a two-pass submission.
  override def beforeProcess(isContinuation: Boolean): Try[Any] =
    if (isContinuation)
      Success(())
    else
      clearRenderedFormatsResources()
}