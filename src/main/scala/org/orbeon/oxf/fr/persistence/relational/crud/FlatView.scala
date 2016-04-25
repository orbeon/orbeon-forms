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
package org.orbeon.oxf.fr.persistence.relational.crud

import java.sql.Connection

import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.persistence.relational.crud.RequestReader._
import org.orbeon.oxf.fr.persistence.relational.{DB2, Oracle, PostgreSQL}
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML._

import scala.annotation.tailrec
import scala.collection.mutable

private object FlatView {

  val SupportedProviders = Set(Oracle, DB2, PostgreSQL)

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
      TablePrefix + joinParts(List(appXML, formXML), MaxNameLength - TablePrefix.length)
    }

    // Delete view if it exists
    // - Only for DB2 and postgresql; on Oracle we can use "OR REPLACE" when creating the view.
    if (Set(DB2, PostgreSQL)(req.provider)) {
      val viewExists = {
        val query = req.provider match{
          case DB2          ⇒ s"""|SELECT *
                                  |  FROM SYSIBM.SYSVIEWS
                                  | WHERE      creator =  (SELECT current_schema
                                  |                          FROM SYSIBM.SYSDUMMY1)
                                  |       AND  name    = ?
                                  |""".stripMargin
          case PostgreSQL   ⇒ s"""|SELECT *
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

    // Compute columns in the view
    val cols = {
      val userCols  = extractPathsCols(xmlDocument()) map { case (path, col) ⇒
        val extractFunction = req.provider match {
          case Oracle     ⇒ s"extractValue(d.xml, '/*/$path')"
          case DB2        ⇒ s"XMLCAST(XMLQUERY('$$XML/*/$path/text()') AS VARCHAR(4000))"
          case PostgreSQL ⇒ s"(xpath('/*/$path/text()', d.xml))[1]::text"
          case _          ⇒ ???
        }
        Col(extractFunction, col)
      }
      MetadataPairs.iterator ++ userCols
    }

    // Create view
    // - Generate app/form name in SQL, as Oracle doesn't allow bind variables for data definition operations.
    locally {
      val query =
        s"""|CREATE  ${if (Set(Oracle, PostgreSQL)(req.provider)) "OR REPLACE" else ""} VIEW $viewName AS
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

  // Returns a list with for each control to be included in the flat view, the parts of the path
  // to that control, e.g.:
  //
  //     List(
  //         List("top-section", "sub-section", "control-a"),
  //         List("top-section", "sub-section", "control-b")
  //     )
  def collectControlPaths(outerSectionNames: List[String], document: DocumentInfo): List[List[String]] = {

    import FormRunner._

    val root = document.rootElement
    val head = root.child(XH → "head").head
    val body = root.child(XH → "body").head
    val xblMappings = sectionTemplateXBLBindingsByURIQualifiedName(head / XBLXBLTest)


    // `outerSectionNames` lists the name of the ancestor sections of the current node,
    // e.g. List("top-section", "sub-section")
    def collectFromNode(outerSectionNames: List[String],  node: NodeInfo): List[List[String]] = {

      def isControl(n: NodeInfo) = isIdForControl(node.id)
      def collectFromChildren(sectionNames: List[String]) = {
        val children = node child *
        children.toList.flatMap(collectFromNode(sectionNames, _))
      }

      node match {
        case _ if isRepeat(node) ⇒
          // Don't go into repeats, as we don't them yet for flat views
          Nil
        case _ if IsSection(node) ⇒
          val sectionNames = outerSectionNames :+ controlNameFromId(node.id)
          collectFromChildren(sectionNames)
        case _ if IsSectionTemplateContent(node) ⇒
          xblMappings.get(node.uriQualifiedName) match {
            case None ⇒
              Nil
            case Some(xblBindingNode) ⇒
              val xblTemplate = xblBindingNode.rootElement.child(XBLTemplateTest).head
              collectFromNode(outerSectionNames, xblTemplate)
          }
        case _ if isControl(node) ⇒
          List(outerSectionNames :+ controlNameFromId(node.id))
        case _ ⇒
          collectFromChildren(outerSectionNames)
      }
    }

    collectFromNode(Nil, body)
  }

  def extractPathsCols(document: DocumentInfo): List[(String, String)] = {

    val paths = collectControlPaths(Nil, document)
    val seen = mutable.HashSet[String](PrefixedMetadataColumns: _*)

    for {
      path: List[String]  ← paths
      sqlPath            = path map xmlToSQLId
      col                = joinParts(sqlPath, MaxNameLength)
      uniqueCol          = resolveDuplicate(col, MaxNameLength)(seen)
    } yield
      path.mkString("/") → uniqueCol
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
  def fitParts(parts: List[String], max: Int): List[String] = {

    val usable = max - parts.length + 1

    @tailrec def shaveParts(parts: List[String]): List[String] = {
      val partsLength = parts.map(_.length)
      val totalLength = partsLength.sum

      if (totalLength <= usable) {
        // Parts fit as is; we're good
        parts
      } else {
        // Parts don't fit; find the longest part
        val maxPartLength = partsLength.max
        val maxPartIndex  = partsLength.indexOf(maxPartLength)
        // Shave the longest part by 1, removing possible '_' leftovers at the end
        val newPart       = parts(maxPartIndex)
                            .dropRight(1)
                            .reverse.dropWhile(_ == '_').reverse
        val newParts      = parts.updated(maxPartIndex, newPart)
        // See if we're good now
        shaveParts(newParts)
      }
    }

    shaveParts(parts)
  }

  def joinParts(parts: List[String], max: Int) =
    fitParts(parts, max) mkString "_"

  def escapeSQL(s: String) =
    s.replaceAllLiterally("'", "''")
}
