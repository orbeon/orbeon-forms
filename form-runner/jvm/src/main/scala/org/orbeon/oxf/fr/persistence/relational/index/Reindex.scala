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
package org.orbeon.oxf.fr.persistence.relational.index

import java.sql.{Connection, PreparedStatement}

import org.orbeon.io.IOUtils._
import org.orbeon.oxf.fr.persistence.relational.Provider.MySQL
import org.orbeon.oxf.fr.persistence.relational.index.status.{Backend, Status, StatusStore}
import org.orbeon.oxf.fr.persistence.relational.{Provider, RelationalUtils}
import org.orbeon.oxf.fr.{FormRunner, FormRunnerPersistence}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xml.NamespaceMapping

trait Reindex extends FormDefinition {

  sealed trait WhatToReindex

  object WhatToReindex {
    case object  AllData                                              extends WhatToReindex
    case class   DataForDocumentId(documentId: String)                extends WhatToReindex
    case class   DataForForm(app: String, form: String, version: Int) extends WhatToReindex
  }

  import WhatToReindex._

  // Reindexing is a 3 step process:
  //   1. Clean the index
  //   2. Get the documents to index
  //   3. For each document:
  //      - add 1 row to orbeon_i_current
  //      - add as many as necessary to orbeon_i_control_text
  def reindex(
    provider      : Provider,
    connection    : Connection,
    whatToReindex : WhatToReindex
  ): Unit = {

    // If a document id was provided, produce WHERE clause, and set parameter
    val (whereConditions, paramSetter) =
      whatToReindex match {
        case AllData => (
          Nil,
          (ps: PreparedStatement) => Unit
        )
        case DataForDocumentId(id) => (
          List("document_id = ?"),
          (ps: PreparedStatement) => ps.setString(1, id)
        )
        case DataForForm(app, form, version) => (
          List(
            "app = ?",
            "form = ?",
            "form_version = ?"
          ),
          (ps: PreparedStatement) => {
            ps.setString(1, app)
            ps.setString(2, form)
            ps.setInt   (3, version)
          }
        )
      }

    // Clean index
    locally {

      val deleteWhereClause = whereConditions match {
        case Nil => ""
        case _   => "WHERE " + whereConditions.mkString(" AND ")
      }
      val orderByClause = provider match {
        case Provider.MySQL => " ORDER BY data_id"
        case _              => ""
      }
      val deleteFromValueIndexWhereSql =
        whatToReindex match {
          case AllData => ""
          case _ =>
            s"""|WHERE data_id IN (
                |   SELECT data_id
                |     FROM orbeon_i_current
                |   $deleteWhereClause
                | )
                |""".stripMargin
        }

      val countFromValueIndexSql  = "SELECT count(*) FROM orbeon_i_control_text " + deleteFromValueIndexWhereSql
      val deleteFromValueIndexSql = "DELETE          FROM orbeon_i_control_text " + deleteFromValueIndexWhereSql

      // Check if there is anything to delete from the value index
      val countFromValueIndex =
        useAndClose(connection.prepareStatement(countFromValueIndexSql)) { ps ⇒
          paramSetter(ps)
          useAndClose(ps.executeQuery()) { rs ⇒
            rs.next()
            rs.getInt(1)
          }
        }

      val deleteFromCurrentIndex =
        s"""|DELETE FROM orbeon_i_current
            |$deleteWhereClause
            |""".stripMargin

      val deleteSql = List(
        if (countFromValueIndex > 0) Some(deleteFromValueIndexSql) else None,
        Some(deleteFromCurrentIndex)
      ).flatten

      deleteSql.foreach { deleteSql =>
        useAndClose(connection.prepareStatement(deleteSql + orderByClause)) { ps ⇒
          paramSetter(ps)
          ps.executeUpdate()
        }
      }
    }

    val currentFromWhere =
      s"""|    FROM
          |      orbeon_form_data d,
          |      (
          |        SELECT
          |          document_id,
          |          draft,
          |          max(last_modified_time) last_modified_time
          |        FROM
          |          orbeon_form_data
          |        ${whereConditions.nonEmpty.string("WHERE")}
          |          ${whereConditions.mkString(" AND ")}
          |        GROUP BY
          |          document_id,
          |          draft
          |      ) l
          |   WHERE
          |     d.document_id          = l.document_id        AND
          |     d.last_modified_time   = l.last_modified_time AND
          |     d.deleted              = 'N'
          |""".stripMargin

    // Count how many documents we'll reindex, and tell progress code
    val countSql =
      s"""|SELECT count(*)
          |$currentFromWhere
          |""".stripMargin
    useAndClose(connection.prepareStatement(countSql)) { ps =>
      paramSetter(ps)
      useAndClose(ps.executeQuery()) { rs =>
        rs.next()
        val count = rs.getInt(1)
        Backend.setProviderDocumentTotal(count)
      }
    }

    // Get all the row from orbeon_form_data that are "latest" and not deleted
    val xmlCol = Provider.xmlCol(provider, "d")
    val currentDataSql =
      s"""  SELECT d.id,
         |         d.created,
         |         d.last_modified_time,
         |         d.last_modified_by,
         |         d.username,
         |         d.groupname,
         |         d.organization_id,
         |         d.app,
         |         d.form,
         |         d.form_version,
         |         d.stage,
         |         d.document_id,
         |         d.draft,
         |         $xmlCol
         |$currentFromWhere
         |ORDER BY app, form
         |""".stripMargin

    useAndClose(connection.prepareStatement(currentDataSql)) { ps =>
      paramSetter(ps)
      useAndClose(ps.executeQuery()) { currentData =>

        // Info on indexed controls for a given app/form
        case class FormIndexedControls(
          app             : String,
          form            : String,
          indexedControls : Seq[IndexedControl]
        )

        // Go through each data document
        // - we keep track of the indexed controls along in the iteration, and thus avoid recomputing them
        var prevIndexedControls: Option[FormIndexedControls] = None
        while (currentData.next() && StatusStore.getStatus != Status.Stopping) {

          Backend.setProviderDocumentNext()
          val app = currentData.getString("app")
          val form = currentData.getString("form")

          // Get indexed controls for current app/form
          val indexedControls: Seq[IndexedControl] = prevIndexedControls match {
            case Some(FormIndexedControls(`app`, `form`, indexedControls)) =>
              // Use indexed controls from previous iteration
              indexedControls
            case _ =>
              // Compute indexed controls reading the form definition
              FormRunner.readPublishedForm(app, form)(RelationalUtils.Logger) match {
                case None =>
                  RelationalUtils.Logger.logError("", s"Can't index documents for $app/$form as form definition can't be found")
                  Seq.empty
                case Some(formDefinition) =>
                  findIndexedControls(
                    formDefinition,
                    FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form)
                  )
              }
          }

          // Insert into the "current data" table
          val position = Iterator.from(1)

          val insertIntoCurrentSql =
            """INSERT INTO orbeon_i_current
              |           (data_id,
              |            created,
              |            last_modified_time,
              |            last_modified_by,
              |            username,
              |            groupname,
              |            organization_id,
              |            app,
              |            form,
              |            form_version,
              |            stage,
              |            document_id,
              |            draft)
              |    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.stripMargin

          useAndClose(connection.prepareStatement(insertIntoCurrentSql)) { ps =>
            ps.setInt      (position.next(), currentData.getInt("id"))
            ps.setTimestamp(position.next(), currentData.getTimestamp("created"))
            ps.setTimestamp(position.next(), currentData.getTimestamp("last_modified_time"))
            ps.setString   (position.next(), currentData.getString("last_modified_by"))
            ps.setString   (position.next(), currentData.getString("username"))
            ps.setString   (position.next(), currentData.getString("groupname"))
            RelationalUtils.getIntOpt(currentData, "organization_id") match {
              case Some(id) => ps.setInt(position.next(), id)
              case None     => ps.setNull(position.next(), java.sql.Types.INTEGER)
            }
            ps.setString   (position.next(), app)
            ps.setString   (position.next(), form)
            ps.setInt      (position.next(), currentData.getInt("form_version"))
            ps.setString   (position.next(), currentData.getString("stage"))
            ps.setString   (position.next(), currentData.getString("document_id"))
            ps.setString   (position.next(), currentData.getString("draft"))
            ps.executeUpdate()
            ps.close()
          }

          // Read data (XML)
          // - using lazy, as we might not need the data, if there are no controls to index
          // - return root element, as XPath this is the node XPath expressions are relative to
          lazy val dataRootElement: NodeInfo = {
            val document = Provider.readXmlColumn(provider, currentData)
            document.descendant(*).head
          }

          // Extract and insert value for each indexed control
          for (control <- indexedControls) {

            val nodes = scaxon.XPath.eval(dataRootElement, control.xpath, FbNamespaceMapping).asInstanceOf[Seq[NodeInfo]]
            for ((node, pos) <- nodes.zipWithIndex) {
              val nodeValue = truncateValue(provider, node.getStringValue)
              // For indexing, we are not interested in empty values
              if (!nodeValue.isEmpty) {
                val position = Iterator.from(1)
                val insertIntoControlTextSql =
                  """INSERT INTO orbeon_i_control_text
                    |           (data_id,
                    |            pos,
                    |            control,
                    |            val)
                    |    VALUES (? , ? , ? , ? )
                  """.stripMargin
                useAndClose(connection.prepareStatement(insertIntoControlTextSql)) { ps =>
                  ps.setInt   (position.next(), currentData.getInt("id"))
                  ps.setInt   (position.next(), pos + 1)
                  ps.setString(position.next(), control.xpath)
                  ps.setString(position.next(), nodeValue)
                  ps.executeUpdate()
                  ps.close()
                }
              }
            }
          }
          // Pass current indexed controls to the next iteration
          prevIndexedControls = Some(FormIndexedControls(app, form, indexedControls))
        }
      }
    }
  }

  /**
   * If control values are "really long", we might not be able fully index them. Here we truncate values stored
   * in the index table so it doesn't exceed the limit imposed by the type used to store the value in
   * `orbeon_i_control_text` for the relevant database.
   *
   * - For MySQL, `text` can [store][MySQL text] up to pow(2, 16-1) bytes. Since UTF-8 encoding can take up to 4 bytes
   *   per character, we conservatively divide this by 4 to get the max number of characters. In MySQL 5.6, with the
   *   UTF-8 uses a [3-byte encoding][MySQL utf], but the documentation says it might use 4 in the future.
   *
   *   [MySQL text]: http://dev.mysql.com/doc/refman/5.6/en/storage-requirements.html#idp59499472
   *   [MySQL utf]: http://dev.mysql.com/doc/refman/5.6/en/charset-unicode-utf8mb3.html
   */
  private def truncateValue(provider: Provider, value: String): String = {
    // Limit, if any, based on the provider
    val limit: Option[Int] = provider match {
      case MySQL => Option(math.floor((math.pow(2, 16) - 1) / 4).toInt)
      case _     => None
    }
    limit match {
      case Some(l) if l < value.length => value.substring(0, l)
      case _                           => value
    }
  }

  // Prefixes used in Form Builder; prefixes in other documents, for now, are not supported
  private val FbNamespaceMapping = NamespaceMapping(Map(
    "xh" -> XMLConstants.XHTML_NAMESPACE_URI,
    "xf" -> XFormsConstants.XFORMS_NAMESPACE_URI
  ).asJava)

}
