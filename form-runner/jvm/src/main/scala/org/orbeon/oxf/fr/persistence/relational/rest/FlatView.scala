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
package org.orbeon.oxf.fr.persistence.relational.rest

import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath.*

import java.sql.Connection
import scala.annotation.tailrec
import scala.collection.mutable


private object FlatView {

  val CompatibilityMaxIdentifierLength = 30

  case class Column(expression: String, nameOpt: Option[String]) {
    def sql: String = expression + nameOpt.map(name => " " + xmlToSQLId(name)).getOrElse("")
  }

  private val TablePrefix        = "orbeon_f_"
  private val FormDataTableAlias = "d"

  private def metadataColumns(tableAlias: String, includeAll: Boolean, includeAlias: Boolean): Seq[Column] =
    ("document_id" :: (if (includeAll) List("created", "last_modified_time", "last_modified_by") else Nil))
      .map(name => Column(s"$tableAlias.$name", if (includeAlias) Some(s"metadata_$name") else None))

  def deleteViewIfExists(provider: Provider, connection: Connection, viewName: String): Unit = {
    val viewExists = {
      useAndClose(connection.prepareStatement(Provider.flatViewExistsQuery(provider))) { ps =>
        ps.setString(1, Provider.flatViewExistsParam(provider, viewName))
        useAndClose(ps.executeQuery())(_.next())
      }
    }
    if (viewExists)
      useAndClose(connection.prepareStatement(s"DROP VIEW $viewName"))(_.executeUpdate())
  }

  // Create a flat relational view. See related issues:
  //
  // - https://github.com/orbeon/orbeon-forms/issues/1069
  // - https://github.com/orbeon/orbeon-forms/issues/1571
  def createFlatViews(
    req                : CrudRequest,
    reqBodyOpt         : Option[RequestReader.Body],
    version            : Int,
    connection         : Connection,
    fullyQualifiedNames: Boolean,
    maxIdentifierLength: Int
  )(implicit
    externalContext    : ExternalContext
  ): Unit =
    RequestReader.xmlDocument(reqBodyOpt).foreach { documentInfo =>
      createFlatViewsForDocument(req, version, connection, documentInfo, fullyQualifiedNames, maxIdentifierLength)
    }

  def createFlatViewsForDocument(
    req                : CrudRequest,
    version            : Int,
    connection         : Connection,
    documentInfo       : DocumentInfo,
    fullyQualifiedNames: Boolean,
    maxIdentifierLength: Int
  ): Unit =
    FlatView.views(documentInfo, req.provider, req.appForm, version).foreach { view =>
      val viewName = view.name(fullyQualifiedNames, maxIdentifierLength)

      if (Provider.flatViewDelete(req.provider)) {
        deleteViewIfExists(req.provider, connection, viewName)
      }

      val viewSelectQuery = view.sql(fullyQualifiedNames, maxIdentifierLength)
      val createViewQuery = Provider.flatViewCreateView(req.provider, viewName, viewSelectQuery)

      useAndClose(connection.prepareStatement(createViewQuery))(_.executeUpdate())
    }

  sealed trait FormNode {
    def nameOpt : Option[String]
    def path    : List[String]
    def repeated: Boolean
    def children: List[FormNode]
  }

  case class Root(children: List[FormNode]) extends FormNode {
    override val nameOpt : Option[String] = None
    override val path    : List[String]   = Nil
    override val repeated: Boolean        = false
  }

  case class Section(
    nameOpt : Option[String],
    path    : List[String],
    repeated: Boolean,
    children: List[FormNode]
  ) extends FormNode

  case class Grid(
    nameOpt : Option[String],
    path    : List[String],
    repeated: Boolean,
    children: List[FormNode]
  ) extends FormNode

  case class Control(
    nameOpt               : Option[String],
    path                  : List[String],
    templateSectionNameOpt: Option[String]
  ) extends FormNode {
    override val repeated: Boolean        = false
    override val children: List[FormNode] = Nil
  }

