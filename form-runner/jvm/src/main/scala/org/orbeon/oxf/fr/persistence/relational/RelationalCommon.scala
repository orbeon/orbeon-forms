/**
  * Copyright (C) 2013 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr.persistence.relational

import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.search.adt.SearchVersion
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion}


object RelationalCommon {

  def joinColumns(cols: Seq[String], t1: String, t2: String): String =
    cols.map(c => s"$t1.$c = $t2.$c").mkString(" AND ")

  // Used by search only
  def getEffectiveFormVersionForSearchMaybeCallApi(
    appForm        : AppForm,
    incomingVersion: SearchVersion
  ): FormDefinitionVersion =
    incomingVersion match {
      case SearchVersion.Unspecified  => PersistenceMetadataSupport.readLatestVersion(appForm).map(FormDefinitionVersion.Specific).getOrElse(FormDefinitionVersion.Latest)
      case SearchVersion.All          => FormDefinitionVersion.Latest
      case SearchVersion.Specific(v)  => FormDefinitionVersion.Specific(v)
    }
}
