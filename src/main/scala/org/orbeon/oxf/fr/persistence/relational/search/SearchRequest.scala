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
package org.orbeon.oxf.fr.persistence.relational.search

import org.orbeon.oxf.util.NetUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.XML._

trait SearchRequest {

  val SearchPath = "/fr/service/([^/]+)/search/([^/]+)/([^/]+)".r

  def httpRequest = NetUtils.getExternalContext.getRequest

  def parseRequest(searchDocument: DocumentInfo): Request = {

    httpRequest.getRequestPath match {
      case SearchPath(provider, app, form) ⇒

        val searchElement = searchDocument.rootElement
        // Get <query> elements
        val queryEls = searchDocument.rootElement.child("query").toList

        Request(
          app        = app,
          form       = form,
          pageSize   = searchElement.firstChild("page-size")  .get.stringValue.toInt,
          pageNumber = searchElement.firstChild("page-number").get.stringValue.toInt,
          columns    = queryEls.tail.map(_.attValue("path"))
        )
    }
  }
}
