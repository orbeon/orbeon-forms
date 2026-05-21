/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fb.mcp

import io.circe.parser
import org.orbeon.oxf.controller.NativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.*

import java.nio.charset.StandardCharsets
import scala.annotation.unused


@unused
object FormBuilderMcpRoute extends NativeRoute {

  private val Sessions  = new FormBuilderMcpSessionStore
  private val Editor    = new FormBuilderEditor(Sessions)
  private val Registry  = FormBuilderMcpTools.registry(Editor)
  private val McpServer = new FormBuilderMcpServer(Registry)

  def process()(implicit pc: PipelineContext, ec: ExternalContext): Unit = {
    Sessions.cleanupExpiredSessions()

    val requestJson =
      parser.parse(new String(NetUtils.inputStreamToByteArray(ec.getRequest.getInputStream), StandardCharsets.UTF_8))
        .fold(_ => throw HttpStatusCodeException(StatusCode.BadRequest), identity)

    McpServer.handleJsonRpc(requestJson) match {
      case Some(response) =>
        val bytes = response.noSpaces.getBytes(StandardCharsets.UTF_8)
        ec.getResponse.setContentType(ContentTypes.JsonContentType)
        ec.getResponse.setContentLength(bytes.length)
        ec.getResponse.getOutputStream.write(bytes)
      case None =>
        ec.getResponse.setStatus(202)
    }
  }
}
