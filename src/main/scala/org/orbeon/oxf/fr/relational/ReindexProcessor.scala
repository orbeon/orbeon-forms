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
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xml.{NamespaceMapping, XMLConstants, TransformerUtils}
import javax.xml.transform.stream.StreamSource
import org.orbeon.oxf.util._
import org.orbeon.scaxon.XML._
import javax.naming.{Context, InitialContext}
import javax.sql.DataSource
import org.orbeon.oxf.fr.FormRunner
import scala.Some
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.fr.relational.Index.IndexedControl
import java.sql.ResultSet
import org.orbeon.oxf.xforms.XFormsConstants
import collection.JavaConverters._

/**
 * Processor repopulating the relational indices. This doesn't create the tables, but deletes their content
 * and repopulates them from scratch.
 *
 * - mapped to `fr:persistence-reindex` in `processors.xml`
 * - mapped to `/fr/service/[provider]/reindex` in `fr/page-flow.xml`
 */
class ReindexProcessor extends ProcessorImpl {

    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[ReindexProcessor]), "")

    private val ReindexPathRegex    = """/fr/service/([^/]+)/reindex""".r
    private val XPathPredicateRegex = """\[[^\]]*\]""".r

    // Prefixes used in Form Builder; prefixes in other documents, for now, are not supported
    val FbNamespaceMapping = new NamespaceMapping(Map(
        "xh" → XMLConstants.XHTML_NAMESPACE_URI,
        "xf" → XFormsConstants.XFORMS_NAMESPACE_URI
    ).asJava)

    override def start(pipelineContext: PipelineContext) {

        // Provider, e.g. "mysql", "oracle", inferred from the request path
        val ReindexPathRegex(provider) = NetUtils.getExternalContext.getRequest.getRequestPath

        // Get connection to the database
        val dataSource = {
            val propertySet = Properties.instance.getPropertySet
            val datasourceProperty = Seq("oxf.fr.persistence", provider, "datasource") mkString "."
            val datasource = propertySet.getString(datasourceProperty)
            val jndiContext = new InitialContext().lookup("java:comp/env/jdbc").asInstanceOf[Context]
            jndiContext.lookup(datasource).asInstanceOf[DataSource]
        }

        useAndClose(dataSource.getConnection) { connection ⇒

            // Clean index
            connection.prepareStatement("delete from orbeon_i_control_text").execute()

            // Get current data
            val currentData = {
                val resultSet = connection.prepareStatement(
                    """select   id, created, last_modified, username, app, form, document_id, xml
                      |  from   orbeon_form_data
                      | where   (app, form, document_id, last_modified) in
                      |         (
                      |               select app, form, document_id, max(last_modified) last_modified
                      |                 from orbeon_form_data
                      |             group by app, form, document_id
                      |         )
                      |   and   deleted = 'N'
                      |order by app, form
                      |""".stripMargin).executeQuery()
                // We create an iterator so we can use the result set in a foldLeft
                new Iterator[ResultSet] {
                  def hasNext = resultSet.next()
                  def next() = resultSet
                  override def toString = "[Iterator]"
                }
            }

            // Info on indexed controls for a given app/form
            case class FormIndexedControls(app: String, form: String, indexedControls: Seq[IndexedControl])

            // Go through each data document
            // - using a foldLeft, so we can pass indexed controls along in the iteration, and thus avoid recomputing them
            currentData.foldLeft[Option[FormIndexedControls]](None)((prevIndexedControls, resultSet) ⇒ {
                val app  = resultSet.getString("app")
                val form = resultSet.getString("form")

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
                                Seq()
                            case Some(formDefinition) ⇒
                                Index.findIndexedControls(formDefinition)
                        }
                }

                // Read data (XML)
                // - using lazy, as we might not need the data, if there are no controls to index
                // - return root element, as XPath this is the node XPath expressions are relative to
                lazy val dataRootElement: NodeInfo = {
                    val dataClob = resultSet.getClob("xml")
                    val source = new StreamSource(dataClob.getCharacterStream)
                    val document = TransformerUtils.readTinyTree(XPathCache.getGlobalConfiguration, source, false)
                    document \\ * head
                }

                // Extract and insert value for each indexed control
                for (control ← indexedControls) {

                    // Remove predicates from XPath expression; applies to:
                    // - the [1] added by `Index.findIndexedControls` for the summary page;
                    //   those will go away when the search will return multiple values per control
                    // - in the FB form, the predicate for the language
                    val xpath = XPathPredicateRegex.replaceAllIn(control.xpath, "")

                    val values = XPathCache.evaluate(dataRootElement, xpath, FbNamespaceMapping, null, null, null, null, null, null)
                    for ((value, position) ← values.asScala.zipWithIndex) {
                        val insert = connection.prepareStatement(
                            """insert into orbeon_i_control_text
                              |           (data_id, username, app, form, control, pos, val)
                              |    values (?, ?, ?, ?, ?, ?, ?)
                            """.stripMargin)
                        insert.setInt   (1, resultSet.getInt("id"))
                        insert.setString(2, resultSet.getString("username"))
                        insert.setString(3, app)
                        insert.setString(4, form)
                        insert.setString(5, control.name)
                        insert.setInt   (6, position + 1)
                        insert.setString(7, truncateValue(provider, value.asInstanceOf[NodeInfo].getStringValue))
                        insert.execute()
                    }
                }

                // Pass current indexed controls to the next iteration
                Some(FormIndexedControls(app, form, indexedControls))
            })
        }
    }

    /**
     * If control values are "really long", we might not be able fully index them. Here we truncate values stored
     * in the index table so it doesn't exceed the limit imposed by the type used to store the value in
     * `orbeon_i_control_text` for the relevant database.
     *
     * - For MySQL, `text` can [store][MySQL text] up to 2^16-1 bytes. Since UTF-8 encoding can take up to 4 bytes
     *   per character, we conservatively divide this by 4 to get the max number of characters. In MySQL 5.6, with the
     *   UTF-8 uses a [3-byte encoding][MySQL utf], but the documentation says it might use 4 in the future.
     *
     *   [MySQL text]: http://dev.mysql.com/doc/refman/5.6/en/storage-requirements.html#idp59499472
     *   [MySQL utf]: http://dev.mysql.com/doc/refman/5.6/en/charset-unicode-utf8mb3.html
     */
    private def truncateValue(provider: String, value: String): String = {
        val limit: Option[Int] = provider match {
            case "mysql" ⇒ Option(math.floor((math.pow(2, 16) - 1) / 4).toInt)
            case _       ⇒ None
        }
        limit map (value.substring(0, _)) getOrElse value
    }
}
