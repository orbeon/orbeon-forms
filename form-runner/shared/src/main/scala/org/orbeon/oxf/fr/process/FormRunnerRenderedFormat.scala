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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.process.ProcessInterpreter._
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import java.net.URI
import scala.language.postfixOps


object FormRunnerRenderedFormat {

  case class PdfTemplate(path: String, nameOpt: Option[String], langOpt: Option[String]) {
    require(path ne null)
  }

  val PdfTemplateNameParam = "pdf-template-name"
  val PdfTemplateLangParam = "pdf-template-lang"
  val UsePdfTemplateParam  = "use-pdf-template"

  private val PdfElemName  = "pdf"

  //@XPathFunction
  def findTemplatePath(
    frFormAttachmentsRootElemOpt : Option[NodeInfo],
    format                       : String,
    pdfTemplateNameOrNull        : String,
    pdfTemplateLangOrNull        : String
  ): String = {

    val pdfTemplateNameOpt = pdfTemplateNameOrNull.trimAllToOpt
    val pdfTemplateLangOpt = pdfTemplateLangOrNull.trimAllToOpt

    val params =
      (pdfTemplateNameOpt map (Some(PdfTemplateNameParam) -> ) toList) :::
      (pdfTemplateLangOpt map (Some(PdfTemplateLangParam) -> ) toList)

    val pdfTemplateOpt =
      findPdfTemplate(
        frFormAttachmentsRootElemOpt,
        params.toMap,
        None // We could try to select a default language, but the language really should be passed in the URL.
      )

    pdfTemplateOpt map (_.path) orNull
  }

  def getOrCreateRenderedFormatPathElemOpt(
    urlsInstanceRootElem : NodeInfo,
    format               : RenderedFormat,
    pdfTemplateOpt       : Option[PdfTemplate],
    defaultLang          : String,
    create               : Boolean
  ): Option[NodeInfo] = {

    // Examples:
    // <urls>
    //   <pdf-automatic-en/>
    //   <tiff-automatic-en/>
    //   <pdf-template-en-myName/>
    //   <tiff-template-en/>
    // </urls>

    val nameOpt = pdfTemplateOpt flatMap (_.nameOpt)
    val lang    = pdfTemplateOpt flatMap (_.langOpt) getOrElse defaultLang

    val key = s"${format.entryName}-${if (pdfTemplateOpt.isDefined) "template" else "automatic"}-$lang${nameOpt map ("-" +) getOrElse ""}"

    List(urlsInstanceRootElem) child key headOption match {
      case None if create =>
        XFormsAPI.insert(
          into   = urlsInstanceRootElem,
          after  = urlsInstanceRootElem child *,
          origin = NodeInfoFactory.elementInfo(key)
        ).headOption
      case someOrNone =>
        someOrNone
    }
  }

  def renderedFormatPathOpt(
    urlsInstanceRootElem: NodeInfo,
    renderedFormat      : RenderedFormat,
    pdfTemplateOpt      : Option[PdfTemplate],
    defaultLang         : String
  ): Option[(URI, String)] =
    for {
      node <- getOrCreateRenderedFormatPathElemOpt(urlsInstanceRootElem, renderedFormat, pdfTemplateOpt, defaultLang, create = false)
      path <- trimAllToOpt(node.stringValue)
    } yield
      URI.create(path) -> node.localname

  def listPdfTemplates: Seq[PdfTemplate] =
    formAttachmentsInstance map (_.rootElement) map extractPdfTemplates getOrElse Nil

  // https://github.com/orbeon/orbeon-forms/issues/5918
  def usePdfTemplate(req: Request): Boolean =
    listPdfTemplates.nonEmpty &&
      ! (req.getMethod == HttpMethod.POST && req.getFirstParamAsString(s"fr-$UsePdfTemplateParam").contains(false.toString))

  private def extractPdfTemplates(attachmentsRootElem: NodeInfo) =
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
    params                       : ActionParams,
    defaultLang                  : Option[String]
  ): Option[PdfTemplate] = {

    val hasTemplates =
      frFormAttachmentsRootElemOpt exists (extractPdfTemplates(_).nonEmpty)

    val usePdfTemplate              = hasTemplates && booleanParamByName(params, UsePdfTemplateParam, default = true)
    val requestedPdfTemplateNameOpt = paramByName(params, PdfTemplateNameParam)

    usePdfTemplate option {
      frFormAttachmentsRootElemOpt flatMap { rootElem =>
        selectPdfTemplate(
          attachmentsRootElem = rootElem,
          pdfTemplateNameOpt  = requestedPdfTemplateNameOpt,
          requestedLangOpt    = paramByName(params, PdfTemplateLangParam) flatMap trimAllToOpt,
          defaultLang         = defaultLang
        )
      } getOrElse {
        throw new OXFException("No PDF template found")
      }
    }
  }

  private val FalseAndTrue = Set(false.toString, true.toString)

  // TODO: what if no PDF/TIFF is produced at all?
  private[process] // for tests
  def createPdfOrTiffParams(
    frFormAttachmentsRootElemOpt : Option[NodeInfo],
    params                       : ActionParams,
    defaultLang                  : String
  ): List[(String, String)] = {

    val pdfTemplateOpt = findPdfTemplate(frFormAttachmentsRootElemOpt, params, Some(defaultLang))

    def nameParamList =
      (pdfTemplateOpt flatMap (_.nameOpt)).toList map (s"fr-$PdfTemplateNameParam" -> _)

    def langParamList = {

      def langParamForPdfTemplateOpt =
        (pdfTemplateOpt flatMap (_.langOpt)) map { lang =>
          (s"fr-$PdfTemplateLangParam" -> lang) :: Nil
        }

      def langParamForPdfAutomatic = {
        val lang = paramByName(params, "lang") flatMap trimAllToOpt getOrElse defaultLang
        List("fr-remember-language" -> false.toString, LanguageParam -> lang)
      }

      pdfTemplateOpt match {
        case Some(_) => langParamForPdfTemplateOpt getOrElse Nil
        case None    => langParamForPdfAutomatic
      }
    }

    def paramIfBoolean(paramName: String) =
      paramByName(params, paramName).filter(FalseAndTrue)

    def hintsAlertsParamForPdfAutomatic(token: String) = {
      val valueOpt = paramIfBoolean(s"show-$token").map(s"fr-pdf-show-$token" -> _)
      pdfTemplateOpt.isEmpty && valueOpt.isDefined flatList valueOpt.toList
    }

    (s"fr-$UsePdfTemplateParam" -> pdfTemplateOpt.isDefined.toString) ::
      nameParamList                             :::
      langParamList                             :::
      hintsAlertsParamForPdfAutomatic("hints")  :::
      hintsAlertsParamForPdfAutomatic("alerts") :::
      hintsAlertsParamForPdfAutomatic("required")
  }
}