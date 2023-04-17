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

import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.email.{EmailMetadata, EmailMetadataConversion, EmailMetadataParsing, EmailMetadataSerialization}
import org.orbeon.oxf.fr.permission.{Operation, Operations}
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath.NodeInfoOps

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
      head             = head,
      data             = Option(data),
      sectionPredicate = _ => true,
      controlPredicate = frc.hasAllClassesPredicate(classNames.splitTo[List]())
    )(
      new InDocFormRunnerDocContext(body)
    ) flatMap {
      case ControlBindPathHoldersResources(_, _, _, Some(holders), _) => holders
      case ControlBindPathHoldersResources(_, _, _, None, _) => Nil
    }

  def buildLinkBackToFormRunnerUsePageName(pageName: String): String =
    buildLinkBackToFormRunner(
      pageName match {
        case "edit"    => "LinkToEditPageParam"
        case "view"    => "LinkToViewPageParam"
        case "new"     => "LinkToNewPageParam"
        case "summary" => "LinkToSummaryPageParam"
        case "home"    => "LinkToHomePageParam"
        case "forms"   => "LinkToFormsPageParam"
        case "admin"   => "LinkToAdminPageParam"
        case "pdf"     => "LinkToPdfParam"
        case other     => throw new IllegalArgumentException(other)
      },
      includeToken = false
    )

  // TODO: move this as it's used by more than email
  //@XPathFunction
  def buildLinkBackToFormRunner(linkType: String, includeToken: Boolean): String = {

    implicit val formRunnerParams @ FormRunnerParams(app, form, version, documentOpt, _, _) = FormRunnerParams()

    val baseUrlNoSlash =
      frc.formRunnerStandaloneBaseUrl(
        CoreCrossPlatformSupport.properties,
        CoreCrossPlatformSupport.externalContext.getRequest
      ).dropTrailingSlash

    def buildTokenParam: Option[(String, String)] = {

      // Don't take risk and let the token expire immediately if the validity is not set
      val validityMinutes = frc.intFormRunnerProperty("oxf.fr.access-token.validity").getOrElse(0)

      val token =
        FormRunnerAccessToken.encryptToken(
          FormRunnerAccessToken.TokenHmac(
            app      = app,
            form     = form,
            version  = version,
            document = documentOpt
          ),
          FormRunnerAccessToken.TokenPayload(
            exp = java.time.Instant.now.plus(java.time.Duration.ofMinutes(validityMinutes)),
            ops = Operations.inDefinitionOrder(List(Operation.Read, Operation.Update))
          )
        )

      token.map(frc.AccessTokenParam -> _)
    }

    def build(mode: String, documentId: Option[String], params: List[(String, String)] = Nil): String =
      recombineQuery(
        pathQuery = s"$baseUrlNoSlash/fr/$app/$form/$mode${documentId map ("/" +) getOrElse ""}",
        params    = (frc.FormVersionParam -> version.toString) :: params
      )

    // Would be good not to have to hardcode these constants
    linkType match {
      case "LinkToEditPageParam"    | "link-to-edit-page"    => build("edit", documentOpt, (includeToken flatOption buildTokenParam).toList)
      case "LinkToViewPageParam"    | "link-to-view-page"    => build("view", documentOpt, (includeToken flatOption buildTokenParam).toList)
      case "LinkToNewPageParam"     | "link-to-new-page"     => build("new", None)
      case "LinkToSummaryPageParam" | "link-to-summary-page" => build("summary", None)
      case "LinkToHomePageParam"    | "link-to-home-page"    => s"$baseUrlNoSlash/fr/"
      case "LinkToFormsPageParam"   | "link-to-forms-page"   => s"$baseUrlNoSlash/fr/forms"
      case "LinkToAdminPageParam"   | "link-to-admin-page"   => s"$baseUrlNoSlash/fr/admin"
      case "LinkToPdfParam"         | "link-to-pdf"          => build("pdf", documentOpt)
      case _                                                 => throw new IllegalArgumentException(linkType)
    }
  }

  def isLegacy2021EmailMetadata(emailMetadata: NodeInfo): Boolean =
    List("subject", "body").forall(emailMetadata.child(_).nonEmpty)

  def parseEmailMetadata(
    emailMetadata : NodeInfo,
    formBody      : NodeInfo
  ): EmailMetadata.Metadata =
    if (isLegacy2021EmailMetadata(emailMetadata)) {
      val legacy2021Metadata = EmailMetadataParsing.parseLegacy2021Metadata(emailMetadata, formBody)
      EmailMetadataConversion.convertLegacy2021Metadata(legacy2021Metadata)
    } else {
      EmailMetadataParsing.parseCurrentMetadata(emailMetadata)
    }

  def serializeEmailMetadata(metadata: EmailMetadata.Metadata): Elem =
    EmailMetadataSerialization.serializeMetadata(metadata)

}

object FormRunnerEmail extends FormRunnerEmail