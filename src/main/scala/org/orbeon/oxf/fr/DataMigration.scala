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
package org.orbeon.oxf.fr

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML
import org.orbeon.scaxon.XML._

object DataMigration {

  // NOTE: We could build this with JSON objects instead
  def encodeMigrationsToJSON(migrations: Seq[(String, String)]) =
    migrations map { case (path, iterationName) ⇒
      s"""{ "path": "$path", "iteration-name": "$iterationName" }"""
    } mkString ("[", ",", "]")

  // Ouch, a lot of boilerplate! (Rapture JSON might provide nicer syntax)
  def decodeMigrationsFromJSON(jsonMigrationMap: String): List[(String, String)] = {

    import spray.json._
    val json = jsonMigrationMap.parseJson

    val names =
      Iterator(json) collect {
        case JsArray(migrations) ⇒
          migrations.iterator collect {
            case JsObject(fields) ⇒
              (
                fields("path").asInstanceOf[JsString].value,
                fields("iteration-name").asInstanceOf[JsString].value
              )
          }
      }

    names.flatten.toList
  }

  private val TrimPathElementRE = """\s*\(?([^)]+)\)?\s*""".r

  private def partitionNodes(
    mutableData : NodeInfo,
    migration   : List[(String, String)]
  ): List[(NodeInfo, List[NodeInfo], String, String)] =
    migration flatMap {
      case (path, iterationName) ⇒

        val (pathToParentNodes, pathToChildNodes) = {
          // NOTE: The format of the path is like `(section-3)/(section-3-iteration)/(grid-4)`. Form Builder
          // puts parentheses for the abandoned case of a custom XML format, and we kept that when producing
          // the migration data. As long as we know that there are no `/` within the parentheses we are fine.
          val parts                          = path.splitTo[List]("/")
          val TrimPathElementRE(trimmedLast) = parts.last

          (parts.init mkString "/", trimmedLast)
        }

        // NOTE: Use collect, but we know they are nodes if the JSON is correct and contains paths
        val parentNodes = XML.eval(mutableData.rootElement, pathToParentNodes) collect {
          case node: NodeInfo ⇒ node
        }

        parentNodes map { parentNode ⇒

          val nodes = XML.eval(parentNode, pathToChildNodes) collect {
            case node: NodeInfo ⇒ node
          }

          // NOTE: Should ideally test on uriQualifiedName instead. The data in practice has elements which
          // in no namespaces, and if they were in a namespace, the prefixes would likely be unique.
          (parentNode, nodes.to[List], pathToChildNodes, iterationName)
        }
    }

  import org.orbeon.oxf.xforms.action.XFormsAPI._

  //@XPathFunction
  def dataMaybeMigratedFrom(data: DocumentInfo, metadata: Option[DocumentInfo], pruneMetadata: Boolean): Option[DocumentInfo] =
    dataMaybeMigratedFromTo(data, metadata, migrateDataFrom(_, _, pruneMetadata))

  //@XPathFunction
  def dataMaybeMigratedTo(data: DocumentInfo, metadata: Option[DocumentInfo]): Option[DocumentInfo] =
    dataMaybeMigratedFromTo(data, metadata, migrateDataTo)

  private def dataMaybeMigratedFromTo(
    data     : DocumentInfo,
    metadata : Option[DocumentInfo],
    migrate  : (DocumentInfo, String) ⇒ DocumentInfo
  ): Option[DocumentInfo] =
    for {
      metadata  ← metadata
      migration ← migrationMapFromMetadata(metadata.rootElement)
    } yield
      migrate(data, migration)

  def migrationMapFromMetadata(metadataRootElement: NodeInfo) =
    metadataRootElement firstChild "migration" filter (_.attValue("version") == "4.8.0") map (_.stringValue)

  def migrateDataTo(data: DocumentInfo, jsonMigrationMap: String): DocumentInfo = {

    val mutableData = TransformerUtils.extractAsMutableDocument(data)

    partitionNodes(mutableData, decodeMigrationsFromJSON(jsonMigrationMap)) foreach {
      case (parentNode, iterations, repeatName, iterationName) ⇒

        iterations match {
          case Nil ⇒
            // Issue: we don't know, based just on the migration map, where to insert container elements to
            // follow bind order. This is not a new problem as we don't enforce order, see:
            //
            //     https://github.com/orbeon/orbeon-forms/issues/443.
            //
            // For now we choose to add after the last element.
            //
            // BTW at runtime `fr:grid[@repeat = 'true']` inserts iterations before the first element.
            insert(
              into       = parentNode,
              after      = parentNode / *,
              origin     = elementInfo(repeatName, Nil),
              doDispatch = false
            )
          case iterations ⇒

            val contentForEachIteration =
              iterations map (iteration ⇒ (iteration /@ @*) ++ (iteration / Node) toList) // force

            delete(iterations.head /@ @*,   doDispatch = false)
            delete(iterations.head / Node, doDispatch = false)
            delete(iterations.tail,        doDispatch = false)

            insert(
              into       = iterations.head,
              origin     = contentForEachIteration map (elementInfo(iterationName, _)),
              doDispatch = false
            )
        }
    }

    mutableData
  }

  def migrateDataFrom(data: DocumentInfo, jsonMigrationMap: String, pruneMetadata: Boolean): DocumentInfo = {

    val mutableData = TransformerUtils.extractAsMutableDocument(data)

    partitionNodes(mutableData, decodeMigrationsFromJSON(jsonMigrationMap)) foreach {
      case (_, iterations, _, _) ⇒

        assert(iterations.tail.isEmpty)
        val container = iterations.head

        val contentForEachIteration =
          (container / * toList) map (iteration ⇒ (iteration /@ @*) ++ (iteration / Node) toList) // force

        insert(
          after      = container,
          origin     = contentForEachIteration map (elementInfo(container.name, _)),
          doDispatch = false
        )

        delete(container, doDispatch = false)
    }

    if (pruneMetadata)
      pruneFormRunnerMetadataFromMutableData(mutableData)

    mutableData
  }

  //@XPathFunction
  def pruneFormRunnerMetadata(data: DocumentInfo): DocumentInfo = {
    val mutableData = TransformerUtils.extractAsMutableDocument(data)
    pruneFormRunnerMetadataFromMutableData(mutableData)
    mutableData
  }

  // Remove all `fr:*` elements and attributes
  def pruneFormRunnerMetadataFromMutableData(mutableData: DocumentInfo): Unit = {
    // Delete elements from concrete `List` to avoid possible laziness
    val frElements = mutableData descendant * filter (_.namespaceURI == XMLNames.FR) toList

    frElements.foreach (delete(_, doDispatch = false))

    // Attributes OTOH can be deleted in document order just fine
    val allElements = mutableData descendant *

    val frAttributes = allElements /@ @* filter (_.namespaceURI == XMLNames.FR)

    frAttributes.foreach (delete(_, doDispatch = false))

    // Also remove all `fr:*` namespaces
    // TODO: This doesn't work: we find the nodes but the delete action doesn't manage to delete the node.
    val frlNamespaces = allElements.namespaceNodes filter (_.stringValue == XMLNames.FR)

    frlNamespaces.foreach (delete(_, doDispatch = false))
  }
}
