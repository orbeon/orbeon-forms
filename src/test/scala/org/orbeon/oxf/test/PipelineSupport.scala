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
import org.orbeon.oxf.util.CoreCrossPlatformSupport

import scala.util.chaining.scalaUtilChainingOps


object PipelineSupport {

  val DefaultRequestUrl = "oxf:/org/orbeon/oxf/default-request.xml"

  def createPipelineContextAndExternalContextJava(): PipelineContext =
    createPipelineContextAndExternalContext()

  def destroyPipelineContextAndExternalContextJava(pipelineContext: PipelineContext): Unit = {
    if (pipelineContext ne null)
      pipelineContext.destroy(true)
    CoreCrossPlatformSupport.clearExternalContext()
  }

  def createPipelineContextAndExternalContext(
    requestURL       : String = DefaultRequestUrl,
    sessionCreated   : Session => Any = _ => (),
    sessionDestroyed : Session => Any = _ => ()
  ): PipelineContext = {
    val pipelineContext =
      new PipelineContext("PipelineSupport.createPipelineContextWithExternalContext()")
    val externalContext =
      createTestExternalContext(
        pipelineContext,
        requestURL,
        sessionCreated,
        sessionDestroyed
      )
    CoreCrossPlatformSupport.setExternalContext(externalContext)
    pipelineContext
  }

  def createTestExternalContext(
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
    )
    .tap(
      ec => ec.getSession(create = true) // make sure a session is available for downstream `SafeRequestContext`
    )

  def withPipelineContextAndTestExternalContext[T](
    sessionCreated  : Session => Any = _ => (),
    sessionDestroyed: Session => Any = _ => (),
    requestURL      : String = DefaultRequestUrl,
  )(
    body            : (PipelineContext, ExternalContext) => T
  ): T =
    InitUtils.withNewPipelineContext("withTestExternalContext()") { pipelineContext =>
      val externalContext =
        createTestExternalContext(
          pipelineContext,
          requestURL,
          sessionCreated,
          sessionDestroyed
        )
      CoreCrossPlatformSupport.withExternalContext(externalContext) {
        body(pipelineContext, externalContext)
      }
    }
}
