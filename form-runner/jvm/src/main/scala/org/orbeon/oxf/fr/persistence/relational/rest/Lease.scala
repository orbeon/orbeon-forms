/**
 * Copyright (C) 2017 Orbeon, Inc.
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

import org.orbeon.oxf.fr.permission.PermissionsAuthorization
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.http.{Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.IOUtils.useAndClose
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.NetUtils

trait Lease extends RequestResponse {

  def lock(req: Request): Unit = {

    val bodyInputStream = RequestGenerator.getRequestBody(PipelineContext.get) match {
      case bodyURL: String ⇒ NetUtils.uriToInputStream(bodyURL)
      case _               ⇒ NetUtils.getExternalContext.getRequest.getInputStream
    }

    //new RuntimeException().printStackTrace()

    val reqLockInfo = LockInfo.parse(bodyInputStream)
    req.dataPart match {
      case None ⇒ throw HttpStatusCodeException(StatusCode.BadRequest)
      case Some(dataPart) ⇒
        RelationalUtils.withConnection { connection ⇒
          LockSql.readLease(connection, dataPart) match {
            case Some(lease) ⇒
              val canUseExistingLease =
                reqLockInfo.username == lease.username || lease.expired
              if (canUseExistingLease)
                LockSql.updateLease(connection, dataPart, reqLockInfo.username, reqLockInfo.groupname)
              else {
                httpResponse.setStatus(423)
                httpResponse.setHeader(Headers.ContentType, "application/xml")
                httpResponse.getOutputStream.pipe(useAndClose(_)(os ⇒
                  LockInfo.serialize(LockInfo(lease.username, lease.groupname), os)
                ))
              }
            case None ⇒
              LockSql.createLease(connection, dataPart, reqLockInfo.username, reqLockInfo.groupname)
          }
        }
    }
  }

  def unlock(req: Request): Unit = {
    ???
  }

}
