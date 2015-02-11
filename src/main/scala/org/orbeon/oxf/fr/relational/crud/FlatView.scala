/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.relational.crud

import java.sql.Connection
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.saxon.om.{NodeInfo, DocumentInfo}
import org.orbeon.scaxon.XML._
import scala.annotation.tailrec
import scala.collection.mutable
import RequestReader._

private object FlatView {

    case class Col(extractExpression: String, colName: String)
    val MetadataPairs           =
        List("document_id", "created", "last_modified_time", "last_modified_by")
                .map(_.toUpperCase)
                .map(col ⇒ Col(s"d.$col", s"METADATA_$col"))
    val PrefixedMetadataColumns = MetadataPairs.map{case Col(_, colName) ⇒ colName}
    val MaxNameLength           = 30
    val TablePrefix             = "ORBEON_F_"

    // Create a flat relational view. See related issues:
    //
    // - https://github.com/orbeon/orbeon-forms/issues/1069
    // - https://github.com/orbeon/orbeon-forms/issues/1571
    def createFlatView(req: Request, connection: Connection): Unit = {

        val viewName = {
            val appXML  = xmlToSQLId(req.app)
            val formXML = xmlToSQLId(req.form)
            TablePrefix + joinParts(appXML, formXML, MaxNameLength - TablePrefix.length)
        }

        // Delete view if it exists
        // - Only for DB2 and postgresql; on Oracle we can use "OR REPLACE" when creating the view.
        if (Set("db2", "postgresql")(req.provider)) {
            val viewExists = {
                val query = req.provider match{
                    case "db2"        ⇒ s"""|SELECT *
                                             |  FROM SYSIBM.SYSVIEWS
                                             | WHERE      creator =  (SELECT current_schema
                                             |                          FROM SYSIBM.SYSDUMMY1)
                                             |       AND  name    = ?
                                             |""".stripMargin
                    case "postgresql" ⇒ s"""|SELECT *
                                             |  FROM      pg_catalog.pg_class c
                                             |       JOIN pg_catalog.pg_namespace n
                                             |         ON n.oid = c.relnamespace
                                             | WHERE      n.nspname = current_schema
                                             |       AND  c.relkind = 'v'
                                             |       AND  upper(c.relname) = ?
                                             |""".stripMargin
                    case _            ⇒ ???
                }

                val ps = connection.prepareStatement(query)
                ps.setString(1, viewName)
                val rs = ps.executeQuery()
                rs.next()
            }
            if (viewExists)
                connection.prepareStatement(s"DROP VIEW $viewName").executeUpdate()
        }

        // Computer columns in the view
        val cols = {
            val userCols  = extractPathsCols(xmlDocument()) map { case (path, col) ⇒
                val extractFunction = req.provider match {
                    case "oracle"     ⇒ s"extractValue(d.xml, '/*/$path')"
                    case "db2"        ⇒ s"XMLSERIALIZE(XMLQUERY('$$XML/*/$path/text()') AS VARCHAR(4000))"
                    case "postgresql" ⇒ s"(xpath('/*/$path/text()', d.xml))[1]::text"
                    case _            ⇒ ???
                }
                Col(extractFunction, col)
            }
            MetadataPairs.iterator ++ userCols
        }

        // Create view
        // - Generate app/form name in SQL, as Oracle doesn't allow bind variables for data definition operations.
        locally {
            val query =
                s"""|CREATE  ${if (Set("oracle", "postgresql")(req.provider)) "OR REPLACE" else ""} VIEW $viewName AS
                    |SELECT  ${cols map { case Col(col, name) ⇒ col + " " + name} mkString ", "}
                    |  FROM  orbeon_form_data d,
                    |        (
                    |            SELECT   max(last_modified_time) last_modified_time,
                    |                     app, form, document_id
                    |              FROM   orbeon_form_data d
                    |             WHERE       app   = '${escapeSQL(req.app)}'
                    |                     AND form  = '${escapeSQL(req.form)}'
                    |                     AND draft = 'N'
                    |            GROUP BY app, form, document_id
                    |        ) m
                    | WHERE      d.last_modified_time = m.last_modified_time
                    |        AND d.app                = m.app
                    |        AND d.form               = m.form
                    |        AND d.document_id        = m.document_id
                    |        AND d.deleted            = 'N'
                    |""".stripMargin
            val ps = connection.prepareStatement(query)
            ps.executeUpdate()
        }
    }

    def collectControls(document: DocumentInfo): Iterator[(NodeInfo, NodeInfo)] = {

        import FormRunner._

        def topLevelSections =
            document descendant (FR → "section") filter (findAncestorContainers(_).isEmpty)

        def descendantControls(container: NodeInfo) =
            container descendant * filter
                (e ⇒ isIdForControl(e.id))

        def isDirectLeafControl(control: NodeInfo) =
            ! IsContainer(control) && findAncestorRepeats(control).isEmpty && findAncestorSections(control).size <= 1

        for {
            topLevelSection ← topLevelSections.to[Iterator]
            control         ← descendantControls(topLevelSection)
            if isDirectLeafControl(control)
        } yield
           topLevelSection → control
    }

    def extractPathsCols(document: DocumentInfo): Iterator[(String, String)] = {

        import FormRunner._

        val seen = mutable.HashSet[String](PrefixedMetadataColumns: _*)

        for {
            (section, control) ← collectControls(document)
            sectionName        = controlNameFromId(section.id)
            controlName        = controlNameFromId(control.id)
            path               = sectionName + "/" + controlName
            col                = joinParts(xmlToSQLId(sectionName), xmlToSQLId(controlName), MaxNameLength)
            uniqueCol          = resolveDuplicate(col, MaxNameLength)(seen)
        } yield
            path → uniqueCol
    }

    def resolveDuplicate(value: String, maxLength: Int)(seen: mutable.HashSet[String]): String = {

        @tailrec def nextValue(value: String, counter: Int = 1): String = {

            val guessCounterString = counter.toString
            val guess = s"${value take (maxLength - guessCounterString.length)}$guessCounterString"

            if (! seen(guess))
                guess
            else
                nextValue(value, counter + 1)
        }

        val cleanValue =
            if (! seen(value))
                value
            else
                nextValue(value)

        seen += cleanValue

        cleanValue
    }

    // Create an acceptable SQL name from an XML NCName (but accept any input)
    // On Oracle names: http://docs.oracle.com/cd/E11882_01/server.112/e10592/sql_elements008.htm
    def xmlToSQLId(id: String) =
        id
        .replaceAllLiterally("-", "_")    // dash to underscore
        .toUpperCase                      // to uppercase
        .replaceAll("""[^A-Z0-9_]""", "") // only keep alphanumeric or underscore
        .dropWhile(_ == '_')              // remove starting underscores
        .reverse
        .dropWhile(_ == '_')              // remove trailing underscores
        .reverse

    // Try to truncate reasonably smartly when needed to maximize the characters we keep
    def fitParts(left: String, right: String, max: Int) = {
        val usable = max - 1
        val half   = usable / 2

        if (left.size + right.size <= usable)            left                           →  right
        else if (left.size > half && right.size > half) (left take half)                → (right take half)
        else if (left.size > half)                      (left take usable - right.size) →  right
        else                                             left                           → (right take usable - left.size)
    }

    def joinParts(left: String, right: String, max: Int) =
        fitParts(left, right, max).productIterator mkString "_"

    def escapeSQL(s: String) =
        s.replaceAllLiterally("'", "''")
}
