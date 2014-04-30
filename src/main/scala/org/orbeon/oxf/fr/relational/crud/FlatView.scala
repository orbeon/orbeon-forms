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

    val MetadataColumns         = List("document_id", "created", "last_modified_time", "last_modified_by") map (_.toUpperCase)
    val PrefixedMetadataColumns = MetadataColumns map (col ⇒ s"METADATA_$col")
    val MetadataPairs           = MetadataColumns zip PrefixedMetadataColumns
    val MaxNameLength           = 30
    val TablePrefix             = "ORBEON_F_"

    // Create a flat relational view. See related issues:
    //
    // - https://github.com/orbeon/orbeon-forms/issues/1069
    // - https://github.com/orbeon/orbeon-forms/issues/1571
    def createFlatView(req: Request, connection: Connection): Unit = {

        val appXML  = xmlToSQLId(req.app)
        val formXML = xmlToSQLId(req.form)

        val tableName = TablePrefix + joinParts(appXML, formXML, MaxNameLength - TablePrefix.length)
        val userCols  = extractPathsCols(xmlDocument()) map { case (path, col) ⇒ s"extractValue(xml, '/*/$path')" → col }
        val allCols   = MetadataPairs.iterator ++ userCols

        // NOTE: Generate app/form name in SQL, as Oracle doesn't allow bind variables for data definition operations
        val ps = connection.prepareStatement(
            s"""|create or replace view $tableName as
                |select
                |    ${allCols map { case (col, name) ⇒ col + " " + name } mkString ", "}
                |from (
                |    select d.*, dense_rank() over (partition by document_id order by last_modified_time desc) as latest
                |    from orbeon_form_data d
                |    where
                |        app = '${escapeSQL(req.app)}'
                |        and form = '${escapeSQL(req.form)}'
                |)
                |where latest = 1 and deleted = 'N'
                |""".stripMargin)

        ps.executeUpdate()
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
