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

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging._

import java.util.Date


// Functions dealing with the session
object StatusStore {

  private var lastModified: Date = new Date()
  private var currentStatus: Status = Status.Stopped

  def getLastModified : Date   = lastModified
  def getStatus       : Status = currentStatus

  def setStatus(status: Status)(implicit indentedLogger: IndentedLogger): Unit = {

    // Log status
    ifDebug {
      def liftLog(log: String => Unit): String => Unit = m => log(s"Reindex status: $m")
      def logInfo  = liftLog(info(_))
      def logDebug = liftLog(debug(_))
      status match {
        case Status.Stopped                         => logInfo("Stopped")
        case Status.Starting(providers)             => logInfo(s"Starting, will index ${providers.mkString("[", ", ", "]")}")
        case Status.Stopping                        => logInfo("Stopping")
        case Status.Indexing(provider, providerCount, maybeDocumentCount) =>
          def providerInfo = s"$provider ${providerCount.current}/${providerCount.total}"
          maybeDocumentCount match {
            case None                        => logInfo (s"Indexing $providerInfo")
            case Some(dc) if dc.current == 0 => logInfo (s"Indexing $providerInfo, ${dc.total} documents")
            case Some(dc) if dc.current != 0 => logDebug(s"Indexing $providerInfo, document ${dc.current}/${dc.total}")
          }
      }
    }

    lastModified  = new Date()
    currentStatus = status
  }
}
