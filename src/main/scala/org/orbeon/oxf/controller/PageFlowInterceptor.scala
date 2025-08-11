package org.orbeon.oxf.controller

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult


trait PageFlowInterceptor {
  def matches(req: Request): Boolean
  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit
}
