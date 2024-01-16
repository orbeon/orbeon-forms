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

import org.orbeon.oxf.fr.embedding.APISupport._
import org.orbeon.oxf.servlet.HttpServletRequest

import java.io.Writer
import java.{util => ju}
import scala.collection.compat._
import scala.jdk.CollectionConverters._

object API {

  // Embed an Orbeon Forms page by path
  def embedPageJava(
    req        : AnyRef, // javax/jakarta.servlet.http.HttpServletRequest
    writer     : Writer,
    path       : String,
    headers    : ju.Map[String, String]
   ): Unit = {
    val httpServletRequest = HttpServletRequest.fromAnyRef(req)

    withSettings(httpServletRequest, writer) { settings =>

      implicit val ctx = new ServletEmbeddingContextWithResponse(
        httpServletRequest,
        Left(writer),
        nextNamespace(httpServletRequest),
        settings.orbeonPrefix,
        settings.httpClient
      )

      proxyPage(
        baseURL = settings.formRunnerURL,
        path    = path,
        headers = Option(headers) map (_.asScala.to(List)) getOrElse Nil,
        params  = Nil
      )
    }
  }

  // Embed a Form Runner form
  def embedFormJava(
    req        : AnyRef, // javax/jakarta.servlet.http.HttpServletRequest
    writer     : Writer,
    app        : String,
    form       : String,
    //formVersion      : Int, TODO: We can't add this here for backward compatibility reasons, but `FormRunnerOffline` supports it.
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
}
