/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.process

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.process.ProcessInterpreter.*
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*

import java.net.URI
import scala.language.postfixOps


object FormRunnerRenderedFormat {

  case class PdfTemplate(path: String, nameOpt: Option[String], langOpt: Option[String]) {
    require(path ne null)
  }

  val UsePdfTemplateParam   = "use-pdf-template"
  val PdfTemplateNameParam  = "pdf-template-name"
  val PdfTemplateNamesParam = "pdf-template-names"
  val PdfTemplateLangParam  = "pdf-template-lang"

  private val PdfElemName   = "pdf"

  sealed trait PdfRendering {
    def pdfTemplateOpt: Option[PdfTemplate] // pdf-template-name / pdf-template-names
  }

  object PdfRendering {

    case class Automatic(
      lang           : String,                 // lang
      showHintsOpt   : Option[Boolean] = None, // show-hints
      showAlertsOpt  : Option[Boolean] = None, // show-alerts
      showRequiredOpt: Option[Boolean] = None  // show-required
    ) extends PdfRendering {
      override val pdfTemplateOpt: Option[PdfTemplate] = None
    }

    case class Template(pdfTemplate: PdfTemplate) extends PdfRendering {
      override val pdfTemplateOpt: Option[PdfTemplate] = Some(pdfTemplate)
    }

    def templateOrAutomatic(
      frFormAttachmentsRootElemOpt: Option[NodeInfo],
      usePdfTemplate              : Boolean,
      pdfTemplateNameOpt          : Option[String],
      pdfTemplateLangOpt          : Option[String],
      automatic                   : Automatic,
      defaultLang                 : String
    ): PdfRendering =
      findPdfTemplate(
        frFormAttachmentsRootElemOpt = frFormAttachmentsRootElemOpt,
        usePdfTemplate               = usePdfTemplate,
        pdfTemplateNameOpt           = pdfTemplateNameOpt,
        pdfTemplateLangOpt           = pdfTemplateLangOpt,
        defaultLang                  = defaultLang.some
      ) match {
        case Some(pdfTemplate) => Template(pdfTemplate)
        case None              => automatic
      }

    def fromActionParams(
      params                      : ActionParams,
      frFormAttachmentsRootElemOpt: Option[NodeInfo],
      defaultLang                 : String
    ): PdfRendering =
      templateOrAutomatic(
        frFormAttachmentsRootElemOpt = frFormAttachmentsRootElemOpt,
        usePdfTemplate               = booleanParamByNameUseAvt(params, UsePdfTemplateParam, default = true),
        pdfTemplateNameOpt           = paramByNameUseAvt(params, PdfTemplateNameParam),
        pdfTemplateLangOpt           = paramByNameUseAvt(params, PdfTemplateLangParam),
        automatic                    = automaticFromActionParams(params, defaultLang),
        defaultLang                  = defaultLang
      )

    // Used by the email action, which supports multiple PDF templates
    def fromActionParamsPerPdfTemplate(
      params                      : ActionParams,
      frFormAttachmentsRootElemOpt: Option[NodeInfo],
      defaultLang                 : String
    ): List[PdfRendering] = {

      val pdfTemplateNames =
        (
          paramByNameUseAvt(params, PdfTemplateNameParam ).toList :::
          paramByNameUseAvt(params, PdfTemplateNamesParam).toList.flatMap(_.splitTo[List]())
        ).distinct

      val usePdfTemplate     = booleanParamByNameUseAvt(params, UsePdfTemplateParam, default = true)
      val pdfTemplateLangOpt = paramByNameUseAvt(params, PdfTemplateLangParam)
      val automatic          = automaticFromActionParams(params, defaultLang)

      val pdfTemplateNameOpts =
        if (pdfTemplateNames.isEmpty) List(None) else pdfTemplateNames.map(Some(_))

      pdfTemplateNameOpts.map { pdfTemplateNameOpt =>
        templateOrAutomatic(frFormAttachmentsRootElemOpt, usePdfTemplate, pdfTemplateNameOpt, pdfTemplateLangOpt, automatic, defaultLang)
      }.distinct
    }

