/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.distinctvalues

import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.permission.PermissionsAuthorization
import org.orbeon.oxf.fr.persistence.SearchVersion
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.distinctvalues.adt.DistinctValuesRequest
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SimplePath._


trait DistinctValuesRequestParser {

  this: DistinctValuesProcessor =>

  private val DistinctValuesPath = "/fr/service/([^/]+)/distinct-values/([^/]+)/([^/]+)".r

  def parseRequest(
    document      : DocumentInfo,
    version       : SearchVersion
  )(implicit
    indentedLogger: IndentedLogger
  ): DistinctValuesRequest = {

    debug(s"distinct values request:\n${TransformerUtils.tinyTreeToString(document)}")

    httpRequest.getRequestPath match {
      case DistinctValuesPath(providerName, app, form) =>

        val rootElement = document.rootElement

        DistinctValuesRequest(
          provider              = Provider.withName(providerName),
          appForm               = AppForm(app, form),
          version               = version,
          credentials           = PermissionsAuthorization.findCurrentCredentialsFromSession,
          anyOfOperations       = None,
          isInternalAdminUser   = false,
          controlPaths          = rootElement.rootElement.child("control").flatMap(_.attValueOpt("path")).toList,
          includeCreatedBy      = rootElement.rootElement.attValue("include-created-by"      ) == "true",
          includeLastModifiedBy = rootElement.rootElement.attValue("include-last-modified-by") == "true",
          includeWorkflowStage  = rootElement.rootElement.attValue("include-workflow-stage"  ) == "true"
        )
    }
  }
}
