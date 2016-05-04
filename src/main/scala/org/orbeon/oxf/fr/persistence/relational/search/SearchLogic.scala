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

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils

trait SearchLogic {

  def doSearch(request: Request): Result =  {

    RelationalUtils.withConnection { connection ⇒

      val resultSet = {
        val ps = connection.prepareStatement(
          s"""|  SELECT c.document_id,
              |         c.created,
              |         c.last_modified_time,
              |         c.last_modified_by,
              |         c.username,
              |         c.groupname,
              |         t.control,
              |         t.pos,
              |         t.val
              |    FROM orbeon_i_current c,
              |         orbeon_i_control_text t
              |   WHERE app       = 'orbeon'  AND
              |         form      = 'builder' AND
              |         c.data_id = t.data_id
              |""".stripMargin)
        ps.executeQuery()
      }

      val resultSetRows =
        Iterator.iterateWhile(
          cond = resultSet.next(),
          elem =
              DocumentMetaData(
              documentId       = resultSet.getString    ("document_id"),
              created          = resultSet.getTimestamp ("created"),
              lastModifiedTime = resultSet.getTimestamp ("last_modified_time"),
              lastModifiedBy   = resultSet.getString    ("last_modified_by"),
              username         = resultSet.getString    ("username"),
              groupname        = resultSet.getString    ("groupname")
            ) →
            DocumentValue(
              control          = resultSet.getString    ("control"),
              pos              = resultSet.getInt       ("pos"),
              value            = resultSet.getString    ("val")
            )
          ).toList

      resultSetRows
        // Group row by common metadata, since the metadata is repeated in the result set
        .groupBy(_._1).mapValues(_.map(_._2)).toList
        // Sort by last modified with the most recent documents first
        .sortBy(- _._1.lastModifiedTime.getTime)

    }
  }

}
