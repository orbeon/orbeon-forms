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
package org.orbeon.oxf.fr.persistence.relational.search

import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.Logger
import org.orbeon.oxf.fr.persistence.relational.search.adt.{DocumentResult, FieldResult, SearchRequest}
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.NodeConversions

trait SearchResult extends SearchRequestParser {

  def outputDocumentResults(
    request   : SearchRequest,
    documents : List[DocumentResult],
    count     : Int,
    receiver  : XMLReceiver
  ): Unit = {

    // Produce XML result
    val documentsElem =
      <documents search-total={count.toString}>{
        documents.map(doc =>
          <document
            created             ={DateUtils.formatIsoDateTimeUtc(doc.metadata.createdTime.getTime)}
            last-modified       ={DateUtils.formatIsoDateTimeUtc(doc.metadata.lastModifiedTime.getTime)}
            created-by          ={doc.metadata.createdBy.map(_.username).map(xml.Text(_))}
            created-by-groupname={doc.metadata.createdBy.flatMap(_.groupname).map(xml.Text(_))}
            last-modified-by    ={doc.metadata.lastModifiedBy.map(_.username).map(xml.Text(_))}
            workflow-stage      ={doc.metadata.workflowStage.map(xml.Text(_))}
            name                ={doc.metadata.documentId}
            draft               ={doc.metadata.draft.toString}
            operations          ={doc.operations}>{

            <details>{
              request.fields.map { requestColumn =>
                  val columnValue = doc.values
                    // For all the value for the current doc, get the ones for the current column
                    .filter(_.control == requestColumn.path)
                    // Sort them in the order in which they appear in the document
                    .sortBy(_.pos)
                    // Just get the string value
                    .map(_.value)
                    // Return values as comma separated list, to be compatible with 2016.1 and earlier
                    .mkString(", ")
                  <detail path={requestColumn.path}>{columnValue}</detail>
              }
            }</details>

          }</document>
        )
      }</documents>

    if (Logger.debugEnabled)
      Logger.logDebug("search result", documentsElem.toString)

    NodeConversions.elemToSAX(documentsElem, receiver)
  }

  def outputFieldResults(
    fields   : List[FieldResult],
    receiver : XMLReceiver
  ): Unit = {

    // Produce XML result
    val fieldsElems =
      <fields>{
        fields.map { field =>
          <field path={field.path}>
            <values>{
              field.values.map { value =>
                <value>{value}</value>
              }
            }</values>
          </field>
        }
      }</fields>

    if (Logger.debugEnabled)
      Logger.logDebug("search result", fieldsElems.toString)

    NodeConversions.elemToSAX(fieldsElems, receiver)
  }
}
