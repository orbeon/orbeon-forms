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

// Functions called by UI

object UI {

  def status : String = StatusStore.getStatus.name
  def stop() : Unit   = StatusStore.setStatus(Status.Stopping)

  def getProviderToken   = Some(StatusStore.getStatus).collect{case Status.Indexing(p, _, _) => p               }.getOrElse("")
  def getProviderCurrent = Some(StatusStore.getStatus).collect{case Status.Indexing(_, c, _) => c.current       }.getOrElse(0)
  def getProviderTotal   = Some(StatusStore.getStatus).collect{case Status.Indexing(_, c, _) => c.total         }.getOrElse(0)
  def getDocumentCurrent = Some(StatusStore.getStatus).collect{case Status.Indexing(_, _, Some(d)) => d.current }.getOrElse(0)
  def getDocumentTotal   = Some(StatusStore.getStatus).collect{case Status.Indexing(_, _, Some(d)) => d.total   }.getOrElse(0)

}

