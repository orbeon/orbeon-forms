/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.relational

import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.xml.{NamespaceMapping, XMLConstants, TransformerUtils}
import javax.xml.transform.stream.StreamSource
import org.orbeon.oxf.util._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.fr.relational.Index.IndexedControl
import org.orbeon.oxf.xforms.XFormsConstants
import collection.JavaConverters._
import org.orbeon.scaxon.XML

/**
 * Processor repopulating the relational indices. This doesn't create the tables, but deletes their content
 * and repopulates them from scratch.
 *
 * - mapped to `fr:persistence-reindex` in `processors.xml`
 * - mapped to `/fr/service/[provider]/reindex` in `fr/page-flow.xml`
 */
class ReindexProcessor extends ProcessorImpl {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[ReindexProcessor]))

  private val ReindexPathRegex    = """/fr/service/([^/]+)/reindex""".r

  // Prefixes used in Form Builder; prefixes in other documents, for now, are not supported
  val FbNamespaceMapping = new NamespaceMapping(Map(
    "xh" → XMLConstants.XHTML_NAMESPACE_URI,
    "xf" → XFormsConstants.XFORMS_NAMESPACE_URI
  ).asJava)

  override def start(pipelineContext: PipelineContext): Unit = {

    val ReindexPathRegex(provider) = NetUtils.getExternalContext.getRequest.getRequestPath

    RelationalUtils.withConnection { connection ⇒

      // Clean index
      connection.prepareStatement("DELETE FROM orbeon_i_current").execute()
      connection.prepareStatement("DELETE FROM orbeon_i_control_text").execute()

      // Get all the row from orbeon_form_data that are "latest" and not deleted
      val currentData = connection.prepareStatement(
        """  SELECT id, created, last_modified, username, app, form, document_id, xml
          |    FROM orbeon_form_data
          |   WHERE (app, form, document_id, last_modified) IN
          |         (
          |               SELECT app, form, document_id, max(last_modified) last_modified
          |                 FROM orbeon_form_data
          |             GROUP BY app, form, document_id
          |         )
          |     AND deleted = 'N'
          |ORDER BY app, form
          |""".stripMargin).executeQuery()

      // Info on indexed controls for a given app/form
      case class FormIndexedControls(app: String, form: String, indexedControls: Seq[IndexedControl])

      // Go through each data document
      // - we keep track of the indexed controls along in the iteration, and thus avoid recomputing them
      var prevIndexedControls: Option[FormIndexedControls] = None
      while (currentData.next()) {
        val app  = currentData.getString("app")
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
                Index.findIndexedControls(formDefinition)
            }
        }

        // Insert into the "current data" table
        val insert = connection.prepareStatement(
          """INSERT INTO orbeon_i_current
            |           (data_id, document_id, created, last_modified, username, app, form)
            |    VALUES (?, ?, ?, ?, ?, ?, ?)
          """.stripMargin)
        insert.setInt      (1, currentData.getInt      ("id"))
        insert.setString   (2, currentData.getString   ("document_id"))
        insert.setTimestamp(3, currentData.getTimestamp("created"))
        insert.setTimestamp(4, currentData.getTimestamp("last_modified"))
        insert.setString   (5, currentData.getString   ("username"))
        insert.setString   (6, app)
        insert.setString   (7, form)
        insert.execute()

        // Read data (XML)
        // - using lazy, as we might not need the data, if there are no controls to index
        // - return root element, as XPath this is the node XPath expressions are relative to
        lazy val dataRootElement: NodeInfo = {
          val dataClob = currentData.getClob("xml")
          val source = new StreamSource(dataClob.getCharacterStream)
          val document = TransformerUtils.readTinyTree(XPath.GlobalConfiguration, source, false)
          document \\ * head
        }

        // Extract and insert value for each indexed control
        for (control ← indexedControls) {

          val values = XML.eval(dataRootElement, control.xpath, FbNamespaceMapping).asInstanceOf[Seq[NodeInfo]]
          for ((value, position) ← values.zipWithIndex) {
            val insert = connection.prepareStatement(
              """insert into orbeon_i_control_text
                |           (data_id, username, app, form, control, pos, val)
                |    values (?, ?, ?, ?, ?, ?, ?)
              """.stripMargin)
            insert.setInt      (1, currentData.getInt("id"))
            insert.setString   (2, currentData.getString("username"))
            insert.setString   (3, app)
            insert.setString   (4, form)
            insert.setString   (5, control.name)
            insert.setInt      (6, position + 1)
            insert.setString   (7, truncateValue(provider, value.getStringValue))
            insert.execute()
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
  private def truncateValue(provider: String, value: String): String = {
    // Limit, if any, based on the provider
    val limit: Option[Int] = provider match {
      case "mysql" ⇒ Option(math.floor((math.pow(2, 16) - 1) / 4).toInt)
      case _       ⇒ None
    }
    limit match {
      case Some(l) if l < value.length ⇒ value.substring(0, l)
      case _                           ⇒ value
    }
  }
}
