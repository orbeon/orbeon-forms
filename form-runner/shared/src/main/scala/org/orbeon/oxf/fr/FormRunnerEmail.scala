/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import cats.syntax.option._
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.util.CoreUtils.PipeOps
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupport}
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps}
import org.orbeon.xforms.XFormsNames


trait FormRunnerEmail {

  // Given a form body and instance data:
  //
  // - find all controls with the given conjunction of class names
  // - for each control, find the associated bind
  // - return all data holders in the instance data to which the bind would apply
  //
  // The use case is, for example, to find all data holders pointed to by controls with the class
  // `fr-email-recipient` and, optionally, `fr-email-attachment`.
  //
  //@XPathFunction
  def searchHoldersForClassTopLevelOnly(
                                         body: NodeInfo,
                                         data: NodeInfo,
                                         classNames: String
                                       ): SequenceIterator =
    frc.searchControlsTopLevelOnly(
      data = Option(data),
      predicate = frc.hasAllClassesPredicate(classNames.splitTo[List]())
    )(
      new InDocFormRunnerDocContext(body)
    ) flatMap {
      case ControlBindPathHoldersResources(_, _, _, Some(holders), _) => holders
      case ControlBindPathHoldersResources(_, _, _, None, _) => Nil
    }

  // Given a form head, form body and instance data:
  //
  // - find all section templates in use
  // - for each section
  //   - determine the associated data holder in instance data
  //   - find the inline binding associated with the section template
  //   - find all controls with the given conjunction of class names in the section template
  //   - for each control, find the associated bind in the section template
  //   - return all data holders in the instance data to which the bind would apply
  //
  // The use case is, for example, to find all data holders pointed to by controls with the class
  // `fr-email-recipient` and, optionally, `fr-email-attachment`, which appear within section templates.
  //
  //@XPathFunction
  def searchHoldersForClassUseSectionTemplates(
                                                head: NodeInfo,
                                                body: NodeInfo,
                                                data: NodeInfo,
                                                classNames: String
                                              ): SequenceIterator =
    frc.searchControlsUnderSectionTemplates(
      head = head,
      data = Option(data),
      predicate = frc.hasAllClassesPredicate(classNames.splitTo[List]())
    )(
      new InDocFormRunnerDocContext(body)
    ) flatMap {
      case ControlBindPathHoldersResources(_, _, _, Some(holders), _) => holders
      case ControlBindPathHoldersResources(_, _, _, None, _) => Nil
    }

  //@XPathFunction
  def buildLinkBackToFormRunner(linkType: String): String = {

    val FormRunnerParams(app, form, version, documentOpt, isDraftOpt, _) = FormRunnerParams()

    val baseUrlNoSlash =
      frc.formRunnerStandaloneBaseUrl(
        CoreCrossPlatformSupport.properties,
        CoreCrossPlatformSupport.externalContext.getRequest
      ).dropTrailingSlash

    def build(mode: String, documentId: Option[String]) =
      recombineQuery(
        pathQuery = s"$baseUrlNoSlash/fr/$app/$form/$mode${documentId map ("/" +) getOrElse ""}",
        params = List(frc.FormVersionParam -> version.toString)
      )

    linkType match {
      case "LinkToEditPageParam" => build("edit", documentOpt)
      case "LinkToViewPageParam" => build("view", documentOpt)
      case "LinkToNewPageParam" => build("new", None)
      case "LinkToSummaryPageParam" => build("summary", None)
      case "LinkToHomePageParam" => s"$baseUrlNoSlash/fr/"
      case "LinkToPdfParam" => build("pdf", documentOpt)
      case _ => throw new IllegalArgumentException(linkType)
    }
  }

  def isLegacy2021Metadata(emailMetadata: NodeInfo): Boolean =
    List("subject", "body").forall(emailMetadata.child(_).nonEmpty)

