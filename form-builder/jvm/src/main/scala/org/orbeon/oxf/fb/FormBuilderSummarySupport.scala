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
package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*

import java.util as ju
import scala.collection.mutable
import scala.util.{Failure, Success, Try}


object FormBuilderSummarySupport {

  import Private.*

  case class PublishedVersionSummary(
    version  : Int,
    available: Boolean
  )

  def publishedVersionsSummaryHtml(
    documents       : Array[NodeInfo],
    app             : String,
    form            : String,
    versionLabel    : String,
    availableLabel  : String,
    unavailableLabel: String
  ): String = {
    val docSeq = Option(documents).map(_.toList).getOrElse(Nil)

    val versions =
      (Option(app).flatMap(_.trimAllToOpt), Option(form).flatMap(_.trimAllToOpt)) match {
        case (Some(trimmedApp), Some(trimmedForm)) =>
          val targetAppForm = AppForm(trimmedApp, trimmedForm)
          val pageAppForms  = extractPageAppForms(docSeq) + targetAppForm

          implicit val indentedLogger: IndentedLogger =
            XFormsAPI.inScopeContainingDocumentOpt
              .map(_.getIndentedLogger("form-builder"))
              .getOrElse(new IndentedLogger(FallbackLogger))
          implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

          val requestAttributesOpt =
            Option(CoreCrossPlatformSupport.externalContext)
              .flatMap(ec => Option(ec.getRequest))
              .flatMap(req => Option(req.getAttributesMap))

          val versionsByAppForm =
            resolveVersionsForPage(
              appForms             = pageAppForms,
              requestAttributesOpt = requestAttributesOpt,
              fetchMetadata        = appFormsToFetch => PersistenceApi.readFormsMetadataForAppForms(appFormsToFetch.toSeq, allForms = true)
            )

          versionsByAppForm.getOrElse(targetAppForm, Nil)

        case _ =>
          Nil
      }

    formatPublishedVersionsHtml(
      versions         = versions,
      versionLabel     = Option(versionLabel)    .flatMap(_.trimAllToOpt).getOrElse("Version"),
      availableLabel   = Option(availableLabel)  .flatMap(_.trimAllToOpt).getOrElse("Available"),
      unavailableLabel = Option(unavailableLabel).flatMap(_.trimAllToOpt).getOrElse("Unavailable")
    )
  }

  def extractPageAppForms(documents: collection.Seq[NodeInfo]): Set[AppForm] =
    documents.iterator.flatMap { doc =>
      val details = (doc / "details" / "detail").toList
      val appOpt  =
        details
          .find(_.attValueOpt("path").exists(_.endsWith("/application-name")))
          .orElse(details.headOption)
          .flatMap(_.stringValue.trimAllToOpt)
      val formOpt =
        details
          .find(_.attValueOpt("path").exists(_.endsWith("/form-name")))
          .orElse(details.lift(1))
          .flatMap(_.stringValue.trimAllToOpt)

      for {
        a <- appOpt
        f <- formOpt
      } yield AppForm(a, f)
    }.toSet

  def parseFormsMetadataXml(formsDoc: DocumentNodeInfoType): Map[AppForm, List[PublishedVersionSummary]] = {
    val entries =
      for {
        formElem <- (formsDoc.rootElement / "form").toList
        // Only consider forms published locally (`<available>` is omitted when only remote metadata is present)
        availableElem <- (formElem / "available").headOption
        app           <- (formElem / "application-name").stringValue.trimAllToOpt
        form          <- (formElem / "form-name")       .stringValue.trimAllToOpt
      } yield {
        val version =
          (formElem / "form-version")
            .stringValue
            .trimAllToOpt
            .flatMap(_.toIntOption)
            .getOrElse(1)
        val available = availableElem.stringValue.trimAllToEmpty != "false"
        AppForm(app, form) -> PublishedVersionSummary(version = version, available = available)
      }

    entries
      .groupMap(_._1)(_._2)
      .view
      .mapValues { versions =>
        versions
          .groupMapReduce(_.version)(identity)((a, _) => a)
          .values
          .toList
          .sortBy(_.version)
      }
      .toMap
  }

  def resolveVersionsForPage(
    appForms            : Set[AppForm],
    requestAttributesOpt: Option[ju.Map[String, AnyRef]],
    fetchMetadata       : Set[AppForm] => Try[DocumentNodeInfoType]
  )(implicit
    indentedLogger      : IndentedLogger
  ): Map[AppForm, List[PublishedVersionSummary]] =
    if (appForms.isEmpty) {
      Map.empty
    } else {
      def load(): Map[AppForm, List[PublishedVersionSummary]] =
        fetchMetadata(appForms) match {
          case Success(formsDoc) =>
            parseFormsMetadataXml(formsDoc)
          case Failure(t) =>
            warn(s"error reading published form metadata for Form Builder Summary page: ${t.getMessage}")
            Map.empty
        }

      requestAttributesOpt match {
        case Some(atts) =>
          val cache =
            Option(atts.get(RequestCacheAttributeKey).asInstanceOf[PageVersionsCache])
              .getOrElse {
                val fresh = mutable.Map.empty[Set[AppForm], Map[AppForm, List[PublishedVersionSummary]]]
                atts.put(RequestCacheAttributeKey, fresh)
                fresh
              }
          cache.getOrElseUpdate(appForms, load())
        case None =>
          load()
      }
    }

  def formatPublishedVersionsHtml(
    versions        : Seq[PublishedVersionSummary],
    versionLabel    : String,
    availableLabel  : String,
    unavailableLabel: String
  ): String = {
    val sorted = versions.sortBy(_.version)
    if (sorted.isEmpty) {
      """<span class="fr-version-empty" aria-hidden="true">—</span>"""
    } else {
      val pillsHtml =
        sorted.map { item =>
          val statusLabel = if (item.available) availableLabel else unavailableLabel
          val pillClasses =
            if (item.available)
              "fr-table-pill fr-version-badge fr-pill-success"
            else
              "fr-table-pill fr-version-badge fr-pill-muted fr-pill-unavailable"
          val tooltipAttr = s"$versionLabel: v${item.version} — $statusLabel".escapeXmlForAttribute
          val srSuffix    = s" ($statusLabel)".escapeXmlMinimal
          s"""<span class="$pillClasses" title="$tooltipAttr">v${item.version}<span class="visually-hidden">$srSuffix</span></span>"""
        }.mkString

      s"""<span class="fr-status-pills">$pillsHtml</span>"""
    }
  }

  private object Private {
    type PageVersionsCache = mutable.Map[Set[AppForm], Map[AppForm, List[PublishedVersionSummary]]]

    val RequestCacheAttributeKey = "org.orbeon.oxf.fb.SummaryPublishedVersionsCache"
    val FallbackLogger           = LoggerFactory.createLogger("org.orbeon.oxf.fb.FormBuilderSummarySupport")
  }
}