  def formTree(document: DocumentInfo): Root = {

    val root        = document.rootElement
    val head        = root.child(XHHeadTest).head
    val body        = root.child(XHBodyTest).head
    val xblMappings = sectionTemplateXBLBindingsByURIQualifiedName(head / XBLXBLTest)

    def formNodes(node: NodeInfo, path: List[String], templateSectionNameOpt: Option[String]): List[FormNode] = {

      val section  = IsSection(node)
      val grid     = IsGrid(node)
      val repeated = isRepeat(node)
      val control  = isIdForControl(node.id)

      // Do not include grid names in paths, unless they're repeated grids
      val addNameToPath          = ! grid || repeated
      val nameOpt                = Option(controlNameFromId(node.id))
      val nameToAddToPathOpt     = addNameToPath.flatOption(nameOpt)
      val addIterationNameToPath = section && repeated
      val iterationNameOpt       = addIterationNameToPath.flatOption(nameToAddToPathOpt.map(_ + DefaultIterationSuffix))
      val currentPath            = path ++ nameToAddToPathOpt.toList ++ iterationNameOpt.toList
      val childrenNodes          = (node child *).toList.flatMap(formNodes(_, currentPath, templateSectionNameOpt))

      if (section) {
        List(Section(nameOpt, currentPath, repeated, childrenNodes))
      } else if (grid) {
        List(Grid(nameOpt, currentPath, repeated, childrenNodes))
      } else if (isSectionTemplateContent(node)) {
        xblMappings.get(node.uriQualifiedName).map { xblBindingNode =>
          // Exclude template content nodes (*-content) from the path
          val newPath                = currentPath.filterNot(_.endsWith(TemplateContentSuffix))
          val templateSectionNameOpt = newPath.lastOption

          // Follow XBL binding to the template
          formNodes(
            node                   = xblBindingNode.rootElement.child(XBLTemplateTest).head,
            path                   = newPath,
            templateSectionNameOpt = templateSectionNameOpt
          )
        }.getOrElse(Nil)
      } else if (control) {
        List(Control(nameOpt, currentPath, templateSectionNameOpt))
      } else {
        childrenNodes
      }
    }

    Root(children = formNodes(body, path = Nil, templateSectionNameOpt = None))
  }

  private def relativePath(fullPath: Seq[String], referencePath: Seq[String]): Seq[String] = {
    assert(fullPath.take(referencePath.size) == referencePath, "Path prefixes do not match")
    fullPath.drop(referencePath.size)
  }

  case class ViewControl(name: String, relativePath: Seq[String], templateSectionNameOpt: Option[String]) {
    def columnNamePath(fullyQualifiedNames: Boolean): Seq[String] =
      if (fullyQualifiedNames)
        relativePath
      else
        templateSectionNameOpt.toSeq :+ name

    def xpath: String =
      "/*/" + relativePath.mkString("/") + "/text()"
  }

