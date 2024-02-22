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

import org.orbeon.connection.ConnectionContextSupport
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence.{DataFormatVersionName, DataXml}
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.util.CoreCrossPlatformSupport.{properties, runtime}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger, PathUtils}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.SimplePath._

import java.time.{LocalDateTime, ZoneId}
import scala.concurrent.Await
import scala.concurrent.duration.Duration


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
  def searchableValues(formDoc: DocumentInfo, app: String, form: String, version: Int): NodeInfo = {
    implicit val indentedLogger: IndentedLogger = inScopeContainingDocument.getIndentedLogger("form-runner")
    Index.searchableValues(
      formDoc,
      AppForm(app, form),
      Some(version),
      FormRunnerPersistence.providerDataFormatVersionOrThrow(AppForm(app, form))
    ).toXML
  }

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

    implicit val externalContext         : ExternalContext                                    = CoreCrossPlatformSupport.externalContext
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait                      = CoreCrossPlatformSupport
    implicit val connectionCtx           : Option[ConnectionContextSupport.ConnectionContext] = ConnectionContextSupport.getContext(Map.empty)
    implicit val xfcd                    : XFormsContainingDocument                           = inScopeContainingDocument
    implicit val indentedLogger          : IndentedLogger                                     = xfcd.getIndentedLogger("form-runner")

    Await.result(
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
      ).unsafeToFuture(),
      Duration.Inf
    )

    // We don't need to update the attachment paths, since the caller just reads data, saves it under a different
    // document id, and then disposes of the read data.
  }
}

object FormRunnerSummary extends FormRunnerSummary