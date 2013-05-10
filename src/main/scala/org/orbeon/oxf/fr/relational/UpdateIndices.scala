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
import org.orbeon.oxf.pipeline.api.ExternalContext.{Response, Request}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import scala.collection.JavaConversions._
import org.orbeon.oxf.resources.handler.HTTPURLConnection
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.XML._
import javax.xml.transform.stream.StreamResult
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.externalcontext.URLRewriter
import collection.JavaConverters._
import org.orbeon.oxf.util._
import scala.util.matching.Regex
import javax.naming.{Context, InitialContext}
import javax.sql.DataSource
import org.orbeon.oxf.fr.FormRunner
import scala.Some

class UpdateIndices extends ProcessorImpl {

    private val ReindexPath = """/fr/service/([^/]+)/reindex""".r
    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[UpdateIndices]), "")

    override def start(pipelineContext: PipelineContext) {

        // Provider, e.g. "mysql", "oracle", inferred from the request path
        val provider = {
            val incomingPath = NetUtils.getExternalContext.getRequest.getRequestPath
            ReindexPath.unapplySeq(incomingPath).get.head
        }
        // Get connection to the database
        val dataSource = {
            val propertySet = Properties.instance.getPropertySet
            val datasourceProperty = Seq("oxf.fr.persistence", provider, "datasource") mkString "."
            val datasource = propertySet.getString(datasourceProperty)
            val jndiContext = new InitialContext().lookup("java:comp/env").asInstanceOf[Context]
            jndiContext.lookup(datasource).asInstanceOf[DataSource]
        }

        useAndClose(dataSource.getConnection) { connection ⇒
            connection.prepareStatement("delete from orbeon_i_control_text").execute()

            // Get current data
            val currentData = connection.prepareStatement(
                """select id, created, last_modified, username, app, form, document_id, xml
                  |  from orbeon_form_data
                  | where (app, form, document_id, last_modified) in
                  |       (
                  |             select app, form, document_id, max(last_modified) last_modified
                  |               from orbeon_form_data
                  |           group by app, form, document_id
                  |       )
                  |   and deleted = 'N'""").executeQuery()

            // Go through each current data document and index it
            while (currentData.next()) {
                val app = currentData.getString("app")
                val form = currentData.getString("form")
                val documentId = currentData.getString("document_id")

                FormRunner.readPublishedForm(app, form) match {
                    case None ⇒ Logger.logError("", s"Can't find published for ${app}/${form}, so can't index document ${documentId}")
                    case Some(document) ⇒

                }


                // Get form
                // - already done in schema code
                // - need to cache
            }
        }
    }
}