  case class View(
    formNode     : FormNode,
    parentViewOpt: Option[View],
    provider     : Provider,
    appForm      : AppForm,
    version      : Int,
    controls     : Seq[ViewControl]
  ) {

    private def level: Int = parentViewOpt.map(_.level + 1).getOrElse(0)
    private val tableAlias = s"view_$level"

    private val relativePath: Seq[String] = parentViewOpt match {
      case None         => formNode.path
      case Some(parent) => FlatView.relativePath(formNode.path, parent.formNode.path)
    }

    private val repetitionColumnNameOpt: Option[String] = formNode.nameOpt.map(name => xmlToSQLId(name + "_repetition"))
    private val repetitionColumnNames: Seq[String]      = parentViewOpt.toSeq.flatMap(_.repetitionColumnNames) ++ repetitionColumnNameOpt.toSeq

    def name(fullyQualifiedNames: Boolean, maxIdentifierLength: Int): String = {

      val viewParts = if (fullyQualifiedNames) {
        // Include all segments from path relative to parent view (if any)
        relativePath
      } else {
        // Only include name of section/grid
        formNode.nameOpt.toSeq
      }

      TablePrefix + joinParts(
        (List(appForm.app, appForm.form, version.toString) ++ viewParts).map(xmlToSQLId),
        maxIdentifierLength - TablePrefix.length
      )
    }

    def sql(fullyQualifiedNames: Boolean, maxIdentifierLength: Int): String = {

      val deduplicatedColumnNames = FlatView.deduplicatedColumnNames(
        controls.map(_.columnNamePath(fullyQualifiedNames)),
        maxIdentifierLength
      )

      val controlColumns = controls.map { control =>
        val columnName = deduplicatedColumnNames(control.columnNamePath(fullyQualifiedNames))

        Column(Provider.flatViewExtractFunction(provider, tableAlias, control.xpath), Some(columnName))
      }

      sql(
        columns              = controlColumns.map(_.sql),
        xmlTable             = "",
        includeAllMetadata   = parentViewOpt.isEmpty,
        includeMetadataAlias = true
      )
    }

    private def sql(
      columns             : Seq[String],
      xmlTable            : String,
      includeAllMetadata  : Boolean,
      includeMetadataAlias: Boolean
    ): String = {

      val (fromStatement, repetitionColumns) = parentViewOpt match {
        case None =>
          val metadataColumns = FlatView.metadataColumns(
            tableAlias   = FormDataTableAlias,
            includeAll   = includeAllMetadata,
            includeAlias = false
          )

          // Top-level query on orbeon_form_data table
          val fromStatement = Provider.flatViewFormDataSelectStatement(
            appForm    = appForm,
            version    = version,
            tableAlias = FormDataTableAlias,
            columns    = metadataColumns.map(_.sql) :+ s"$FormDataTableAlias.xml extracted_xml"
          )

          (fromStatement, Seq())

        case Some(parentView) =>
          // In PostgreSQL, we extract single and multiple XML nodes/values using a single 'xpath' function. We can
          // then "unnest" that array to get individual rows, in the "SELECT" part of the SQL query. In Oracle, SQL
          // Server, and DB2, we will instead explicitly generate an "XML table", in the "FROM" part of the SQL query.
          // This means that the row_number() function will have to be called at different levels of the SQL query
          // depending on whether we're unnesting an array or using an XML table.

          // Repetition columns logic
          val canUnnestArray     = Provider.flatViewCanUnnestArray(provider)
          val docIdTableAlias    = if (canUnnestArray) tableAlias else parentView.tableAlias
          val docIdColumn        = s"$docIdTableAlias.document_id"
          val partitionBy        = s"PARTITION BY ${(docIdColumn +: parentView.repetitionColumnNames).mkString(", ")}"
          val orderBy            = Provider.flatViewRowNumberOrderBy(provider)
          val rowNumberStatement = s"row_number() OVER ($partitionBy $orderBy) AS ${repetitionColumnNameOpt.get}"
          val rowNumberInParent  = ! canUnnestArray
          val repetitionColumn   = if (rowNumberInParent) repetitionColumnNameOpt.get else rowNumberStatement
          val repetitionColumns  = parentView.repetitionColumnNames :+ repetitionColumn

          // XML XPath extraction logic (repeated sections/grids)
          val xpath           = s"/*/${relativePath.mkString("/")}"
          val parentXmlColumn = Provider.flatViewRepeatedXmlColumn(provider, parentView.tableAlias, xpath)
          val parentXmlTable  = Provider.flatViewRepeatedXmlTable (provider, parentView.tableAlias, xpath)
          val parentColumns   = Seq(parentXmlColumn) ++ rowNumberInParent.seq(rowNumberStatement)

          val fromStatement = parentView.sql(
            columns              = parentColumns,
            xmlTable             = parentXmlTable,
            includeAllMetadata   = includeAllMetadata,
            includeMetadataAlias = false
          )

          (fromStatement, repetitionColumns)
      }

      val metadataColumns = FlatView.metadataColumns(
        tableAlias   = tableAlias,
        includeAll   = includeAllMetadata,
        includeAlias = includeMetadataAlias
      ).map(_.sql)

      s"""|SELECT
          |  ${(metadataColumns ++ repetitionColumns ++ columns).mkString(", ")}
          |FROM
          |  (
          |    $fromStatement
          |  ) $tableAlias$xmlTable
          |""".stripMargin
    }
  }

