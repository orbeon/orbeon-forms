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

import org.orbeon.oxf.fr.FormRunner._
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

    private def partitionNodes(
        mutableData : NodeInfo,
        migration   : List[(String, String)]
    ): List[(List[NodeInfo], String)] =
        migration flatMap {
            case (path, iterationName) ⇒

                // NOTE: Use collect, but we know they are nodes if the JSON is correct and contains paths
                val nodes = XML.eval(mutableData.rootElement, path) collect {
                    case node: NodeInfo ⇒ node
                }

                // NOTE: Should ideally test on uriQualifiedName instead. The data in practice has elements which
                // in no namespaces, and if they were in a namespace, the prefixes would likely be unique.
                val firsts = nodes filter (node ⇒ node.precedingSibling(node.name).isEmpty)

                firsts map { first ⇒
                    (first :: first.followingSibling(first.name).toList, iterationName)
                }
        }

    import org.orbeon.oxf.xforms.action.XFormsAPI._

    //@XPathFunction
    def dataMaybeMigratedFrom(data: DocumentInfo, metadata: Option[DocumentInfo]) =
        dataMaybeMigratedFromTo(data, metadata, migrateDataFrom)

    //@XPathFunction
    def dataMaybeMigratedTo(data: DocumentInfo, metadata: Option[DocumentInfo]) =
        dataMaybeMigratedFromTo(data, metadata, migrateDataTo)

    private def dataMaybeMigratedFromTo(
        data     : DocumentInfo,
        metadata : Option[DocumentInfo],
        migrate  : (DocumentInfo, String) ⇒ DocumentInfo
    ) =
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
            case (iterations, iterationName) ⇒

                val iterationContent = iterations map (_ / Node toList) // force

                delete(iterations.head / Node, doDispatch = false)
                delete(iterations.tail,        doDispatch = false)

                insert(
                    into       = iterations.head,
                    origin     = iterationContent map (elementInfo(iterationName, _)),
                    doDispatch = false
                )
        }

        mutableData
    }

    def migrateDataFrom(data: DocumentInfo, jsonMigrationMap: String): DocumentInfo = {

        val mutableData = TransformerUtils.extractAsMutableDocument(data)

        partitionNodes(mutableData, decodeMigrationsFromJSON(jsonMigrationMap)) foreach {
            case (iterations, iterationName) ⇒

                assert(iterations.tail.isEmpty)
                val container = iterations.head

                val iterationContent = (container / * toList) map (_ / Node toList) // force

                insert(
                    after      = container,
                    origin     = iterationContent map (elementInfo(container.name, _)),
                    doDispatch = false
                )

                delete(container, doDispatch = false)
        }

        mutableData
    }
}
