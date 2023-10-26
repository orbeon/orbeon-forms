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
package org.orbeon.oxf.fr.persistence.relational.distinctcontrolvalues

import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.permission.PermissionsAuthorization
import org.orbeon.oxf.fr.persistence.SearchVersion
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.distinctcontrolvalues.adt.DistinctControlValuesRequest
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.SimplePath._


trait DistinctControlValuesRequestParser { this: DistinctControlValuesProcessor =>

  private val DistinctControlValuesPath = "/fr/service/([^/]+)/distinct-control-values/([^/]+)/([^/]+)".r

  def parseRequest(document: DocumentInfo, version: SearchVersion): DistinctControlValuesRequest = {

    if (Logger.debugEnabled) {
      Logger.logDebug("distinct control values request", TransformerUtils.tinyTreeToString(document))
    }

    httpRequest.getRequestPath match {
      case DistinctControlValuesPath(providerName, app, form) =>

        DistinctControlValuesRequest(
          provider     = Provider.withName(providerName),
          appForm      = AppForm(app, form),
          version      = version,
          credentials  = PermissionsAuthorization.findCurrentCredentialsFromSession,
          controlPaths = document.rootElement.child("control").flatMap(_.attValueOpt("path")).toList
        )
    }
  }
}
