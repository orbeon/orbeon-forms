/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.rest

import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl


class CRUD
    extends ProcessorImpl
    with RequestResponse
    with Common
    with Read
    with CreateUpdateDelete
    with LockUnlock {

  override def start(pipelineContext: PipelineContext): Unit =
    try {

      val req = request

      httpRequest.getMethod match {
        case HttpMethod.GET    => get(req)
        case HttpMethod.PUT    => change(req, delete = false)
        case HttpMethod.DELETE => change(req, delete = true)
        case HttpMethod.LOCK   => lock(req)
        case HttpMethod.UNLOCK => unlock(req)
        case _                 => httpResponse.setStatus(405)
      }
    } catch {
      case e: HttpStatusCodeException =>
        httpResponse.setStatus(e.code)
    }
}