  def viewControls(formNode: FormNode, referencePath: List[String]): Seq[ViewControl] =
    formNode match {
      case _: Section | _: Grid if formNode.repeated =>
        // Stop collecting controls for a view when we encounter a repeated section/grid, which will be covered by a separate view
        Seq.empty
      case Control(name, path, templateSectionNameOpt) =>
        // Make path relative to the reference path (i.e. root or repeated section/grid)
        Seq(ViewControl(name.get, FlatView.relativePath(path, referencePath), templateSectionNameOpt))
      case _ =>
        formNode.children.flatMap(viewControls(_, referencePath))
    }

  def views(
    formNode     : FormNode,
    parentViewOpt: Option[View],
    provider     : Provider,
    appForm      : AppForm,
    version      : Int
  ): Seq[View] = {

    // We generate a view for the root of the form, as well as for every repeated section/grid (except with MySQL)
    val generateView = formNode match {
      case _: Root              => true
      case _: Section | _: Grid => formNode.repeated && (provider != Provider.MySQL)
      case _                    => false
    }

    val viewOpt = generateView.option {
      // Collect all controls under the current view
      val controls = formNode.children.flatMap(viewControls(_, formNode.path))

      View(formNode, parentViewOpt, provider, appForm, version, controls)
    }

    // Propagate any currently active view towards the child views (serves as a reference)
    val childParentViewOpt = viewOpt orElse parentViewOpt

    viewOpt.toSeq ++ formNode.children.flatMap(views(_, childParentViewOpt, provider, appForm, version))
  }

  def views(document: DocumentInfo, provider: Provider, appForm: AppForm, version: Int): Seq[View] =
    views(formNode = formTree(document), parentViewOpt = None, provider, appForm, version)

  def deduplicatedColumnNames(columnNamesAsPaths: Seq[Seq[String]], maxIdentifierLength: Int): Map[Seq[String], String] = {

    val seen  = mutable.HashSet[String](
      metadataColumns(tableAlias = FormDataTableAlias, includeAll = true, includeAlias = true).flatMap(_.nameOpt)*
    )

    (for {
      path: Seq[String]  <- columnNamesAsPaths
      sqlPath            = path map xmlToSQLId
      col                = joinParts(sqlPath, maxIdentifierLength)
      uniqueCol          = resolveDuplicate(col, maxIdentifierLength)(seen)
    } yield
      path -> uniqueCol).toMap
  }

  def resolveDuplicate(value: String, maxIdentifierLength: Int)(seen: mutable.HashSet[String]): String = {

    @tailrec def nextValue(value: String, counter: Int = 1): String = {

      val guessCounterString = counter.toString
      val guess = s"${value take (maxIdentifierLength - guessCounterString.length)}$guessCounterString"

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
  def xmlToSQLId(id: String): String =
    id
    .replace("-", "_")                   // dash to underscore
    .replaceAll("""[^A-Za-z0-9_]""", "") // only keep alphanumeric or underscore
    .dropWhile(_ == '_')                 // remove starting underscores
    .reverse
    .dropWhile(_ == '_')                 // remove trailing underscores
    .reverse

  // Try to truncate reasonably smartly when needed to maximize the characters we keep
  private def fitParts(parts: Seq[String], maxIdentifierLength: Int): Seq[String] = {

    val usable = maxIdentifierLength - parts.length + 1

    @tailrec def shaveParts(parts: Seq[String]): Seq[String] = {
      // The limit imposed by some databases is on the number of bytes, not characters, but we don't need to compute the
      // UTF-8-encoded length here as we limit identifiers to ASCII.
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

  def joinParts(parts: Seq[String], maxIdentifierLength: Int): String =
    fitParts(parts, maxIdentifierLength) mkString "_"
}
