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
package org.orbeon.oxf.fr.relational.crud

import org.orbeon.oxf.externalcontext.ExternalContextOps._
import org.orbeon.oxf.fr.relational._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.ScalaUtils._

trait RequestResponse {

    case class Request(app: String, form: String, filename: Option[String], version: Version, dataPart: Option[Request#DataPart]) {
        case class DataPart(isDraft: Boolean, documentId: String)
        def forForm = ! dataPart.isDefined
        def forData =   dataPart.isDefined
        def forAttachment = filename.isDefined
    }

    def tableName(request: Request): String =
        Seq(
            Some("orbeon_form"),
            request.forForm       option "_definition",
            request.forData       option "_data",
            request.forAttachment option "_attach"
        ).flatten.mkString


    def httpRequest = NetUtils.getExternalContext.getRequest
    def headerValue(name: String): Option[String] = httpRequest.getFirstHeader(name)
    def requestUsername : Option[String] = headerValue("orbeon-username")
    def requestGroupname: Option[String] = headerValue("orbeon-groupname")

    val CrudFormPath = "/fr/service/([^/]+)/crud-ng/([^/]+)/([^/]+)/form/([^/]+)".r
    def request: Request = {
        val CrudFormPath(_, app, form, _) = NetUtils.getExternalContext.getRequest.getRequestPath
        val version = {
            val documentId = headerValue("orbeon-for-document-id")
            documentId match {
                case Some(id) ⇒ ForDocument(id)
                case None ⇒
                    val versionHeader = headerValue("orbeon-form-definition-version")
                    versionHeader match {
                        case None ⇒ Latest
                        case Some("next") ⇒ Next
                        case Some(v) ⇒ Specific(Integer.parseInt(v))
                    }
            }
        }
        new Request(app, form, None, version, None)
    }

    def httpResponse = NetUtils.getExternalContext.getResponse

}
