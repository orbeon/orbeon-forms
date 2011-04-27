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
import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}
import java.io.{OutputStreamWriter, InputStream}
import javax.servlet.ServletException
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.saxon.value.DateTimeValue
import java.util.Date
import com.mongodb.casbah.gridfs.GridFS
import xml.NodeSeq._
import xml.{NodeSeq, Node, XML}

/*!# Experimental: Form Runner MongoDB persistence layer implementation.

 Supports:

 * storing and retrieving XML data
 * storing and retrieving data attachments
 * searching: all, keyword, and structured

 Known issues:

 * does not support storing and retrieving forms
 * does not support storing and retrieving form attachments
 * reusing connection to MongoDB (opens/closes at each request)
*/
class MongoDBPersistence extends HttpServlet {

    /*! MongoDB keys used for custom Orbeon fields */
    private val DOCUMENT_ID_KEY = "_orbeon_document_id"
    private val LAST_UPDATE_KEY = "_orbeon_last_update"
    private val XML_KEY = "_orbeon_xml"
    private val KEYWORDS_KEY = "_orbeon_keywords"

    /*! Regexp matching a form data path */
    private val DataPath = """.*/crud/([^/]+)/([^/]+)/data/([^/]+)/([^/]+)""".r
    /*! Regexp matching a form definition path */
    private val FormPath = """.*/crud/([^/]+)/([^/]+)/form/([^/]+)""".r
    /*! Regexp matching a search path */
    private val SearchPath = """.*/search/([^/]+)/([^/]+)/?""".r

    /*!## Servlet PUT entry point

      Store form data, form definition, or attachment.
     */
    override def doPut(req: HttpServletRequest, resp: HttpServletResponse) {
        req.getPathInfo match {
            case DataPath(app, form, documentId, "data.xml") =>
                storeDocument(app, form, documentId, req.getInputStream)
            case DataPath(app, form, documentId, attachmentName) =>
                storeAttachment(app, form, documentId, attachmentName, req)
            case FormPath(app, form, documentId) =>
                // TODO
            case _ => throw new ServletException
        }
    }

    /*!## Servlet GET entry point

      Retrieve form data, form definition, or attachment.
     */
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

