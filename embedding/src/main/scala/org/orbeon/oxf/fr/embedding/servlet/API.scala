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
import java.{util => ju}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import org.orbeon.oxf.fr.embedding.APISupport._
import org.orbeon.oxf.fr.embedding.FormRunnerMode

import scala.jdk.CollectionConverters._
import scala.collection.immutable.Seq
import scala.collection.compat._

object API {

  // Embed an Orbeon Forms page by path
  def embedPageJava(
    req        : HttpServletRequest,
    writer     : Writer,
    path       : String,
    headers    : ju.Map[String, String]
   ): Unit =
    withSettings(req, writer) { settings =>

      implicit val ctx = new ServletEmbeddingContextWithResponse(
        req,
        Left(writer),
        nextNamespace(req),
        settings.orbeonPrefix,
        settings.httpClient
      )

      proxyPage(
        settings.formRunnerURL,
        path,
        Option(headers) map (_.asScala.to(List)) getOrElse Nil
      )
    }

  // Embed a Form Runner form
  def embedFormJava(
    req        : HttpServletRequest,
    writer     : Writer,
    app        : String,
    form       : String,
    mode       : String,
    documentId : String,
    query      : String,
    headers    : ju.Map[String, String]
   ): Unit =
    embedPageJava(
      req,
      writer,
      formRunnerPath(app, form, mode, Option(documentId), Option(query)),
      headers
    )

  // Embed an Orbeon Forms page by path
  def embedPage(
    req        : HttpServletRequest,
    out        : Writer Either HttpServletResponse,
    path       : String,
    headers    : Seq[(String, String)] = Nil
   ): Unit =
    withSettings(req, out.fold(identity, _.getWriter)) { settings =>

      implicit val ctx = new ServletEmbeddingContextWithResponse(
        req,
        out,
        nextNamespace(req),
        settings.orbeonPrefix,
        settings.httpClient
      )

      proxyPage(settings.formRunnerURL, path, headers)
    }

  // Embed a Form Runner form
  def embedForm(
    req        : HttpServletRequest,
    out        : Writer Either HttpServletResponse,
    app        : String,
    form       : String,
    mode       : FormRunnerMode,
    documentId : Option[String] = None,
    query      : Option[String] = None,
    headers    : Seq[(String, String)] = Nil
  ): Unit =
    embedPage(
      req,
      out,
      formRunnerPath(app, form, mode.entryName, documentId, query),
      headers
    )
}
