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
package org.orbeon.oxf.fr.persistence.relational.index

import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.{DataMigration, FormRunner, FormRunnerPersistence}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML._

trait FormDefinition {

  private val FBLangPredicate = "[@xml:lang = $fb-lang]"

  // For Summary page
  //@XPathFunction
  def findIndexedControlsAsXML(formDoc: DocumentInfo, app: String, form: String): Seq[NodeInfo] =
    findIndexedControls(formDoc, app, form) map (_.toXML)

  // Returns the controls that are searchable from a form definition
  def findIndexedControls(formDoc: DocumentInfo, app: String, form: String): Seq[IndexedControl] = {

    // NOTE: Once we introduce
    val migratePathsTo40Format =
      FormRunnerPersistence.providerDataFormatVersion(app, form) == FormRunnerPersistence.DataFormatVersion400

    // Controls in the view, with the fr-summary or fr-search class
    val indexedControlElements = {
      // NOTE: Can't use getAllControlsWithIds() because Form Builder's form.xhtml doesn't follow the same body
      // pattern.
      val body        = formDoc \ "*:html" \ "*:body"
      val allControls = body \\ * filter (_ \@ "bind" nonEmpty)

      allControls filter (_.attClasses & Set("fr-summary", "fr-search") nonEmpty)
    }

    val migrationPaths =
      if (migratePathsTo40Format)
        FormRunner.metadataInstanceRootOpt(formDoc).toList flatMap
          DataMigration.migrationMapFromMetadata           flatMap
          DataMigration.decodeMigrationsFromJSON           map
          { case (path, iterationName) ⇒ path.splitTo[List]("/") }
      else
        Nil

    indexedControlElements map { control ⇒

      val controlName    = FormRunner.getControlName(control)
      val bindForControl = FormRunner.findBindByName(formDoc, controlName).get
      val bindsToControl = bindForControl.ancestorOrSelf("*:bind").reverse.tail // skip instance root bind
      val bindRefs       =
        for {
          bind    ← bindsToControl.to[List]
          bindRef = bind.attValue("ref")
        } yield {
          // Specific case for Form Builder: drop language predicate, as we want to index/return
          // values for all languages. So far, this is handled as a special case, as this is not
          // something that happens in other forms.
          if (bindRef.endsWith(FBLangPredicate))
            bindRef.dropRight(FBLangPredicate.length)
          else
            bindRef
        }

      // Adjust the search paths if needed by dropping the repeated grid iteration element
      val adjustedBindRefs =
        if (migratePathsTo40Format && (migrationPaths exists (bindRefs.startsWith(_))))
          bindRefs.dropRight(2) ::: bindRefs.last :: Nil
        else
          bindRefs

      IndexedControl(
        name      = controlName,
        inSearch  = control.attClasses("fr-search"),
        inSummary = control.attClasses("fr-summary"),
        xpath     = adjustedBindRefs mkString "/",
        xsType    = (bindForControl \@ "type" map (_.stringValue)).headOption getOrElse "xs:string",
        control   = control.localname,
        htmlLabel = FormRunner.hasHTMLMediatype(control \ (XF → "label"))
       )
    }
  }

  case class IndexedControl(
    name      : String,
    inSearch  : Boolean,
    inSummary : Boolean,
    xpath     : String,
    xsType    : String,
    control   : String,
    htmlLabel : Boolean
  ) {
    def toXML: NodeInfo =
      <query
        name={name}
        path={xpath}
        type={xsType}
        control={control}
        search-field={inSearch.toString}
        summary-field={inSummary.toString}
        match="substring"
        html-label={htmlLabel.toString}/>
  }

}