    private def automaticFromActionParams(params: ActionParams, defaultLang: String): Automatic =
      Automatic(
        lang            = paramByNameUseAvt(params, "lang").getOrElse(defaultLang),
        showHintsOpt    = paramByNameUseAvt(params, "show-hints"   ).map(_ == "true"),
        showAlertsOpt   = paramByNameUseAvt(params, "show-alerts"  ).map(_ == "true"),
        showRequiredOpt = paramByNameUseAvt(params, "show-required").map(_ == "true")
      )
  }

  sealed trait RenderedFormatRequest { def format: RenderedFormat }
  case class PrintRequest (format: RenderedFormat.Print, pdfRendering: PdfRendering) extends RenderedFormatRequest
  case class ExportRequest(format: RenderedFormat.Export)                            extends RenderedFormatRequest

  object RenderedFormatRequest {
    def fromActionParams(
      params                      : ActionParams,
      format                      : RenderedFormat,
      frFormAttachmentsRootElemOpt: Option[NodeInfo],
      defaultLang                 : String
    ): RenderedFormatRequest =
      format match {
        case format: RenderedFormat.Print  =>
          PrintRequest(format, PdfRendering.fromActionParams(params, frFormAttachmentsRootElemOpt, defaultLang))
        case format: RenderedFormat.Export =>
          ExportRequest(format)
      }
  }

  //@XPathFunction
  def findTemplatePath(
    frFormAttachmentsRootElemOpt : Option[NodeInfo],
    format                       : String, // TODO: why is this unused?
    pdfTemplateNameOrNull        : String,
    pdfTemplateLangOrNull        : String
  ): String = {

    val pdfTemplateOpt =
      findPdfTemplate(
        frFormAttachmentsRootElemOpt = frFormAttachmentsRootElemOpt,
        usePdfTemplate               = true,
        pdfTemplateNameOpt           = pdfTemplateNameOrNull.trimAllToOpt,
        pdfTemplateLangOpt           = pdfTemplateLangOrNull.trimAllToOpt,
        defaultLang                  = None // We could try to select a default language, but the language really should be passed in the URL.
      )

    pdfTemplateOpt map (_.path) orNull
  }

  def getOrCreateRenderedFormatPathElemOpt(
    urlsInstanceRootElem: NodeInfo,
    request             : RenderedFormatRequest,
    defaultLang         : String,
    create              : Boolean
  ): Option[NodeInfo] = {

    // Examples:
    // <urls>
    //   <pdf-automatic-en/>
    //   <tiff-automatic-en/>
    //   <pdf-template-en-myName/>
    //   <tiff-template-en/>
    // </urls>

    val key =
      request match {
        case PrintRequest(format, PdfRendering.Template(PdfTemplate(_, nameOpt, langOpt))) =>
          s"${format.entryName}-template-${langOpt getOrElse defaultLang}${nameOpt map ("-" +) getOrElse ""}"
        case PrintRequest(format, PdfRendering.Automatic(lang, _, _, _)) =>
          s"${format.entryName}-automatic-$lang"
        case ExportRequest(format) =>
          s"${format.entryName}-automatic-$defaultLang"
      }

    urlsInstanceRootElem.child(key).headOption match {
      case None if create =>
        XFormsAPI.insert(
          into   = urlsInstanceRootElem,
          after  = urlsInstanceRootElem.child(*),
          origin = NodeInfoFactory.elementInfo(key)
        ).headOption
      case someOrNone =>
        someOrNone
    }
  }

  def updateOrCreateRenderedFormatPathElem(
    urlsInstanceRootElem : NodeInfo,
    key                  : String,
    url                  : URI
  ): Unit =
    urlsInstanceRootElem.child(key).headOption match {
      case Some(node) =>
        XFormsAPI.setvalue(
          ref   = List(node),
          value = url.toString
        )
      case None =>
        XFormsAPI.insert(
          into   = urlsInstanceRootElem,
          after  = urlsInstanceRootElem.child(*),
          origin = NodeInfoFactory.elementInfo(key, List(url.toString: StringValue))
        )
    }

  def renderedFormatPathOpt(
    urlsInstanceRootElem: NodeInfo,
    request             : RenderedFormatRequest,
    defaultLang         : String
  ): Option[(URI, String)] =
    for {
      node <- getOrCreateRenderedFormatPathElemOpt(urlsInstanceRootElem, request, defaultLang, create = false)
      path <- trimAllToOpt(node.stringValue)
    } yield
      URI.create(path) -> node.localname

