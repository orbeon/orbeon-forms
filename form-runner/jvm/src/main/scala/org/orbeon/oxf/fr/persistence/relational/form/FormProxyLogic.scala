/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.form

import cats.implicits.catsSyntaxOptionId
import org.orbeon.connection.{ConnectionResult, StreamedContent}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.{formBuilderPermissions, orbeonRolesFromCurrentRequest}
import org.orbeon.oxf.fr.FormRunnerHome.RemoteServer
import org.orbeon.oxf.fr.FormRunnerPersistence.{getPersistenceURLHeadersFromProvider, getProviders}
import org.orbeon.oxf.fr.permission.PermissionsAuthorization
import org.orbeon.oxf.fr.persistence.proxy.PersistenceProxyProcessor
import org.orbeon.oxf.fr.persistence.proxy.PersistenceProxyProcessor.OutgoingRequest.headersFromRequest
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.PersistenceBase
import org.orbeon.oxf.fr.persistence.relational.form.adt.{Form, FormRequest, FormResponse}
import org.orbeon.oxf.http.{BasicCredentials, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.PathUtils.PathOps
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, StaticXPath}
import org.orbeon.oxf.xml.dom.IOSupport.readOrbeonDom
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.NodeInfoOps

import java.net.ConnectException


trait FormProxyLogic { this: PersistenceProxyProcessor.type =>

  sealed trait MetadataProvider

  case class LocalMetadataProvider (provider: String) extends MetadataProvider

  case class RemoteMetadataProvider(
    remoteServer: RemoteServer,
    credentials : BasicCredentials
  ) extends MetadataProvider

  def localAndRemoteFormsMetadata(
    request          : Request,
    appFormFromUrlOpt: Option[AppFormOpt]
  )(implicit
    indentedLogger   : IndentedLogger
  ): NodeInfo = {

    val formRequest = FormRequest.parseOrThrowBadRequest(FormRequest(request, bodyFromPipelineOpt = None, appFormFromUrlOpt))

    val providers     = getProviders(formRequest.exactAppOpt, formRequest.exactFormOpt, FormOrData.Form)
    val remoteServers = FormRunnerHome.remoteServers

    // As of 2024, the resource provider does not return any form metadata. Processing it first if present allows us
    // to optimize the case where we only have one other local provider and no remote server.
    val (resourceProvider, nonResourceProviders) = providers.partition(_ == RelationalUtils.ResourceProvider)
    val localMetadataProviders  = (resourceProvider ::: nonResourceProviders).map(LocalMetadataProvider.apply)

    // Retrieve credentials for the remote servers from the request. Ignore servers for which we don't have credentials.
    val remoteMetadataProviders = for {
      remoteServer <- remoteServers
      credentials  <- formRequest.credentialsOpt(remoteServer)
    } yield RemoteMetadataProvider(remoteServer, credentials)

    val localFormsMetadata = localMetadataProviders.map(localForms(request, _, formRequest.formRequestForLocalProvider))

    val remoteFormsMetadata = remoteMetadataProviders.map { remoteMetadataProvider =>
      remoteMetadataProvider.remoteServer -> remoteForms(request, remoteMetadataProvider, formRequest.formRequestForRemoteServer)
    }

    val mergedFormsMetadata = merged(localFormsMetadata, remoteFormsMetadata, formRequest)

    mergedFormsMetadata.toXML
  }

