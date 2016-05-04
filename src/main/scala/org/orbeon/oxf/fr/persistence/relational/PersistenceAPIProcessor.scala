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
package org.orbeon.oxf.fr.persistence.relational

import java.sql.Timestamp

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.util.DateUtils.DateTime
import org.orbeon.oxf.util.{NetUtils, XPath}
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.XML
import org.orbeon.scaxon.XML._

import scala.collection.mutable.ListBuffer

// TODO: remove this, and any references, since this is now implemented in SearchProcessor

/**
 * Implementation of the persistence API for relational databases.
 */
class PersistenceAPIProcessor extends ProcessorImpl {

  private val SearchPathRegex= """/fr/service/([^/^.]+)/search/([^/^.]+)/([^/^.]+)""".r

  case class DocumentMetadata(dataId: Int, documentId: String, created: Timestamp, lastModified: Timestamp)

  override def createOutput(name: String): ProcessorOutput = {
    addOutput(name, new ProcessorOutputImpl(this, name) {
      def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
        val SearchPathRegex(_, app, form) = NetUtils.getExternalContext.getRequest.getRequestPath
        withConnection { connection ⇒

          // <query> elements from request
          val requestQueries = {
            val searchRequest = readInputAsTinyTree(pipelineContext, getInputByName(ProcessorImpl.INPUT_DATA),
              XPath.GlobalConfiguration)
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

            val documentsResultSet = getDocumentsStatement.executeQuery()
            val documents = ListBuffer[DocumentMetadata]()
            while (documentsResultSet.next())
              documents += DocumentMetadata(
                dataId       = documentsResultSet.getInt      ("data_id"),
                documentId   = documentsResultSet.getString   ("document_id"),
                created      = documentsResultSet.getTimestamp("created"),
                lastModified = documentsResultSet.getTimestamp("last_modified")
              )
            documents
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

            // Build query
            val dataIdsPlaceholders = ("?" * documentsMetadata.length)  mkString ", "
            val controlsPlaceholders = ("?" * controls.length) mkString ","
            val getValues = connection.prepareStatement(
              s"""  select data_id, control, pos, val
                 |    from orbeon_i_control_text
                 |   where data_id in ($dataIdsPlaceholders)
                 |     and control in ($controlsPlaceholders)
                 |order by data_id, control, pos;
                 |""".stripMargin)

            // Populate placeholders
            for ((documentMetadata, index) ← documentsMetadata.zipWithIndex)
              getValues.setInt(index + 1, documentMetadata.dataId)
            for ((control, index) ← controls.zipWithIndex)
              getValues.setString(documentsMetadata.length + index + 1, control)

            // Build tuples from result-set
            val resultSet = getValues.executeQuery()
            var values = ListBuffer[Value]()
            while (resultSet.next())
              values += Value(
                resultSet.getInt   ("data_id"),
                resultSet.getString("control"),
                resultSet.getInt   ("pos"),
                resultSet.getString("val")
              )
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
