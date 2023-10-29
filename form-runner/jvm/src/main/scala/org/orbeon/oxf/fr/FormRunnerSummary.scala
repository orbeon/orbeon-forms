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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataFormatVersionName, DataXml}
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.util.CoreCrossPlatformSupport.properties
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath._

import java.time.{LocalDateTime, ZoneId}
import scala.collection.JavaConverters._

trait FormRunnerSummary {

  private def zoneIdToOffsetAsOfNow(zoneId: String): String = {
    // NOTE: It's unclear how right this is just around the time of transitioning from regular
    // time to daylight saving time.
    val zid = ZoneId.of(zoneId)
    zid.getRules.getOffset(LocalDateTime.now(zid)).getId
  }

  private val OffsetR = """([+-])(\d{2}):(\d{2})""".r

  private def offsetToDuration(offset: String): String =
    offset match {
      case "Z"                           => "PT0H0M"
      case OffsetR(sign, hours, minutes) => (if (sign == "+") "PT" else "-PT") + hours + 'H' + minutes + 'M'
      case other                         => throw new IllegalArgumentException(other)
    }

  //@XPathFunction
  def buildSummaryLinkButton(app: String, form: String, buttonName: String, documentId: String, lang: String): String = {

    val (mode, params) = buttonName match {
      case "excel-export" => "export" -> List("export-format" -> RenderedFormat.ExcelWithNamedRanges.entryName)
      case "xml-export"   => "export" -> List("export-format" -> RenderedFormat.XmlFormStructureAndData.entryName)
      case other          => other    -> Nil
    }

    PathUtils.recombineQuery(s"/fr/$app/$form/$mode/$documentId", "fr-language" -> lang :: params)
  }

  //@XPathFunction
  def defaultTimezoneToOffsetString: String =
    properties.getPropertyOpt("oxf.fr.default-timezone")
      .flatMap(_.stringValue.trimAllToOpt)
      .map(zoneIdToOffsetAsOfNow)
      .map(offsetToDuration)
      .orNull

  //@XPathFunction
  def findIndexedControlsAsXML(formDoc: DocumentInfo, app: String, form: String, userRoles: java.util.List[String]): Seq[NodeInfo] =
    Index.findIndexedControls(
      formDoc,
      FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form)),
      forUserRoles = Some(userRoles.asScala.toList)
    ) map
      (_.toXML)

  //@XPathFunction
  def duplicate(
    data          : NodeInfo,
    app           : String,
    form          : String,
    fromDocument  : String,
    toDocument    : String,
    formVersion   : String,
    workflowStage : String
  ): Unit = {

    val databaseDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form))

    putWithAttachments(
      liveData           = data.root,
      migrate            = None,
      toBaseURI          = "", // local save
      fromBasePaths      = List(createFormDataBasePath(app, form, isDraft = false, fromDocument) -> formVersion.trimAllToOpt.map(_.toInt).getOrElse(1)),
      toBasePath         = createFormDataBasePath(app, form, isDraft = false, toDocument),
      filename           = DataXml,
      commonQueryString  = s"$DataFormatVersionName=${databaseDataFormatVersion.entryName}",
      forceAttachments   = true,
      formVersion        = Some(formVersion),
      workflowStage      = Some(workflowStage)
    )
  }
}

object FormRunnerSummary extends FormRunnerSummary