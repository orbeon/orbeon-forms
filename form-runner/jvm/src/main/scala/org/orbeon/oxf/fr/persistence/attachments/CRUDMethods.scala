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

import org.log4s.Logger
import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Response}
import org.orbeon.oxf.http.{HttpRange, HttpRanges, StatusCode}

import java.io.InputStream
import scala.util.{Failure, Success}


trait CRUDMethods {
  def head  (pathInformation: PathInformation, httpRanges: HttpRanges)(implicit httpRequest: Request, httpResponse: Response): Unit
  def get   (pathInformation: PathInformation, httpRanges: HttpRanges)(implicit httpRequest: Request, httpResponse: Response): Unit
  def put   (pathInformation: PathInformation                        )(implicit httpRequest: Request, httpResponse: Response): Unit
  def delete(pathInformation: PathInformation                        )(implicit httpRequest: Request, httpResponse: Response): Unit
}

object CRUDMethods {
  def get(
    httpRanges        : HttpRanges,
    length            : Long,
    partialInputStream: HttpRange => InputStream,
    fullInputStream   : => InputStream)(implicit
    httpRequest       : Request,
    httpResponse      : Response,
    logger            : Logger
  ): Unit =
    httpRanges.streamResponse(length, partialInputStream, fullInputStream) match {
      case Success(streamResponse) =>
        IOUtils.copyStreamAndClose(streamResponse.inputStream, httpResponse.getOutputStream)

        httpResponse.addHeaders(streamResponse.headers)
        httpResponse.setStatus(streamResponse.statusCode)

      case Failure(throwable) =>
        logger.error(throwable)(s"Error while processing request ${httpRequest.getRequestPath}")
        httpResponse.setStatus(StatusCode.InternalServerError)
    }
}
