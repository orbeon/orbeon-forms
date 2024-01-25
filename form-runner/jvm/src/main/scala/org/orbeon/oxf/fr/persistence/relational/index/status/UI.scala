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
package org.orbeon.oxf.fr.persistence.relational.index.status

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.util.DateUtils


// Functions called by UI
object UI {

  //@XPathFunction
  def status : String = {
    RelationalUtils.Logger.debug(s"Reindex status read from UI: ${StatusStore.getStatus.name}")
    val lastModified = DateUtils.formatIsoDateTimeUtc(StatusStore.getLastModified.getTime)
    val statusName   = StatusStore.getStatus.name
    s"$lastModified $statusName"
  }

  //@XPathFunction
  def stop() : Unit   = StatusStore.setStatus(Status.Stopping)(RelationalUtils.newIndentedLogger)

  //@XPathFunction
  def getProviderToken  : String = Some(StatusStore.getStatus).collect{case Status.Indexing(p, _, _) => p               }.getOrElse("")
  //@XPathFunction
  def getProviderCurrent: Int    = Some(StatusStore.getStatus).collect{case Status.Indexing(_, c, _) => c.current       }.getOrElse(0)
  //@XPathFunction
  def getProviderTotal  : Int    = Some(StatusStore.getStatus).collect{case Status.Indexing(_, c, _) => c.total         }.getOrElse(0)
  //@XPathFunction
  def getDocumentCurrent: Int    = Some(StatusStore.getStatus).collect{case Status.Indexing(_, _, Some(d)) => d.current }.getOrElse(0)
  //@XPathFunction
  def getDocumentTotal  : Int    = Some(StatusStore.getStatus).collect{case Status.Indexing(_, _, Some(d)) => d.total   }.getOrElse(0)
}

