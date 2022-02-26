/**
 * Copyright (C) 2022 Orbeon, Inc.
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
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.util.CoreUtils.{BooleanOps, PipeOps}
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupport}
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.saxon.function.ProcessTemplateSupport
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps}
import org.orbeon.xforms.XFormsNames

import scala.collection.compat.immutable.LazyList
import scala.xml.Elem

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

  def isLegacy2021EmailMetadata(emailMetadata: NodeInfo): Boolean =
    List("subject", "body").forall(emailMetadata.child(_).nonEmpty)

  def parseEmailMetadata(emailMetadata: NodeInfo): Metadata.Metadata =
    if (isLegacy2021EmailMetadata(emailMetadata)) {
      val legacy2021Metadata = Parsing.parseLegacy2021Metadata(emailMetadata)
      Conversion.convertLegacy2021Metadata(legacy2021Metadata)
    } else {
      Parsing.parseCurrentMetadata(emailMetadata)
    }

  def serializeEmailMetadata(metadata: Metadata.Metadata): Elem =
    Serializing.serializeMetadata(metadata)

  object Metadata {

    case class Metadata(templates: List[Template], params: List[Param])
    case class Template(name: String, lang: Option[String], subject: Part, body: Part)
    case class Part(isHTML: Boolean, text: String)

    sealed trait Param
    case class ControlValueParam(name: String, controlName: String) extends Param
    case class ExpressionParam  (name: String, expression : String) extends Param

    // `name` is common to all params
    implicit class ParamOps(private val p: Param) {
      def name: String = p match {
        case ControlValueParam(name, _) => name
        case ExpressionParam  (name, _) => name
      }
    }

    object Legacy2021 {
      case class Metadata(subject: Part, body: Part)
      case class Part(templates: List[Template], params: List[Param])
      case class Template(lang: String, isHTML: Boolean, text: String)
    }
  }

  object Conversion {

    def convertLegacy2021Metadata(metadata: Metadata.Legacy2021.Metadata): Metadata.Metadata =
      convertFormat(renameParams(metadata, paramsToRename(metadata)))

    private case class ParamToRename(originalName: String, newSubjectName: String, newBodyName: String)
    private def paramsToRename(metadata: Metadata.Legacy2021.Metadata): List[ParamToRename] = {
      var allParamNames: List[String] = (metadata.subject.params ++ metadata.body.params).map(_.name)
      val conflictingNames = allParamNames.filter(name => allParamNames.count(_ == name) > 1).distinct
      conflictingNames.map { originalName =>
        def newName() =
          LazyList.from(1).map(originalName + "-" + _).collectFirst {
            case candidateName if !allParamNames.contains(candidateName) => candidateName
          }.get.kestrel(newName => allParamNames = newName :: allParamNames)
        ParamToRename(originalName, newName(), newName())
      }
    }

    private def renameParams(
      metadata       : Metadata.Legacy2021.Metadata,
      paramsToRename : List[ParamToRename]
    ): Metadata.Legacy2021.Metadata =
      paramsToRename.foldLeft(metadata) { (metadata, paramToRename) =>
        def renamePart(
          part         : Metadata.Legacy2021.Part,
          originalName : String,
          newName      : String
        ): Metadata.Legacy2021.Part =
          Metadata.Legacy2021.Part(
            templates = part.templates.map { template =>
              template.copy(text = {
                val replacement = List(originalName -> ("{$" + newName + "}"))
                ProcessTemplateSupport.processTemplateWithNames(template.text, replacement)
              })
            },
            params    = part.params.map { param =>
              if (param.name == originalName)
                param match {
                  case p @ Metadata.ControlValueParam(_, _) => p.copy(name = newName)
                  case p @ Metadata.ExpressionParam  (_, _) => p.copy(name = newName)
                }
              else
                param
            }
          )
        Metadata.Legacy2021.Metadata(
          subject = renamePart(metadata.subject, paramToRename.originalName, paramToRename.newSubjectName),
          body    = renamePart(metadata.body   , paramToRename.originalName, paramToRename.newBodyName),
        )
      }

    private def convertFormat(metadata: Metadata.Legacy2021.Metadata): Metadata.Metadata = {
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

  object Serializing {

    def serializeMetadata(metadata: Metadata.Metadata): Elem =
      <email>{
        List(
          serializeTemplates(metadata.templates),
          serializeParams   (metadata.params)
        )
      }</email>

    def serializeParam(param: Metadata.Param): Elem =
      param match {
        case Metadata.ControlValueParam(_, controlName) =>
          <fr:param type="ControlValueParam" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
              <fr:name       >{ param.name  }</fr:name>
              <fr:controlName>{ controlName }</fr:controlName>
          </fr:param>
        case Metadata.ExpressionParam(_, expression) =>
          <fr:param type="ExpressionParam" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
              <fr:name>{ param.name  }</fr:name>
              <fr:expr>{ expression  }</fr:expr>
          </fr:param>
      }

    def serializeParams(params: List[Metadata.Param]): Elem =
      <parameters>{ params.map(serializeParam) }</parameters>

    def serializeTemplate(template: Metadata.Template): Elem =
      <template name={template.name} xml:lang={template.lang.orNull}>
        <subject mediatype={template.subject.isHTML.option("text/html").orNull}>{ template.subject.text }</subject>
        <body    mediatype={template.body   .isHTML.option("text/html").orNull}>{ template.body.text    }</body>
      </template>

    def serializeTemplates(templates: List[Metadata.Template]): Elem =
      <templates>{ templates.map(serializeTemplate) }</templates>
  }
}

object FormRunnerEmail extends FormRunnerEmail