/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.relational

import collection.JavaConverters._
import java.util.{List ⇒ JList}
import org.orbeon.saxon.om.{NodeInfo, DocumentInfo}
import java.sql.Connection
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fr.FormRunner._

object Index {

    def updateIndex(connection: Connection, appName: String, formName: String, documentId: String, formDoc: DocumentInfo, dataDoc: DocumentInfo): Unit = {

        // Remove any existing data we might have in the index about this document
        val cleanStatement = connection.prepareStatement("""delete from orbeon_i_control_text where document_id = ?""")
        cleanStatement.setString(1, documentId)
        cleanStatement.execute()

        findIndexedControls(formDoc)
    }

    // For Summary page
    def findIndexedControlsAsXML(formDoc: DocumentInfo): Seq[NodeInfo] =
        findIndexedControls(formDoc) map (_.toXML)

    // Returns the controls that are searchable from a form definition
    def findIndexedControls(formDoc: DocumentInfo): Seq[IndexedControl] = {

        // Controls in the view, with the fr-summary or fr-search class
        val indexedControlElements =  {
            val body = formDoc \ "*:html" \ "*:body"
            val allControls = body \\ * filter (_ \@ "bind" nonEmpty)
            allControls filter (_.attClasses & Set("fr-summary", "fr-search") nonEmpty)
        }

        indexedControlElements map { control ⇒
            val controlName = getControlName(control)
            val bindForControl = findBindByName(formDoc, controlName).get
            val binds = (bindForControl ancestorOrSelf "*:bind").reverse.tail // skip root bind pointing to instance
            IndexedControl(
                name        = controlName,
                inSearch    = control.attClasses("fr-search"),
                inSummary   = control.attClasses("fr-summary"),
                xpath       = binds map (_.attValue("ref") + "[1]") mkString "/",
                xsType      = (bindForControl \@ "type" map (_.stringValue)).headOption getOrElse "xs:string",
                control     = control.localname,
                htmlLabel   = hasHTMLMediatype(control \ (XF → "label"))
             )
        }
    }

    case class IndexedControl(name: String, inSearch: Boolean, inSummary: Boolean, xpath: String, xsType: String, control: String, htmlLabel: Boolean) {
        def toXML: NodeInfo =
            <query name={name} path={xpath} type={xsType} control={control} search-field={inSearch.toString}
                   summary-field={inSummary.toString} match="substring" html-label={htmlLabel.toString}/>
    }
}
