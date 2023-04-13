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

import enumeratum._
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.http.{Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{ContentTypes, NetUtils}

import java.sql.Connection
import scala.util.Try


trait LockUnlock extends RequestResponse {

  import Private._

  def lock(req: LockUnlockRequest): Unit = {
    import LeaseStatus._
    val timeout = readTimeoutFromHeader
    readLeaseStatus(req) { (connection, leaseStatus, dataPart, reqLockInfo) =>
      leaseStatus match {
        case DoesNotExist =>
          LockSql.createLease(connection, req.provider, dataPart, reqLockInfo.userAndGroup, timeout)
        case ExistsCanUse =>
          LockSql.updateLease(connection, req.provider, dataPart, reqLockInfo.userAndGroup, timeout)
        case ExistsCanNotUse(existingLease) =>
          issueLockedResponse(existingLease)
      }
    }
  }

  def unlock(req: LockUnlockRequest): Unit = {
    import LeaseStatus._
    readLeaseStatus(req) { (connection, leaseStatus, dataPart, _) =>
      leaseStatus match {
        case DoesNotExist =>
          // NOP, we're already good
        case ExistsCanUse =>
          LockSql.removeLease(connection, dataPart)
        case ExistsCanNotUse(existingLease) =>
          issueLockedResponse(existingLease)
      }
    }
  }

  private object Private {

    sealed trait LeaseStatus extends EnumEntry
    object LeaseStatus extends Enum[LeaseStatus] {
      val values = findValues
      case object ExistsCanUse                                  extends LeaseStatus
      case class  ExistsCanNotUse(existingLease: LockSql.Lease) extends LeaseStatus
      case object DoesNotExist                                  extends LeaseStatus
    }

    def issueLockedResponse(existingLease: LockSql.Lease): Unit = {
      httpResponse.setStatus(StatusCode.Locked)
      httpResponse.setHeader(Headers.ContentType, ContentTypes.XmlContentType)
      httpResponse.getOutputStream.pipe(useAndClose(_)(os =>
        LockInfo.serialize(LockInfo(existingLease.lockInfo.userAndGroup), os)
      ))
    }

    def readTimeoutFromHeader: Int = {
      // Header has the form `Timeout: Second-600` header
      val prefix            = Headers.TimeoutValuePrefix
      val request           = NetUtils.getExternalContext.getRequest
      val headerValue       = request.getFirstHeaderIgnoreCase(Headers.Timeout)
      val timeoutString     = headerValue.flatMap(hv => hv.startsWith(prefix).option(hv.substring(prefix.length)))
      val timeoutInt        = timeoutString.flatMap(ts => Try(ts.toInt).toOption)
      timeoutInt.getOrElse(throw HttpStatusCodeException(StatusCode.BadRequest))
    }

    def readLeaseStatus(
      req   : LockUnlockRequest)(
      thunk : (Connection, LeaseStatus, DataPart, LockInfo) => Unit
    ): Unit = {

      import LeaseStatus._
      val bodyInputStream = RequestGenerator.getRequestBody(PipelineContext.get) match {
        case Some(bodyURL) => NetUtils.uriToInputStream(bodyURL)
        case None          => NetUtils.getExternalContext.getRequest.getInputStream
      }

      val reqLockInfo = LockInfo.parse(bodyInputStream)
      RelationalUtils.withConnection { connection =>
        def callThunk(leaseStatus: LeaseStatus): Unit =
          thunk(connection, leaseStatus, req.dataPart, reqLockInfo)
        Provider.withLockedTable(connection, req.provider, "orbeon_form_data_lease", () =>
          LockSql.readLease(connection, req.provider, req.dataPart) match {
            case Some(lease) =>
              val canUseExistingLease =
                reqLockInfo.userAndGroup.username == lease.lockInfo.userAndGroup.username || lease.timeout <= 0
              if (canUseExistingLease)
                callThunk(ExistsCanUse)
              else
                callThunk(ExistsCanNotUse(lease))
            case None =>
              callThunk(DoesNotExist)
          }
        )
      }
    }
  }
}
