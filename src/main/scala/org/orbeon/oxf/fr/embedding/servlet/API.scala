/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.embedding.servlet

import java.io.Writer
import javax.servlet.ServletContext
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.orbeon.oxf.fr.embedding.APISupport._
import org.orbeon.oxf.fr.embedding.Action

object API {

    // Embed an Orbeon Forms page by path
    def embedPageJava(
        servletCtx: ServletContext,
        req       : HttpServletRequest,
        writer    : Writer,
        path      : String
     ): Unit =
        withSettings(servletCtx, req, writer) { settings ⇒

            implicit val ctx = new ServletEmbeddingContextWithResponse(
                req,
                servletCtx.log,
                Left(writer),
                nextNamespace(req),
                settings.orbeonPrefix,
                settings.httpClient
            )

            proxyPage(settings.formRunnerURL, path)
        }

    // Embed a Form Runner form
    def embedFormJava(
        servletCtx: ServletContext,
        req       : HttpServletRequest,
        writer    : Writer,
        app       : String,
        form      : String,
        action    : String,
        documentId: String,
        query     : String
     ): Unit =
        embedPageJava(
            servletCtx,
            req,
            writer,
            formRunnerPath(app, form, action, Option(documentId), Option(query))
        )

    // Embed an Orbeon Forms page by path
    def embedPage(
        servletCtx: ServletContext,
        req       : HttpServletRequest,
        out       : Writer Either HttpServletResponse,
        path      : String
     ): Unit =
        withSettings(servletCtx, req, out.fold(identity, _.getWriter)) { settings ⇒

            implicit val ctx = new ServletEmbeddingContextWithResponse(
                req,
                servletCtx.log,
                out,
                nextNamespace(req),
                settings.orbeonPrefix,
                settings.httpClient
            )

            proxyPage(settings.formRunnerURL, path)
        }

    // Embed a Form Runner form
    def embedForm(
        servletCtx: ServletContext,
        req       : HttpServletRequest,
        out       : Writer Either HttpServletResponse,
        app       : String,
        form      : String,
        action    : Action,
        documentId: Option[String],
        query     : Option[String]
    ): Unit =
        embedPage(
            servletCtx,
            req,
            out,
            formRunnerPath(app, form, action.name, documentId, query)
        )
}
