/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.fr.mongdb

import com.mongodb.casbah.Imports._
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.io.{OutputStreamWriter, InputStream}
import javax.servlet.ServletException
import org.orbeon.oxf.util.ScalaUtils
import org.orbeon.oxf.xml.{TransformerUtils, XMLUtils}
import org.dom4j.{Document, Text, Element}
import scala.collection.JavaConversions._
import org.orbeon.saxon.value.DateTimeValue
import java.util.Date
import com.mongodb.casbah.gridfs.GridFS

/**
 * Experimental: Form Runner MongoDB persistence layer implementation.
 *
 * Supports:
 *
 * o storing and retrieving XML data
 * o storing and retrieving data attachments
 * o searching: all, keyword, and structured
 *
 * Known issues
 *
 * o does not support storing and retrieving forms
 * o does not support storing and retrieving form attachments
 * o reusing connection to MongoDB (opens/closes at each request)
 */
class MongoDBPersistence extends HttpServlet {

    private val DOCUMENT_ID_KEY = "_orbeon_document_id"
    private val LAST_UPDATE_KEY = "_orbeon_last_update"
    private val XML_KEY = "_orbeon_xml"
    private val KEYWORDS_KEY = "_orbeon_keywords"

    private val FormPath = """.*/crud/([^/]+)/([^/]+)/form/([^/]+)""".r
    private val DataPath = """.*/crud/([^/]+)/([^/]+)/data/([^/]+)/([^/]+)""".r
    private val SearchPath = """.*/search/([^/]+)/([^/]+)/?""".r

    // Put document or attachment
    override def doPut(req: HttpServletRequest, resp: HttpServletResponse) {
        req.getPathInfo match {
            case DataPath(app, form, documentId, "data.xml") =>
                putDocument(app, form, documentId, req.getInputStream)
            case DataPath(app, form, documentId, attachmentName) =>
                putAttachment(app, form, documentId, attachmentName, req)
            case FormPath(app, form, documentId) =>
                // TODO
            case _ => throw new ServletException
        }
    }

