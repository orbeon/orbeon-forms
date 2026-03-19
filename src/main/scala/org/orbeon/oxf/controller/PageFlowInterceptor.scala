package org.orbeon.oxf.controller

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.pipeline.api.PipelineContext


sealed trait InterceptorResult
object InterceptorResult {

  sealed trait HandledOrNotHandled extends InterceptorResult

  case object Handled               extends InterceptorResult with HandledOrNotHandled
  case object NotHandled            extends InterceptorResult with HandledOrNotHandled
  case object ConnectionInterrupted extends InterceptorResult
}

trait PageFlowInterceptor {
  def process()(implicit pc: PipelineContext, ec: ExternalContext): InterceptorResult.HandledOrNotHandled
}
