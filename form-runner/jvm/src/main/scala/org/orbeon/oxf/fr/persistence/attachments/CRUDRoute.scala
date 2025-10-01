/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.attachments

import org.orbeon.oxf.controller.NativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.http.{HttpMethod, HttpRanges, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}

import scala.util.{Failure, Success}


object CRUDRoute extends NativeRoute {

  private val logger = LoggerFactory.createLogger(CRUDRoute.getClass)

  def process()(implicit pc: PipelineContext, ec: ExternalContext): Unit = {

    implicit val httpRequest:  Request  = ec.getRequest
    implicit val httpResponse: Response = ec.getResponse
    implicit val indentedLogger: IndentedLogger = new IndentedLogger(logger)

    try {
      info(s"Attachments provider service: ${httpRequest.getMethod} ${httpRequest.getRequestPath}")

      val (provider, pathInformation) = PathInformation.providerAndPathInformation(httpRequest)

      HttpRanges(httpRequest) match {
        case Success(ranges) =>
          httpRequest.getMethod match {
            case HttpMethod.HEAD   => provider.head  (pathInformation, ranges)
            case HttpMethod.GET    => provider.get   (pathInformation, ranges)
            case HttpMethod.PUT    => provider.put   (pathInformation)
            case HttpMethod.DELETE => provider.delete(pathInformation)
            case _                 => httpResponse.setStatus(StatusCode.MethodNotAllowed)
          }

        case Failure(throwable) =>
          error(s"Error while processing request ${httpRequest.getRequestPath}", throwable)
          httpResponse.setStatus(StatusCode.BadRequest)
      }
    } catch {
      case e: HttpStatusCodeException =>
        httpResponse.setStatus(e.code)
    }
  }
}
