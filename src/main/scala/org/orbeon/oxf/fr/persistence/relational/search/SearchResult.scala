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

import org.orbeon.oxf.util.DateUtils._
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.XML

trait SearchResult {

  def outputResult(request: Request, result: Result, receiver: XMLReceiver): Unit = {

    // Produce XML result
    val documentsElem =
      <documents>{
        for ((metadata, values) <- result) yield

          // TODO: handle operations attribute
          // TODO: check if any other attribute is used by the summary page
          <document
            created       ={DateTime.print(metadata.created.getTime)}
            last-modified ={DateTime.print(metadata.lastModifiedTime.getTime)}
            name          ={metadata.documentId}
            operations    ="*">{

            <details>{
              request.columns.map { requestColumn ⇒
                  val columnValue = values
                    // For all the value for the current doc, get the ones for the current column
                    .filter(_.control == requestColumn)
                    // Sort them in the order in which they appear in the document
                    .sortBy(_.pos)
                    // Just get the string value
                    .map(_.value)
                    // Return values as comma separated list, to be compatible with 2016.1 and earlier
                    .mkString(", ")
                  <detail>{columnValue}</detail>
              }
            }</details>

          }</document>
      }</documents>

    XML.elemToSAX(documentsElem, receiver)
  }

}
