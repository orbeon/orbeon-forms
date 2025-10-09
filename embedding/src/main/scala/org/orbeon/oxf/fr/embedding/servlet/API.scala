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

import org.orbeon.fr.FormRunnerPath
import org.orbeon.oxf.fr.embedding.APISupport.*
import org.orbeon.oxf.servlet.HttpServletRequest

import java.io.{FilterWriter, Writer}
import java.util as ju
import scala.jdk.CollectionConverters.*

object API {

  // Embed an Orbeon Forms page by path
  def embedPageJava(
    req        : AnyRef, // javax/jakarta.servlet.http.HttpServletRequest
    writer     : Writer,
    path       : String,
    headers    : ju.Map[String, String]
   ): Unit = {
    val httpServletRequest = HttpServletRequest.fromAnyRef(req)

    // https://github.com/orbeon/orbeon-forms/issues/7194
    // There is code downstream that calls `useAndClose()`. We could track down such uses and avoid them, but it seems
    // safer to make sure that the incoming `Writer` is not closed no matter what.
    val neverClosingWriter =
      new FilterWriter(writer) {
        override def close(): Unit = ()
      }

    withSettings(httpServletRequest, neverClosingWriter) { settings =>

      implicit val ctx = new ServletEmbeddingContextWithResponse(
        httpServletRequest,
        Left(neverClosingWriter),
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
      FormRunnerPath.formRunnerPath(app, form, mode, Option(documentId), Option(query)),
      headers
    )
}
