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

import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.email.{EmailMetadata, EmailMetadataConversion, EmailMetadataParsing, EmailMetadataSerialization}
import org.orbeon.oxf.fr.permission.Operation
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps}

import scala.collection.mutable


trait FormRunnerEmail {

  //@XPathFunction
  def buildCustomCssClassToControlNamesMapDocument(body: NodeInfo): NodeInfo = {

    val controlNamesWithCustomCssClasses =
      frc.searchControlsTopLevelOnly(
        data      = None,
        predicate = frc.hasAnyCustomClassPredicate
      )(
        new InDocFormRunnerDocContext(body)
      ) map {
        case ControlBindPathHoldersResources(control, _, _, _, _) =>
          frc.getControlName(control) -> control.attClasses.filterNot(frc.StandardCassNames)
      }

    val customCssClassToControlNames = mutable.Map[String, mutable.Set[String]]()

    controlNamesWithCustomCssClasses foreach {
      case (controlName, classes) =>
        classes foreach { clazz =>
          customCssClassToControlNames.getOrElseUpdate(clazz, mutable.Set[String]()) += controlName
        }
    }

    import org.orbeon.oxf.xforms.NodeInfoFactory.*

    elementInfo(
      "_",
      customCssClassToControlNames.toList.map { case (cssClass, controlNames) =>
        elementInfo(
          "entry",
          attributeInfo("class", cssClass) ::
            controlNames.toList.map(elementInfo(_))
        )
      }
    )
  }

  def buildLinkBackToFormRunnerUsePageName(pageName: String, includeToken: Boolean): String =
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
      includeToken
    )

  // TODO: move this as it's used by more than email
  //@XPathFunction
  def buildLinkBackToFormRunner(linkType: String, includeToken: Boolean): String = {

    implicit val formRunnerParams @ FormRunnerParams(app, form, _, documentOpt, _, _) = FormRunnerParams()

    val version = formRunnerParams.formVersion

    val baseUrlNoSlash =
      frc.formRunnerStandaloneBaseUrl(
        CoreCrossPlatformSupport.properties,
        CoreCrossPlatformSupport.externalContext.getRequest
      ).dropTrailingSlash

    def buildTokenParam(tokenOperations: Set[Operation]): Option[(String, String)] = {

      // Don't take risk and let the token expire immediately if the validity is not set
      val validityMinutes = frc.intFormRunnerProperty("oxf.fr.access-token.validity").getOrElse(0)

      val token =
        FormRunnerAccessToken.encryptToken(
          app         = app,
          form        = form,
          version     = version,
          documentOpt = documentOpt,
          validity    = java.time.Duration.ofMinutes(validityMinutes),
          operations  = tokenOperations
        )

      token.map(frc.AccessTokenParam -> _)
    }

    def build(mode: String, documentId: Option[String], params: List[(String, String)] = Nil): String =
      recombineQuery(
        pathQuery = s"$baseUrlNoSlash/fr/$app/$form/$mode${documentId map ("/" +) getOrElse ""}",
        params    = (frc.FormVersionParam -> version.toString) :: params
      )

    // Would be good not to have to hardcode these constants
    // TODO: use existing enum instead of String
    linkType match {
      case "LinkToEditPageParam"    | "link-to-edit-page"    => build("edit", documentOpt, if (! includeToken) Nil else buildTokenParam(Set(Operation.Read, Operation.Update)).toList)
      case "LinkToViewPageParam"    | "link-to-view-page"    => build("view", documentOpt, if (! includeToken) Nil else buildTokenParam(Set(Operation.Read                  )).toList)
      case "LinkToPdfParam"         | "link-to-pdf"          => build("pdf",  documentOpt, if (! includeToken) Nil else buildTokenParam(Set(Operation.Read                  )).toList)
      case otherLinkType if includeToken                     => throw new IllegalArgumentException(s"Token not supported for link type: $otherLinkType")
      case "LinkToNewPageParam"     | "link-to-new-page"     => build("new", None)
      case "LinkToSummaryPageParam" | "link-to-summary-page" => build("summary", None)
      case "LinkToHomePageParam"    | "link-to-home-page"    => s"$baseUrlNoSlash/fr/"
      case "LinkToFormsPageParam"   | "link-to-forms-page"   => s"$baseUrlNoSlash/fr/forms"
      case "LinkToAdminPageParam"   | "link-to-admin-page"   => s"$baseUrlNoSlash/fr/admin"
      case otherLinkType                                     => throw new IllegalArgumentException(otherLinkType)
    }
  }

  // TODO: also take the form definition as parameter, to be more discriminate, and avoid returning `true` if we don't have any `fr-email-…` controls
  def isLegacy2021EmailMetadata(emailMetadataOpt: Option[NodeInfo]): Boolean =
    emailMetadataOpt match {
      case Some(emailMetadata) => List("subject", "body").exists(emailMetadata.child(_).nonEmpty)
      case None                => true
    }

  def isLegacy2022EmailMetadata(emailMetadataOpt: Option[NodeInfo]): Boolean =
    emailMetadataOpt match {
      case Some(emailMetadata) => emailMetadata.child("templates").child("template").child("form-fields").nonEmpty
      case None                => false
    }

  //@XPathFunction
  def parseEmailMetadata(
    emailMetadata  : Option[NodeInfo],
    formDefinition : NodeInfo
  ): EmailMetadata.Metadata =
    if (isLegacy2021EmailMetadata(emailMetadata)) {
      val legacy2021Metadata = EmailMetadataParsing.parseLegacy2021Metadata(emailMetadata, formDefinition)
      EmailMetadataConversion.convertLegacy2021Metadata(legacy2021Metadata)
    } else if (isLegacy2022EmailMetadata(emailMetadata)) {
      val legacy2022Metadata = EmailMetadataParsing.parseLegacy2022Metadata(emailMetadata, formDefinition)
      EmailMetadataConversion.convertLegacy2022Metadata(legacy2022Metadata)
    } else {
      EmailMetadataParsing.parseCurrentMetadata(emailMetadata, formDefinition)
    }

  //@XPathFunction
  def serializeEmailMetadata(metadata: EmailMetadata.Metadata): NodeInfo =
    EmailMetadataSerialization.serializeMetadata(metadata)
}

object FormRunnerEmail extends FormRunnerEmail