  def listPdfTemplates: collection.Seq[PdfTemplate] =
    formAttachmentsInstance map (_.rootElement) map extractPdfTemplates getOrElse Nil

  // https://github.com/orbeon/orbeon-forms/issues/5918
  def usePdfTemplate(req: Request): Boolean =
    listPdfTemplates.nonEmpty && ! (
      req.getMethod == HttpMethod.POST &&
      req.getFirstParamAsString(s"fr-$UsePdfTemplateParam").contains(false.toString)
    )

  def extractPdfTemplates(attachmentsRootElem: NodeInfo): LazyList[PdfTemplate] =
    for {
      pdfElem <- attachmentsRootElem child PdfElemName
      path    <- pdfElem.stringValue.trimAllToOpt
    } yield
      PdfTemplate(path, pdfElem.attValueOpt("name") flatMap (_.trimAllToOpt), pdfElem.attValueOpt("lang") flatMap (_.trimAllToOpt))

  private def selectPdfTemplate(
    attachmentsRootElem : NodeInfo,
    pdfTemplateNameOpt  : Option[String],
    requestedLangOpt    : Option[String],
    defaultLang         : Option[String]
  ): Option[PdfTemplate] = {

    val pdfTemplates = extractPdfTemplates(attachmentsRootElem)

    // NOTE: We have a choice here if no name is requested:
    // - return entries without a name only
    // - return all entries, which is what we do below
    val matchingEntriesForNameOpt =
      pdfTemplateNameOpt match {
        case Some(name) =>
          val matches = pdfTemplates collect { case v @ PdfTemplate(_, Some(`name`), _) => v }
          matches.nonEmpty option matches
        case None =>
          Some(pdfTemplates)
      }

    matchingEntriesForNameOpt flatMap { matchingEntriesForName =>

      requestedLangOpt match {
        case Some(requestedLang) =>
          matchingEntriesForName collectFirst {
            case v @ PdfTemplate(_, _, Some(`requestedLang`)) => v
          }
        case None =>
          matchingEntriesForName collectFirst {
            case v @ PdfTemplate(_, _, `defaultLang`) => v
          } orElse
            matchingEntriesForName.headOption
      }
    }
  }

  // Can throw if PDF template is requested but not found.
  // TODO: Use `Validation` or `Either`.
  def findPdfTemplate(
    frFormAttachmentsRootElemOpt : Option[NodeInfo],
    usePdfTemplate               : Boolean,
    pdfTemplateNameOpt           : Option[String],
    pdfTemplateLangOpt           : Option[String],
    defaultLang                  : Option[String]
  ): Option[PdfTemplate] = {

    val hasTemplates =
      frFormAttachmentsRootElemOpt exists (extractPdfTemplates(_).nonEmpty)

    (hasTemplates && usePdfTemplate) option {
      frFormAttachmentsRootElemOpt flatMap { rootElem =>
        selectPdfTemplate(
          attachmentsRootElem = rootElem,
          pdfTemplateNameOpt  = pdfTemplateNameOpt,
          requestedLangOpt    = pdfTemplateLangOpt,
          defaultLang         = defaultLang
        )
      } getOrElse {
        throw new OXFException("No PDF template found")
      }
    }
  }

  // TODO: what if no PDF/TIFF is produced at all?
  private[process] // for tests
  def createPdfOrTiffParams(pdfRendering: PdfRendering): List[(String, String)] =
    pdfRendering match {
      case PdfRendering.Template(PdfTemplate(_, nameOpt, langOpt)) =>
        (s"fr-$UsePdfTemplateParam" -> true.toString)          ::
          nameOpt.toList.map(s"fr-$PdfTemplateNameParam" -> _) :::
          langOpt.toList.map(s"fr-$PdfTemplateLangParam" -> _)

      case PdfRendering.Automatic(lang, showHintsOpt, showAlertsOpt, showRequiredOpt) =>
        (s"fr-$UsePdfTemplateParam" -> false.toString)                     ::
          ("fr-remember-language"   -> false.toString)                     ::
          (LanguageParam            -> lang)                               ::
          showHintsOpt   .toList.map("fr-pdf-show-hints"    -> _.toString) :::
          showAlertsOpt  .toList.map("fr-pdf-show-alerts"   -> _.toString) :::
          showRequiredOpt.toList.map("fr-pdf-show-required" -> _.toString)
    }
}