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

import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider._
import org.orbeon.oxf.fr.persistence.relational.index.status.{Backend, StatusStore, Stopping}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xml.{NamespaceMapping, XMLConstants}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

trait Reindex extends FormDefinition {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[Reindex]))

  sealed trait                                                              WhatToReindex
  case object  AllData                                              extends WhatToReindex
  case class   DataForDocumentId(documentId: String)                extends WhatToReindex
  case class   DataForForm(app: String, form: String, version: Int) extends WhatToReindex

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

    if (Index.ProvidersWithIndexSupport.contains(provider)) {

      // If a document id was provided, produce WHERE clause, and set parameter
      val (whereClause, paramSetter) =
        whatToReindex match {
          case AllData ⇒ (
            "",
            (ps: PreparedStatement) ⇒ Unit
          )
          case DataForDocumentId(id) ⇒ (
            "WHERE document_id = ?",
            (ps: PreparedStatement) ⇒ ps.setString(1, id)
          )
          case DataForForm(app, form, version) ⇒ (
            """WHERE app = ?          AND
              |      form = ?         AND
              |      form_version = ?
            """.stripMargin,
            (ps: PreparedStatement) ⇒ {
              ps.setString(1, app)
              ps.setString(2, form)
              ps.setInt   (3, version)
            }
          )
        }

      // Clean index
      connection
        .prepareStatement("DELETE FROM orbeon_i_control_text" + (
            whatToReindex match {
              case AllData ⇒ ""
              case _ ⇒
                s""" WHERE data_id IN (
                   |   SELECT data_id
                   |     FROM orbeon_i_current
                   |   $whereClause
                   | )
                   |""".stripMargin
            }
          )
        )
        .kestrel(paramSetter)
        .execute()
      connection
        .prepareStatement(s"DELETE FROM orbeon_i_current $whereClause")
        .kestrel(paramSetter)
        .execute()

      // Get count of documents to index for progress indicator
      val currentFromWhere =
        s"""|    FROM
            |      orbeon_form_data d,
            |      (
            |        SELECT
            |          app,
            |          form,
            |          document_id,
            |          max(last_modified_time) last_modified_time
            |        FROM
            |          orbeon_form_data
            |        $whereClause
            |        GROUP BY
            |          app, form, document_id
            |      ) l
            |   WHERE
            |     d.app                = l.app                AND
            |     d.form               = l.form               AND
            |     d.document_id        = l.document_id        AND
            |     d.last_modified_time = d.last_modified_time AND
            |     d.deleted            = 'N'
            |""".stripMargin


      // Count how many documents we'll reindex, and tell progress code
      connection
        .prepareStatement(
          s"""  SELECT count(*)
             |$currentFromWhere
           """.stripMargin)
        .kestrel(paramSetter)
        .executeQuery()
        .kestrel(_.next())
        .pipe(_.getInt(1))
        .kestrel(Backend.setProviderDocumentTotal)

      // Get all the row from orbeon_form_data that are "latest" and not deleted
      val xmlCol = Provider.xmlCol(provider, "d")
      val currentData = connection
        .prepareStatement(
          s"""  SELECT d.id,
             |         d.created,
             |         d.last_modified_time,
             |         d.last_modified_by,
             |         d.username,
             |         d.groupname,
             |         d.app,
             |         d.form,
             |         d.form_version,
             |         d.document_id,
             |         d.draft,
             |         $xmlCol
             |$currentFromWhere
             |ORDER BY app, form
             |""".stripMargin)
        .kestrel(paramSetter)
        .executeQuery()

      // Info on indexed controls for a given app/form
      case class FormIndexedControls(
        app             : String,
        form            : String,
        indexedControls : Seq[IndexedControl]
      )

      // Go through each data document
      // - we keep track of the indexed controls along in the iteration, and thus avoid recomputing them
      var prevIndexedControls: Option[FormIndexedControls] = None
      while (currentData.next() && StatusStore.getStatus != Stopping) {

        Backend.setProviderDocumentNext()
        val app = currentData.getString("app")
        val form = currentData.getString("form")

        // Get indexed controls for current app/form
        val indexedControls: Seq[IndexedControl] = prevIndexedControls match {
          case Some(FormIndexedControls(`app`, `form`, indexedControls)) ⇒
            // Use indexed controls from previous iteration
            indexedControls
          case _ ⇒
            // Compute indexed controls reading the form definition
            FormRunner.readPublishedForm(app, form) match {
              case None ⇒
                Logger.logError("", s"Can't index documents for $app/$form as form definition can't be found")
                Seq.empty
              case Some(formDefinition) ⇒
                findIndexedControls(formDefinition)
            }
        }

        // Insert into the "current data" table
        val position = Iterator.from(1)
        val insert = connection.prepareStatement(
          """INSERT INTO orbeon_i_current
            |           (data_id,
            |            created,
            |            last_modified_time,
            |            last_modified_by,
            |            username,
            |            groupname,
            |            app,
            |            form,
            |            form_version,
            |            document_id,
            |            draft)
            |    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """.stripMargin
        )
        insert.setInt(position.next(), currentData.getInt("id"))
        insert.setTimestamp(position.next(), currentData.getTimestamp("created"))
        insert.setTimestamp(position.next(), currentData.getTimestamp("last_modified_time"))
        insert.setString(position.next(), currentData.getString("last_modified_by"))
        insert.setString(position.next(), currentData.getString("username"))
        insert.setString(position.next(), currentData.getString("groupname"))
        insert.setString(position.next(), app)
        insert.setString(position.next(), form)
        insert.setInt(position.next(), currentData.getInt("form_version"))
        insert.setString(position.next(), currentData.getString("document_id"))
        insert.setString(position.next(), currentData.getString("draft"))
        insert.execute()

        // Read data (XML)
        // - using lazy, as we might not need the data, if there are no controls to index
        // - return root element, as XPath this is the node XPath expressions are relative to
        lazy val dataRootElement: NodeInfo = {
          val document = readXmlColumn(provider, currentData)
          document.descendant(*).head
        }

        // Extract and insert value for each indexed control
        for (control ← indexedControls) {

          val nodes = XML.eval(dataRootElement, control.xpath, FbNamespaceMapping).asInstanceOf[Seq[NodeInfo]]
          for ((node, pos) ← nodes.zipWithIndex) {
            val nodeValue = truncateValue(provider, node.getStringValue)
            // For indexing, we are not interested in empty values
            if (!nodeValue.isEmpty) {
              val position = Iterator.from(1)
              val insert = connection.prepareStatement(
                """INSERT INTO orbeon_i_control_text
                  |           (data_id,
                  |            pos,
                  |            control,
                  |            val)
                  |    VALUES (? , ? , ? , ? )
                """.stripMargin
              )
              insert.setInt(position.next(), currentData.getInt("id"))
              insert.setInt(position.next(), pos + 1)
              insert.setString(position.next(), control.xpath)
              insert.setString(position.next(), nodeValue)
              insert.executeUpdate()
              // For a query, we need to close so not to keep an open cursor; it's unclear if this
              // is really needed with executeUpdate(), but it shouldn't hurt
              insert.close()
            }
          }
        }

        // Pass current indexed controls to the next iteration
        prevIndexedControls = Some(FormIndexedControls(app, form, indexedControls))
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
      case MySQL ⇒ Option(math.floor((math.pow(2, 16) - 1) / 4).toInt)
      case _     ⇒ None
    }
    limit match {
      case Some(l) if l < value.length ⇒ value.substring(0, l)
      case _                           ⇒ value
    }
  }

  // Prefixes used in Form Builder; prefixes in other documents, for now, are not supported
  private val FbNamespaceMapping = new NamespaceMapping(Map(
    "xh" → XMLConstants.XHTML_NAMESPACE_URI,
    "xf" → XFormsConstants.XFORMS_NAMESPACE_URI
  ).asJava)

}
