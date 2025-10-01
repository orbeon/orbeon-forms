/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor

import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{SessionExpiredException, StatusCode}
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInputOutputInfo, ProcessorOutput}
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.event.ClientEvents
import org.orbeon.oxf.xml.*
import org.orbeon.xforms.*
import org.orbeon.xforms.route.XFormsServerRoute

import scala.util.control.NonFatal


/**
  * The XForms Server processor handles client requests, including events, and either returns an XML
  * response, or returns a response through the `ExternalContext`.
  */
private object XFormsServerProcessor {
  private val InputRequest = "request"
}

class XFormsServerProcessor extends ProcessorImpl {

  self =>

  addInputInfo(new ProcessorInputOutputInfo(XFormsServerProcessor.InputRequest))

  // Case where an XML response must be generated.
  override def createOutput(outputName: String): ProcessorOutput = {
    val output = new ProcessorOutputImpl(self, outputName) {
      override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
        implicit val pc: PipelineContext = pipelineContext
        implicit val ec: ExternalContext = XFormsCrossPlatformSupport.externalContext
        try {
          XFormsServerRoute.doIt(
            requestDocument = readInputAsOrbeonDom(pipelineContext, XFormsServerProcessor.InputRequest),
            xmlReceiverOpt  = Some(xmlReceiver)
          )
        } catch {
          case e: SessionExpiredException =>
            LifecycleLogger.eventAssumingRequest("xforms", e.message, Nil)
            // Don't log whole exception
            Loggers.logger.info(e.message)
            ClientEvents.errorDocument(e.message, e.code)(xmlReceiver, ec)
          case NonFatal(t) =>
            Loggers.logger.error(OrbeonFormatter.format(t))
            ClientEvents.errorDocument(OrbeonFormatter.message(t), StatusCode.InternalServerError)(xmlReceiver, ec)
        }
      }
    }
    addOutput(outputName, output)
    output
  }

  // Case where the response is generated through the `ExternalContext` (submission with `replace="all"`).
  override def start(pipelineContext: PipelineContext): Unit = {

    implicit val pc: PipelineContext = pipelineContext
    implicit val ec: ExternalContext = XFormsCrossPlatformSupport.externalContext

    XFormsServerRoute.doIt(
      requestDocument = readInputAsOrbeonDom(pipelineContext, XFormsServerProcessor.InputRequest),
      xmlReceiverOpt  = None
    )
  }
}