    /*!## Servlet POST entry point

      Perform a search based on an incoming search specification in XML, and return search results in XML.
     */
    override def doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        req.getPathInfo match {
            case SearchPath(app, form) =>
                search(app, form, req, resp)
            case _ => throw new ServletException
        }
    }

    /*!## Store an XML document */
    def storeDocument(app: String, form: String, documentId: String, inputStream: InputStream) {

        // Use MongoDB ObjectID as that can serve as timestamp for creation
        val builder = MongoDBObject.newBuilder
        builder += (DOCUMENT_ID_KEY -> documentId)
        builder += (LAST_UPDATE_KEY -> DateTimeValue.getCurrentDateTime(null).getCanonicalLexicalRepresentation.toString)

        // Create one entry per leaf, XML doc and keywords
        val root = XML.load(inputStream)
        val keywords = collection.mutable.Set[String]()

        root \\ "_" filter (_ \ "_" isEmpty) map { e =>
            val text = e.text
            builder += (e.label -> text)
            if (text.trim.nonEmpty)
                keywords ++= text.split("""\s+""")
        }

        builder += (XML_KEY -> root.toString)
        builder += (KEYWORDS_KEY -> keywords.toArray)

        // Create or update
        withCollection(app, form) {
            _.update(MongoDBObject(DOCUMENT_ID_KEY -> documentId), builder.result, upsert = true, multi = false)
        }
    }

    /*!## Retrieve an XML document */
    def retrieveDocument(app: String, form: String, documentId: String, resp: HttpServletResponse) {
        withCollection(app, form) { coll =>
            coll.findOne(MongoDBObject(DOCUMENT_ID_KEY -> documentId)) match {
                case Some(result: DBObject) =>
                    result(XML_KEY) match {
                        case xml: String =>
                            resp.setContentType("application/xml")
                            useAndClose(new OutputStreamWriter(resp.getOutputStream)) {
                                osw => osw.write(xml)
                            }
                        case _ => resp.setStatus(404)
                    }
                case _ => resp.setStatus(404)
            }
        }
    }

    /*!## Store an attachment */
    def storeAttachment(app: String, form: String, documentId: String, name: String, req: HttpServletRequest) {
        withFS {
            _(req.getInputStream) { fh =>
                fh.filename = Seq(app, form, documentId, name) mkString "/"
                fh.contentType = Option(req.getContentType) getOrElse "application/octet-stream"
            }
        }
    }

    /*!## Retrieve an attachment */
    def retrieveAttachment(app: String, form: String, documentId: String, name: String, resp: HttpServletResponse) {
        withFS {
            _.findOne(Seq(app, form, documentId, name) mkString "/") match {
                case Some(dbFile) =>
                    resp.setContentType(dbFile.contentType)
                    copyStream(dbFile.inputStream, resp.getOutputStream)
                case _ => resp.setStatus(404)
            }
        }
    }

    /*!## Perform a search */
    def search(app: String, form: String, req: HttpServletRequest, resp: HttpServletResponse) {

        def elemValue(n: Node) = n.text.trim
        def attValue(n: Node, name: String) = n.attribute(name).get.text
        def intValue(n: NodeSeq) = n.head.text.toInt

        // Extract search parameters
        val root = XML.load(req.getInputStream)

        val pageSize = intValue(root \ "page-size")
        val pageNumber = intValue(root \ "page-number")

        val searchElem = root \ "query"
        val fullQuery = elemValue(searchElem.head)

        withCollection(app, form) { coll =>
            // Create search iterator depending on type of search
            val find =
                if (searchElem forall (elemValue(_) isEmpty)) {
                    // Return all
                    coll.find
                } else if (fullQuery.nonEmpty) {
                    // Keyword search
                    coll.find(MongoDBObject(KEYWORDS_KEY -> fullQuery))
                } else {
                    // Structured search: gather all non-empty <query name="$NAME">$VALUE</query>
                    coll.find(MongoDBObject(searchElem.tail filter (elemValue(_) nonEmpty) map (e => (attValue(e, "name") -> elemValue(e))) toList))
                }

            // Run search with sorting/paging
            val resultsToSkip = (pageNumber - 1) * pageSize
            val rows = find sort MongoDBObject(LAST_UPDATE_KEY -> -1) skip resultsToSkip limit pageSize

            // Create and output result
            val result =
                <documents total={rows.size.toString} page-size={pageSize.toString} page-number={pageNumber.toString}>{
                    rows map { o =>
                        val created = DateTimeValue.fromJavaDate(new Date(o.get("_id").asInstanceOf[ObjectId].getTime)).getCanonicalLexicalRepresentation.toString
                        <document created={created} last-modified={o.get(LAST_UPDATE_KEY).toString} name={o.get(DOCUMENT_ID_KEY).toString}>
                            <details>{
                                searchElem.tail map { e =>
                                    <detail>{o.get(attValue(e, "name"))}</detail>
                                }
                            }</details>
                        </document>
                    }
                }</documents>

            resp.setContentType("application/xml")
            useAndClose(new OutputStreamWriter(resp.getOutputStream)) {
                osw => osw.write(result.toString)
            }
        }
    }

    def withDB[T](t: (MongoDB) => T) {
        val mongoConnection = MongoConnection()
        try {
            t(mongoConnection("orbeon"))
        } finally {
            mongoConnection.close()
        }
    }

    def withCollection[T](app: String, form: String)(t: (MongoCollection) => T) {
        withDB { db => t(db(app + '.' + form))}
    }

    def withFS[T](t: (GridFS) => T) {
        withDB { db => t(GridFS(db))}
    }
}
