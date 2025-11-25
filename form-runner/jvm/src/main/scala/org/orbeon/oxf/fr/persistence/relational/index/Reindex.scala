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

import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.persistence.PersistenceMetadataSupport
import org.orbeon.oxf.fr.persistence.relational.*
import org.orbeon.oxf.fr.persistence.relational.Provider.MySQL
import org.orbeon.oxf.fr.persistence.relational.WhatToReindex.*
import org.orbeon.oxf.fr.persistence.relational.index.status.{Backend, Status, StatusStore}
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, FormRunner}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames
import org.orbeon.xml.NamespaceMapping

import java.sql.PreparedStatement
import scala.util.{Failure, Success}


trait Reindex extends FormDefinition {

  // Reindexing is a 3-step process:
  //   1. Clear the index
  //   2. Get the documents to index
  //   3. For each document:
  //      - add 1 row to orbeon_i_current
  //      - add as many as necessary to orbeon_i_control_text
  def reindex(
    provider       : Provider,
    whatToReindex  : WhatToReindex,
    clearOnly      : Boolean,
    connectionOpt  : Option[java.sql.Connection] = None
  )(implicit
    externalContext: ExternalContext,
    indentedLogger : IndentedLogger,
    propertySet    : PropertySet
  ): Unit = {

    // If a document id was provided, produce WHERE clause, and set parameter
    val (whereConditions, paramSetter, updateStatus) =
      whatToReindex match {
        case AllData => (
          Nil,
          (_: PreparedStatement) => (),
          true
        )
        case DataForDocumentId(id, _) => (
          List("document_id = ?"),
          (ps: PreparedStatement) => ps.setString(1, id),
          false
        )
        case DataForForm((AppForm(app, form), version)) => (
          List(
            "app = ?",
            "form = ?",
            "form_version = ?"
          ),
          (ps: PreparedStatement) => {
            ps.setString(1, app)
            ps.setString(2, form)
            ps.setInt   (3, version)
          },
          true
        )
      }

    val currentFromWhere =
      s"""|    FROM
          |      orbeon_form_data d,
          |      (
          |        SELECT
          |          document_id,
          |          draft,
          |          max(id) id
          |        FROM
          |          orbeon_form_data
          |        ${whereConditions.nonEmpty.string("WHERE")}
          |          ${whereConditions.mkString(" AND ")}
          |        GROUP BY
          |          document_id,
          |          draft
          |      ) l
          |   WHERE
          |     d.id      = l.id AND
          |     d.deleted = 'N'
          |""".stripMargin

    val distinctForms: List[AppFormVersion] = RelationalUtils.withConnection(connectionOpt) { connection =>

      // Clear index
      locally {

        val deleteWhereClause = whereConditions match {
          case Nil => ""
          case _   => "WHERE " + whereConditions.mkString(" AND ")
        }

        // 2025-06-13: `ORDER BY` was added for #3606 and appeared to solve a problem. However, it costs a huge amount
        // of performance when reindexing a single document. Cautiously remove it in that specific case.
        val orderByClause = (provider, whatToReindex) match {
          case (Provider.MySQL, AllData | DataForForm(_)) => " ORDER BY data_id"
          case _                                          => ""
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
          useAndClose(connection.prepareStatement(countFromValueIndexSql)) { ps =>
            paramSetter(ps)
            useAndClose(ps.executeQuery()) { rs =>
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
          useAndClose(connection.prepareStatement(deleteSql + orderByClause)) { ps =>
            paramSetter(ps)
            ps.executeUpdate()
          }
        }
      }

      // https://github.com/orbeon/orbeon-forms/issues/6915
      if (clearOnly)
        return

      if (updateStatus) {
        // Count how many documents we'll reindex, and tell progress code (side effect)
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
      }

      whatToReindex match {
        case DataForDocumentId(_, appFormVersion) =>
          List(appFormVersion)
        case DataForForm(appFormVersion) =>
          List(appFormVersion)
        case AllData =>
          val distinctFormsSql =
            s"""|SELECT DISTINCT
                |       app,
                |       form,
                |       form_version
                |$currentFromWhere
                |""".stripMargin

          useAndClose(connection.prepareStatement(distinctFormsSql)) { ps =>
            paramSetter(ps)
            useAndClose(ps.executeQuery()) { rs =>
              var forms = List.empty[AppFormVersion]
              while (rs.next()) {
                val app     = rs.getString("app")
                val form    = rs.getString("form")
                val version = rs.getInt("form_version")
                forms = (AppForm(app, form), version) :: forms
              }
              forms
            }
          }
      }
    }

    val formsToIndexedControlsXPaths: Map[AppFormVersion, List[String]] =
      distinctForms.map { case appFormVersion@(appForm@AppForm(app, form), version) =>
        val formDetailsTry =
          PersistenceMetadataSupport.readPublishedFormStorageDetails(
            appForm, FormDefinitionVersion.Specific(version))
        val indexedControlsXPaths = formDetailsTry match {
          case Failure(_) =>
            error(s"Can't index documents for $app/$form as form definition can't be found")
            Nil
          case Success(FormStorageDetails(_, indexedFields, _)) =>
            indexedFields.value
        }
        appFormVersion -> indexedControlsXPaths
      }.toMap

    RelationalUtils.withConnection(connectionOpt) { connection =>
      // Get all the rows from `orbeon_form_data` that are "latest" and not deleted
      val xmlCol         = Provider.xmlColSelect(provider, "d")
      val selectXmlSql   = s"SELECT $xmlCol FROM orbeon_form_data d WHERE id = ?"
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
           |         d.draft
           |$currentFromWhere
           |ORDER BY app, form, form_version
           |""".stripMargin

      var currentBatchSize = 0
      val maxBatchSize     = FormRunner.providerPropertyAsInteger(
          provider = provider.entryName,
          property = "max-batch-size",
          default  = 1000
        )

      useAndClose(connection.prepareStatement(currentDataSql)) { currentDataPS =>
        useAndClose(connection.prepareStatement(InsertIntoCurrentSql)) { insertIntoCurrentPS =>
          useAndClose(connection.prepareStatement(InsertIntoControlTextSql)) { insertIntoControlTextPS =>
            useAndClose(connection.prepareStatement(selectXmlSql)) { selectXmlPS =>
              paramSetter(currentDataPS)
              useAndClose(currentDataPS.executeQuery()) { currentDataRS =>

                // Go through each data document
                while (currentDataRS.next() && StatusStore.getStatus != Status.Stopping) {

                  if (updateStatus)
                    Backend.setProviderDocumentNext()

                  val app         = currentDataRS.getString("app")
                  val form        = currentDataRS.getString("form")
                  val formVersion = currentDataRS.getInt   ("form_version")

                  // Get indexed controls for current app/form
                  val indexedControlsXPaths: List[String] = formsToIndexedControlsXPaths(AppForm(app, form), formVersion)

                  // Insert into the "current data" table
                  val position = Iterator.from(1)

                  insertIntoCurrentPS.setInt      (position.next(), currentDataRS.getInt("id"))
                  insertIntoCurrentPS.setTimestamp(position.next(), currentDataRS.getTimestamp("created"))
                  insertIntoCurrentPS.setTimestamp(position.next(), currentDataRS.getTimestamp("last_modified_time"))
                  insertIntoCurrentPS.setString   (position.next(), currentDataRS.getString("last_modified_by"))
                  insertIntoCurrentPS.setString   (position.next(), currentDataRS.getString("username"))
                  insertIntoCurrentPS.setString   (position.next(), currentDataRS.getString("groupname"))
                  RelationalUtils.getIntOpt(currentDataRS, "organization_id") match {
                    case Some(id) => insertIntoCurrentPS.setInt(position.next(), id)
                    case None     => insertIntoCurrentPS.setNull(position.next(), java.sql.Types.INTEGER)
                  }
                  insertIntoCurrentPS.setString   (position.next(), app)
                  insertIntoCurrentPS.setString   (position.next(), form)
                  insertIntoCurrentPS.setInt      (position.next(), currentDataRS.getInt("form_version"))
                  insertIntoCurrentPS.setString   (position.next(), currentDataRS.getString("stage"))
                  insertIntoCurrentPS.setString   (position.next(), currentDataRS.getString("document_id"))
                  insertIntoCurrentPS.setString   (position.next(), currentDataRS.getString("draft"))
                  insertIntoCurrentPS.addBatch()
                  currentBatchSize = maybeExecuteBatches(insertIntoCurrentPS, insertIntoControlTextPS, currentBatchSize, maxBatchSize)

                  // Read data (XML)
                  // - using lazy, as we might not need the data, if there are no controls to index
                  // - return root element, as XPath this is the node XPath expressions are relative to
                  lazy val dataRootElement: NodeInfo = {
                    selectXmlPS.setInt(1, currentDataRS.getInt("id"))
                    val document = useAndClose(selectXmlPS.executeQuery()) { selectXmlRS =>
                      selectXmlRS.next()
                      Provider.readXmlColumn(provider, selectXmlRS)
                    }
                    document.descendant(*).head
                  }

                  // Extract and insert value for each indexed control
                  for (controlXPath <- indexedControlsXPaths) {
                    val nodes = scaxon.XPath.evalNodes(dataRootElement, controlXPath, FbNamespaceMapping)
                    for ((node, pos) <- nodes.zipWithIndex) {
                      val nodeValue = truncateValue(provider, node.getStringValue)
                      // For indexing, we are not interested in empty values
                      if (nodeValue.nonEmpty) {
                        val position = Iterator.from(1)
                        insertIntoControlTextPS.setInt   (position.next(), currentDataRS.getInt("id"))
                        insertIntoControlTextPS.setInt   (position.next(), pos + 1)
                        insertIntoControlTextPS.setString(position.next(), controlXPath)
                        insertIntoControlTextPS.setString(position.next(), nodeValue)
                        insertIntoControlTextPS.addBatch()
                        currentBatchSize = maybeExecuteBatches(insertIntoCurrentPS, insertIntoControlTextPS, currentBatchSize, maxBatchSize)
                      }
                    }
                  }
                }

                insertIntoCurrentPS.executeBatch()
                insertIntoControlTextPS.executeBatch()
              }
            }
          }
        }
      }
    }
  }

  private def maybeExecuteBatches(
    insertIntoCurrentPS     : PreparedStatement,
    insertIntoControlTextPS : PreparedStatement,
    currentBatchSize        : Int,
    maxBatchSize            : Int
  ): Int =
    if (currentBatchSize + 1 >= maxBatchSize) {
      insertIntoCurrentPS.executeBatch()
      insertIntoControlTextPS.executeBatch()
      0
    } else {
      currentBatchSize + 1
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

  private val InsertIntoCurrentSql =
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

  private val InsertIntoControlTextSql =
    """INSERT INTO orbeon_i_control_text
      |           (data_id,
      |            pos,
      |            control,
      |            val)
      |    VALUES (? , ? , ? , ? )
    """.stripMargin

  // Prefixes used in Form Builder; prefixes in other documents, for now, are not supported
  private val FbNamespaceMapping = NamespaceMapping(Map(
    "xh" -> XMLConstants.XHTML_NAMESPACE_URI,
    "xf" -> XFormsNames.XFORMS_NAMESPACE_URI
  ))
}