  private def merged(
    localFormResponses : Seq[FormResponse],
    remoteFormResponses: Seq[(RemoteServer, FormResponse)],
    formRequest        : FormRequest
  )(implicit
    indentedLogger     : IndentedLogger
  ): FormResponse = {

    // We will index local and remote forms by app/form/version
    case class Key(appForm: AppForm, version: FormDefinitionVersion.Specific)

    val indexedLocalForms: Map[Key, Form] =
      localFormResponses
        .flatMap(_.forms)
        .map(form => Key(form.appForm, form.version) -> form)
        .toMap

    val indexedRemoteForms: Map[Key, Map[RemoteServer, Form]] =
      remoteFormResponses
        .flatMap { case (remoteServer, formResponse) => formResponse.forms.map(form => (remoteServer, form)) }
        .groupBy { case (_, form) =>  Key(form.appForm, form.version) }
        .view
        .mapValues(_.toMap)
        .toMap

    val allKeys = (indexedLocalForms.keySet ++ indexedRemoteForms.keySet).toList

    val latestFormVersionQueryOpt = formRequest.latestFormVersionQueryOpt

    // Keep only the latest version of each form if needed
    val keysToReturn =
      if (latestFormVersionQueryOpt.isDefined)
        allKeys.groupBy(_.appForm).map(_._2.maxBy(_.version.version)).toList
      else
        allKeys

    // Merge local and remote forms (i.e. group them by app/form/version)
    val mergedForms = keysToReturn.map { key =>
      val localFormOpt = indexedLocalForms.get(key)
      val remoteForms  = indexedRemoteForms.getOrElse(key, Map.empty)

      Form(
        appForm          = key.appForm,
        version          = key.version,
        localMetadataOpt = localFormOpt.flatMap(_.localMetadataOpt),
        remoteMetadata   = remoteForms.flatMap { case (remoteServer, form) =>
          form.localMetadataOpt.map(remoteServer.url -> _)
        }
      )
    }

    // Add operations (computed from local permissions) to the forms and remove forms the user can't perform any operation on
    val formsWithLocalOperations = mergedForms.flatMap {
      _.withOperationsFromPermissions(
        fbPermissions          = formBuilderPermissions(FormRunner.formBuilderPermissionsConfiguration, orbeonRolesFromCurrentRequest),
        allForms               = formRequest.allForms,
        ignoreAdminPermissions = formRequest.ignoreAdminPermissions,
        credentialsOpt         = PermissionsAuthorization.findCurrentCredentialsFromSession
      )
    }

    // 1) Filter forms (by queries)
    val queriesToCheck = formRequest.queries.diff(latestFormVersionQueryOpt.toSeq)
    val filteredForms  = formsWithLocalOperations.filter(form => queriesToCheck.forall(_.satisfies(form, formRequest.languageOpt)))

    // 2) Sort forms (by queries)
    // TODO: implement general sorting
    val sortedForms = filteredForms.sortBy { form =>
      // Sort forms by local OR remote last modified time
      -(form.localMetadataOpt.toSeq ++ form.remoteMetadata.toSeq.sortBy(_._1).map(_._2)).head.lastModifiedTime.toEpochMilli
    }

    // 3) Paginate forms
    val paginatedForms =
      if (formRequest.pageNumberOpt.isDefined || formRequest.pageSizeOpt.isDefined) {
        // Use same defaults as in search API
        val pageNumber = formRequest.pageNumberOpt.getOrElse(1)
        val pageSize   = formRequest.pageSizeOpt.getOrElse(10)

        val startIndex = (pageNumber - 1) * pageSize
        val endIndex   = startIndex + pageSize

        sortedForms.slice(startIndex, endIndex)
      } else {
        sortedForms
      }

    FormResponse(paginatedForms, searchTotal = sortedForms.size)
  }

  private def localForms(
    request              : Request,
    localMetadataProvider: LocalMetadataProvider,
    formRequest          : FormRequest
  )(implicit
    indentedLogger         : IndentedLogger
  ): FormResponse = {

    val (providerURI, providerHeaders) = getPersistenceURLHeadersFromProvider(localMetadataProvider.provider)

    localOrRemoteForms(
      request        = request,
      uriPrefix      = providerURI,
      headers        = providerHeaders,
      formRequest    = formRequest,
      credentialsOpt = None
    )
  }

  private def remoteForms(
    request               : Request,
    remoteMetadataProvider: RemoteMetadataProvider,
    formRequest           : FormRequest
  )(implicit
    indentedLogger        : IndentedLogger
  ): FormResponse =
    try {
      localOrRemoteForms(
        request        = request,
        uriPrefix      = remoteMetadataProvider.remoteServer.url.dropTrailingSlash + PersistenceBase,
        headers        = Map.empty,
        formRequest    = formRequest,
        credentialsOpt = remoteMetadataProvider.credentials.some
      )
    } catch {
      case ce: ConnectException    => throw HttpStatusCodeException(StatusCode.BadGateway, throwable = ce.some)
      case ve: ValidationException =>
        ve.throwable match {
          case _: ConnectException => throw HttpStatusCodeException(StatusCode.BadGateway, throwable = ve.some)
          case _                   => throw ve
        }
      case t: Throwable            => throw t
    }

  private def localOrRemoteForms(
    request       : Request,
    uriPrefix     : String,
    headers       : Map[String, String],
    formRequest   : FormRequest,
    credentialsOpt: Option[BasicCredentials]
  )(implicit
    indentedLogger: IndentedLogger
  ): FormResponse = {

    val requestContentOpt = formRequest.httpBodyOpt.map { httpBody =>
      val bodyAsBytes = StaticXPath.tinyTreeToString(httpBody).getBytes(CharsetNames.Utf8)
      StreamedContent.fromBytes(bodyAsBytes, ContentTypes.XmlContentType.some)
    }

    val cxr = proxyEstablishConnection(
      request        = OutgoingRequest(formRequest.httpMethod, headersFromRequest(request)),
      requestContent = requestContentOpt,
      uri            = uriPrefix.dropTrailingSlash + formRequest.pathSuffix,
      headers        = headers,
      credentials    = credentialsOpt
    )

    ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true) { is =>
      FormResponse(StaticXPath.orbeonDomToTinyTree(readOrbeonDom(is)).rootElement)
    }
  }
}
