package org.orbeon.oxf.controller

import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.pipeline.api.PipelineContext


trait PageFlowInterceptor {
  def process()(implicit pc: PipelineContext, ec: ExternalContext): Boolean
}