  def parseMetadata(emailMetadata: NodeInfo): Metadata.Metadata =
    if (isLegacy2021Metadata(emailMetadata)) {
      val legacy2021Metadata = Parsing.parseLegacy2021Metadata(emailMetadata)
      Conversion.convertLegacy2021Metadata(legacy2021Metadata)
    } else {
      Parsing.parseCurrentMetadata(emailMetadata)
    }

  object Metadata {

    case class Metadata(templates: List[Template], params: List[Param])
    case class Template(name: String, lang: Option[String], subject: Part, body: Part)
    case class Part(isHTML: Boolean, text: String)

    sealed trait Param
    case class ControlValueParam(name: String, controlName: String) extends Param
    case class ExpressionParam  (name: String, expression: String) extends Param

    object Legacy2021 {
      case class Metadata(subject: Part, body: Part)
      case class Part(templates: List[Template], params: List[Param])
      case class Template(lang: String, isHTML: Boolean, text: String)
    }
  }

  object Conversion {

    def convertLegacy2021Metadata(metadata: Metadata.Legacy2021.Metadata): Metadata.Metadata = {
      val langs = metadata.subject.templates.map(_.lang)
      Metadata.Metadata(
        templates = langs.map { lang =>
          Metadata.Template(
            name    = "default",
            lang    = Some(lang),
            subject = {
              val legacySubject = metadata.subject.templates.filter(_.lang == lang).head
              Metadata.Part(isHTML = legacySubject.isHTML, text = legacySubject.text)
            },
            body = {
              val legacyBody = metadata.body.templates.filter(_.lang == lang).head
              Metadata.Part(isHTML = legacyBody.isHTML, text = legacyBody.text)
            }
          )
        },
        params = metadata.subject.params ++ metadata.body.params
      )
    }

  }

  object Parsing {

    def parseCurrentMetadata(emailMetadata: NodeInfo): Metadata.Metadata =
      Metadata.Metadata(
        templates = emailMetadata.child("templates").child("template").toList.map(parseTemplate),
        params    = emailMetadata.child("parameters").child(XMLNames.FRParamTest).toList.map(parseParam)
      )

    def parseTemplate(templateNodeInfo: NodeInfo): Metadata.Template =
      Metadata.Template(
        name    = templateNodeInfo.attValue("name"),
        lang    = templateNodeInfo.attValueOpt(XMLConstants.XML_LANG_QNAME),
        subject = parsePart(templateNodeInfo.child("subject").head),
        body    = parsePart(templateNodeInfo.child("body").head)
      )

    def parsePart(partNodeInfo: NodeInfo): Metadata.Part =
      Metadata.Part(
        isHTML = partNodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType),
        text   = partNodeInfo.stringValue
      )

    def parseLegacy2021Metadata(emailMetadata: NodeInfo): Metadata.Legacy2021.Metadata =
      Metadata.Legacy2021.Metadata(
        subject = parseLegacy2021Part(emailMetadata.child("subject").head),
        body    = parseLegacy2021Part(emailMetadata.child("body"   ).head)
      )

    def parseLegacy2021Part(partNodeInfo: NodeInfo): Metadata.Legacy2021.Part =
      Metadata.Legacy2021.Part(
        templates = partNodeInfo.child("template").toList.map { templateNodeInfo =>
          Metadata.Legacy2021.Template(
            lang   = templateNodeInfo.attValue(XMLConstants.XML_LANG_QNAME),
            isHTML = templateNodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType),
            text   = templateNodeInfo.stringValue
          )
        },
        params = partNodeInfo.child(XMLNames.FRParamTest).toList.map(parseParam)
      )

    def parseParam(paramNodeInfo: NodeInfo): Metadata.Param = {
      val name = paramNodeInfo.child(XMLNames.FRNameTest).stringValue
      paramNodeInfo.attValue("type") match {
        case "ControlValueParam" => Metadata.ControlValueParam(name, paramNodeInfo.child(XMLNames.FRControlNameTest).stringValue)
        case "ExpressionParam"   => Metadata.ExpressionParam  (name, paramNodeInfo.child(XMLNames.FRExprTest).stringValue)
      }
    }

  }
}

object FormRunnerEmail extends FormRunnerEmail