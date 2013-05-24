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

import org.orbeon.oxf.pipeline.api.{XMLReceiver, PipelineContext}
import org.orbeon.oxf.processor.{ProcessorOutput, ProcessorImpl}
import org.orbeon.oxf.util.{XPathCache, NetUtils}
import org.orbeon.oxf.util.DateUtils.DateTime
import org.orbeon.oxf.fr.relational.RelationalUtils._
import org.orbeon.scaxon.XML._
import java.sql.Timestamp
import org.orbeon.scaxon.XML

/**
 * Implementation of the persistence API for relational databases.
 */
class PersistenceAPIProcessor extends ProcessorImpl {

    private val SearchPathRegex= """/fr/service/([^/^.]+)/search/([^/^.]+)/([^/^.]+)""".r

    case class DocumentMetadata(dataId: Int, documentId: String, created: Timestamp, lastModified: Timestamp)

    override def createOutput(name: String): ProcessorOutput = {
        addOutput(name, new ProcessorOutputImpl(this, name) {
            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
                val SearchPathRegex(provider, app, form) = NetUtils.getExternalContext.getRequest.getRequestPath
                withConnection(provider) { connection ⇒

                    // <query> elements from request
                    val requestQueries = {
                        val searchRequest = readInputAsTinyTree(pipelineContext, getInputByName(ProcessorImpl.INPUT_DATA),
                            XPathCache.getGlobalConfiguration)
                        searchRequest \ "search" \ "query"
                    }

                    /**
                     * Get the documents that match the search criteria, for the page we're are, returning the metadata for
                     * those document. This typically returns a short list, since it is just for one page
                     */
                    val documentsMetadata: Seq[DocumentMetadata] = {

                        val nonEmptyQueries = requestQueries filter (_.getStringValue != "")
                        val getDocumentsQuery = {
                            val selectStart =   """  select data_id, document_id, created, last_modified from orbeon_i_current
                                                  |   where app  = ?
                                                  |	    and form = ?
                                                """.stripMargin
                            val controlMatch =  """	 and data_id in
                                                  |		 (
                                                  |		  select distinct data_id from orbeon_i_control_text
                                                  |		   where app     = ?
                                                  |			 and form    = ?
                                                  |			 and control = ?
                                                  |			 and match(val) against (?)
                                                  |		 )
                                                """.stripMargin
                            val selectEnd =     """order by last_modified desc, data_id
                                                  |   limit 10;
                                                """.stripMargin
                            selectStart + (controlMatch * nonEmptyQueries.length) + selectEnd
                        }

                        val getDocumentsStatement = {
                            val statement = connection.prepareStatement(getDocumentsQuery)
                            statement.setString(1, app)
                            statement.setString(2, form)
                            for ((query, index) ← nonEmptyQueries.zipWithIndex) {
                                statement.setString(index * 4 + 3, app)
                                statement.setString(index * 4 + 4, form)
                                statement.setString(index * 4 + 5, query.attValue("name"))
                                statement.setString(index * 4 + 6, query.getStringValue)
                            }
                            statement
                        }

                        (for (resultSet ← getDocumentsStatement) yield
                            DocumentMetadata(
                                dataId       = resultSet.getInt      ("data_id"),
                                documentId   = resultSet.getString   ("document_id"),
                                created      = resultSet.getTimestamp("created"),
                                lastModified = resultSet.getTimestamp("last_modified")
                            )
                        ).toSeq
                    }

                    /**
                     * For the rows returned, retrieve the columns values
                     */
                    case class Value(dataId: Int, control: String, pos: Int, value: String)
                    val controls = {
                        val summaryQueries = requestQueries filter (_.attValue("summary-field") == "true")
                        summaryQueries map (_.attValue("name"))
                    }
                    val values = {
                        val dataIds = documentsMetadata map (_.dataId.toString) mkString ", "
                        val getValues = connection.prepareStatement(
                            s"""  select data_id, control, pos, val
                               |    from orbeon_i_control_text
                               |   where data_id in ($dataIds)
                               |     and control in (${controls map sqlString mkString ", "})
                               |order by data_id, control, pos;
                               |""".stripMargin)
                        val values = (getValues map (resultSet ⇒ Value(
                            resultSet.getInt   ("data_id"),
                            resultSet.getString("control"),
                            resultSet.getInt   ("pos"),
                            resultSet.getString("val")
                        ))).toSeq
                        values groupBy (_.dataId) mapValues (_.groupBy(_.control))
                    }

                    /**
                     * Build XML returned by service
                     * - if there are multiple values for a given control, we return all of them, coma-separated,
                     *   and remove empty values to avoid uninformative ", , ," results; TODO: this bit of business is
                     *   better left of to the front-end, but requires changing the format of the returned data
                     */
                    val documentsXML =
                        <documents>{
                            for (metadata ← documentsMetadata) yield
                            <document created       ={DateTime.print(metadata.created.getTime) }
                                      last-modified ={DateTime.print(metadata.lastModified.getTime)}
                                      name          ={metadata.documentId.toString}>
                                <details>{
                                    val thisRowValues = values(metadata.dataId)
                                    for (control ← controls) yield
                                    <detail>{
                                        val thisControlValues = thisRowValues(control) sortBy (_.pos)
                                        thisControlValues map (_.value) filter (_ != "") mkString ", "
                                    } </detail>
                                }</details>
                            </document>
                        }</documents>

                    XML.elemToSAX(documentsXML, xmlReceiver)
                }
            }
        })
    }

}
