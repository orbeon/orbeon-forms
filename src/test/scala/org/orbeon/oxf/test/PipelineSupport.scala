/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.test

import org.orbeon.oxf.externalcontext.ExternalContext.Session
import org.orbeon.oxf.externalcontext.{ExternalContext, TestExternalContext}
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.util.CoreUtils._

object PipelineSupport {

  val DefaultRequestUrl = "oxf:/org/orbeon/oxf/default-request.xml"

  def createPipelineContextWithExternalContextJava(): PipelineContext =
    createPipelineContextWithExternalContext()

  def createPipelineContextWithExternalContext(
    requestURL       : String = DefaultRequestUrl,
    sessionCreated   : Session => Any = _ => (),
    sessionDestroyed : Session => Any = _ => ()
  ): PipelineContext =
    new PipelineContext |!> (setExternalContext(_, requestURL, sessionCreated, sessionDestroyed))

  def setExternalContext(
    pipelineContext  : PipelineContext,
    requestURL       : String = DefaultRequestUrl,
    sessionCreated   : Session => Any = _ => (),
    sessionDestroyed : Session => Any = _ => ()
  ): ExternalContext =
    new TestExternalContext(
        pipelineContext,
        ProcessorUtils.createDocumentFromURL(requestURL, null),
        sessionCreated,
        sessionDestroyed
      ) |!> (
        pipelineContext.setAttribute(
          PipelineContext.EXTERNAL_CONTEXT,
          _
        )
    )

  def withTestExternalContext[T](
    sessionCreated   : Session => Any = _ => (),
    sessionDestroyed : Session => Any = _ => ())(
    body             : ExternalContext => T
  ): T =
    InitUtils.withPipelineContext { pipelineContext =>
      body(
        setExternalContext(
          pipelineContext,
          PipelineSupport.DefaultRequestUrl,
          sessionCreated,
          sessionDestroyed
        )
      )
    }
}
