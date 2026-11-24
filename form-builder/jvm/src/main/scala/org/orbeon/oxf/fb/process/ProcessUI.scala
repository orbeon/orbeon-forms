/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fb.process

import org.orbeon.oxf.fb.{FormBuilder, FormBuilderDocContext, FormBuilderXPathApi}
import org.orbeon.oxf.fr.{FormRunner, FormRunnerParams}
import org.orbeon.oxf.fr.process.ProcessEditorUI
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames

import scala.util.Try


object ProcessUI {

  /*@XPathFunction*/
  def formContextXml(): NodeInfo = {
    val (hasDocContext, controlsXml, sectionsXml, emailTemplatesXml, pdfTemplatesXml, customModesXml) =
      Try {
        implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

        val someResourcesMap =
          Some(FormBuilder.currentResources.child(*).map(r => r.localname -> r).toMap)

        val controls =
          FormBuilderXPathApi.getControlsLabelValueItemset().map { item =>
            val label = item.attValue("label")
            val value = item.attValue("value")
            s"""<item label="${label.escapeXmlForAttribute}" value="${value.escapeXmlForAttribute}"/>"""
          }.mkString

        val sections =
          FormRunner.getAllContainersWithIds
            .filter(FormRunner.IsSection)
            .flatMap { sectionElem =>
              FormRunner.getControlNameOpt(sectionElem).map { sectionName =>
                val sectionLabel =
                  FormBuilder.getControlResourceOrEmpty(
                    sectionName,
                    "label",
                    removeHtmlTags  = true,
                    resourcesMapOpt = someResourcesMap
                  )
                val label = if (sectionLabel.nonEmpty) s"$sectionLabel ($sectionName)" else sectionName
                s"""<item label="${label.escapeXmlForAttribute}" value="${sectionName.escapeXmlForAttribute}"/>"""
              }
            }.mkString

        val emailTemplates =
          (ctx.metadataRootElem / "email" / "templates" / "template")
            .flatMap(_.attValueOpt("name"))
            .flatMap(_.trimAllToOpt)
            .distinct
            .map(n => s"""<item label="${n.escapeXmlForAttribute}" value="${n.escapeXmlForAttribute}"/>""")
            .mkString

        val pdfTemplates =
          (ctx.modelElem / XFormsNames.XFORMS_INSTANCE_QNAME)
            .find(_.id == "fr-form-attachments")
            .toList
            .flatMap(inst => (inst / * / "pdf").toList)
            .flatMap(_.attValueOpt("name"))
            .flatMap(_.trimAllToOpt)
            .distinct
            .map(n => s"""<item label="${n.escapeXmlForAttribute}" value="${n.escapeXmlForAttribute}"/>""")
            .mkString

        val customModes =
          Try(FormRunner.customModes(FormRunnerParams()).map(_.publicName).distinct).getOrElse(Nil)
            .map(m => s"""<item label="${m.escapeXmlForAttribute}" value="${m.escapeXmlForAttribute}"/>""")
            .mkString

        (true, controls, sections, emailTemplates, pdfTemplates, customModes)
      }.getOrElse((false, "", "", "", "", ""))

    ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(
      s"""<form-context has-doc-context="$hasDocContext">
         |  <controls>$controlsXml</controls>
         |  <sections>$sectionsXml</sections>
         |  <email-templates>$emailTemplatesXml</email-templates>
         |  <pdf-templates>$pdfTemplatesXml</pdf-templates>
         |  <custom-modes>$customModesXml</custom-modes>
         |  <processes/>
         |</form-context>""".stripMargin
    )
  }
}