    // Get document or attachment
    override def doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        req.getPathInfo match {
            case DataPath(app, form, documentId, "data.xml") =>
                retrieveDocument(app, form, documentId, resp)
            case DataPath(app, form, documentId, attachmentName) =>
                retrieveAttachment(app, form, documentId, attachmentName, resp)
            case FormPath(app, form, documentId) =>
                // TODO
            case _ => throw new ServletException
        }
    }

    // Search
    override def doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        req.getPathInfo match {
            case SearchPath(app, form) =>
                val searchDoc = TransformerUtils.readDom4j(req.getInputStream, null, false, false)
                search(app, form, searchDoc, resp)
            case _ => throw new ServletException
        }
    }

    def search(app: String, form: String, searchDoc: Document, resp: HttpServletResponse) {

//        println(Dom4jUtils.domToPrettyString(searchDoc))

        // Extract search parameters
        val root = searchDoc.getRootElement

        val pageSize = root.element("page-size").getText.toInt
        val pageNumber = root.element("page-number").getText.toInt

        val searchElem = Dom4jUtils.elements(root, "query")
        val fullQuery = searchElem.head.getText.trim

        // Create search iterator depending on type of search
        withCollection(app, form) { coll =>
            val find =
                if (searchElem forall (_.getText.trim.isEmpty)) {
                    // Return all
                    coll.find
                } else if (fullQuery.nonEmpty) {
                    // Keyword search
                    coll.find(MongoDBObject(KEYWORDS_KEY -> fullQuery))
                } else {
                    // Structured search
                    coll.find(MongoDBObject(searchElem.tail filter (_.getText.trim.nonEmpty) map (e => (e.attributeValue("name") -> e.getText)) toList))
                }

            // Run search with sorting/paging
            val resultsToSkip = (pageNumber - 1) * pageSize
            val rows = find.sort(MongoDBObject(LAST_UPDATE_KEY -> -1)).skip(resultsToSkip).limit(pageSize).toSeq

            // Create and output result
            val result =
                <documents total={rows.size.toString} page-size={pageSize.toString} page-number={pageNumber.toString} query={fullQuery}>{
                    rows map { o =>
                        val created = DateTimeValue.fromJavaDate(new Date(o.get("_id").asInstanceOf[ObjectId].getTime)).getCanonicalLexicalRepresentation.toString
                        <document created={created} last-modified={o.get(LAST_UPDATE_KEY).toString} name={o.get(DOCUMENT_ID_KEY).toString}>
                            <details>{
                                searchElem.tail map { e =>
                                    <detail>{o.get(e.attributeValue("name"))}</detail>
                                }
                            }</details>
                        </document>
                    }
                }</documents>

            resp.setContentType("application/xml")
            ScalaUtils.useAndClose(new OutputStreamWriter(resp.getOutputStream)) {
    //            println(result.toString)
                osw => osw.write(result.toString)
            }
        }
    }

    def putDocument(app: String, form: String, documentId: String, inputStream: InputStream) {

        // Use MongoDB ObjectID as that can serve as timestamp for creation
        val builder = MongoDBObject.newBuilder
        builder += (DOCUMENT_ID_KEY -> documentId)
        builder += (LAST_UPDATE_KEY -> DateTimeValue.getCurrentDateTime(null).getCanonicalLexicalRepresentation.toString)

        // Create one entry per leaf, XML doc and keywords
        val doc = Dom4jUtils.readDom4j(inputStream, null, XMLUtils.ParserConfiguration.PLAIN)
        val keywords = collection.mutable.Set[String]()
        Dom4jUtils.visitSubtree(doc.getRootElement, new Dom4jUtils.VisitorListener {
            def startElement(element: Element) {
                if (!element.hasMixedContent) {
                    val text = element.getText
                    builder += (element.getName -> text)
                    if (text.trim.nonEmpty)
                        keywords ++= text.split("""\s+""")
                }
            }

            def endElement(element: Element) {}
            def text(text: Text) {}
        })

        builder += (XML_KEY -> Dom4jUtils.domToString(doc))
        builder += (KEYWORDS_KEY -> keywords.toArray)

        // Create or update
        withCollection(app, form) {
            _.update(MongoDBObject(DOCUMENT_ID_KEY -> documentId), builder.result, upsert = true, multi = false)
        }
    }

    def retrieveDocument(app: String, form: String, documentId: String, resp: HttpServletResponse) {
        withCollection(app, form) { coll =>
            coll.findOne(MongoDBObject(DOCUMENT_ID_KEY -> documentId)) match {
                case Some(result: DBObject) =>
                    result(XML_KEY) match {
                        case xml: String =>
                            resp.setContentType("application/xml")
                            ScalaUtils.useAndClose(new OutputStreamWriter(resp.getOutputStream)) {
                                osw => osw.write(xml)
                            }
                        case _ => resp.setStatus(404)
                    }
                case _ => resp.setStatus(404)
            }
        }
    }

    def putAttachment(app: String, form: String, documentId: String, name: String, req: HttpServletRequest) {
        withFS {
            _(req.getInputStream) { fh =>
                fh.filename = Seq(app, form, documentId, name) mkString "/"
                fh.contentType = Option(req.getContentType) getOrElse "application/octet-stream"
            }
        }
    }

    def retrieveAttachment(app: String, form: String, documentId: String, name: String, resp: HttpServletResponse) {
        withFS {
            _.findOne(Seq(app, form, documentId, name) mkString "/") match {
                case Some(dbFile) =>
                    resp.setContentType(dbFile.contentType)
                    ScalaUtils.copyStream(dbFile.inputStream, resp.getOutputStream)
                case _ => resp.setStatus(404)
            }
        }
    }

    def withDB(t: (MongoDB) => Any) {
        val mongoConnection = MongoConnection()
        try {
            t(mongoConnection("orbeon"))
        } finally {
            mongoConnection.close()
        }
    }

    def withCollection(app: String, form: String)(t: (MongoCollection) => Any) {
        withDB { db => t(db(app + '.' + form))}
    }

    def withFS(t: (GridFS) => Any) {
        withDB { db => t(GridFS(db))}
    }
}