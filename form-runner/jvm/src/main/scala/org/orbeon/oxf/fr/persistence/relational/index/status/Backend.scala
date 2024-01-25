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

// Functions called by the backend

object Backend {

  def reindexingProviders(
    providers     : List[String],
    indexProvider : String => Unit
  )(implicit
    indentedLogger: IndentedLogger
  ): Unit = {
    StatusStore.setStatus(Status.Starting(providers))
    providers
      .zipWithIndex
      .foreach { case (provider, index) =>
        if (StatusStore.getStatus != Status.Stopping) {
          StatusStore.setStatus(Status.Indexing(
            provider      = provider,
            providerCount = Count(index + 1, providers.length),
            documentCount = None
          ))
          indexProvider(provider)
        }
      }
    StatusStore.setStatus(Status.Stopped)
  }

  def setProviderDocumentTotal(total: Int)(implicit indentedLogger: IndentedLogger): Unit =
    setIndexing(i => Some(i.copy(documentCount = Some(Count(total = total, current = 0)))))

  def setProviderDocumentNext()(implicit indentedLogger: IndentedLogger): Unit =
    setDocumentCount(c => c.copy(current = c.current + 1))

  private def setIndexing(setter: Status.Indexing => Option[Status.Indexing])(implicit indentedLogger: IndentedLogger): Unit =
    Some(StatusStore.getStatus).collect { case status: Status.Indexing =>
      setter(status).foreach(StatusStore.setStatus)
    }

  private def setDocumentCount(setter: Count => Count)(implicit indentedLogger: IndentedLogger): Unit =
    setIndexing(indexing =>
      indexing.documentCount.map { dc =>
        indexing.copy(documentCount = Some(setter(dc)))
      }
    )
}
