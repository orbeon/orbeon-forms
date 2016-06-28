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

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils._
import org.orbeon.oxf.util.NetUtils

import scala.collection.JavaConverters._

// Functions dealing with the session

object StatusStore {

  private var currentStatus: Status = Stopped

  private def session =
    NetUtils.getExternalContext.getSession(true).getAttributesMap.asScala

  def getStatus: Status = currentStatus

  def setStatus(status: Status): Unit = {

    // Log status
    if (Logger.isDebugEnabled) {
      def log = Logger.logDebug("reindex status", _: String)
      status match {
        case Stopped ⇒ log("stopped")
        case Starting ⇒ log("starting")
        case Stopping ⇒ log("stopping")
        case Indexing(provider, providerCount, documentCount) ⇒
          val providerInfo = s"${provider.name} ${providerCount.current}/${providerCount.total}"
          val documentInfo = documentCount.map(dc ⇒ s"document ${dc.current}/${dc.total}").getOrElse("")
          log(s"indexing $providerInfo $documentInfo")
      }
    }

    currentStatus = status
  }
}
