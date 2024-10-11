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
package org.orbeon.oxf.fr.persistence.relational.form.adt

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.fr.FormRunnerHome.RemoteServer
import org.orbeon.oxf.fr.persistence.proxy.PersistenceProxyProcessor
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.instantFromString
import org.orbeon.oxf.fr.persistence.relational.form.FormProcessor
import org.orbeon.oxf.fr.persistence.relational.form.adt.LocalRemoteOrCombinator.Remote
import org.orbeon.oxf.fr.{AppFormOpt, FormDefinitionVersion}
import org.orbeon.oxf.http.{BasicCredentials, HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xml.dom.IOSupport.readOrbeonDom
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps

import scala.util.{Failure, Success, Try}


case class FormRequest(
  compatibilityMode      : Boolean,
  allForms               : Boolean,
  ignoreAdminPermissions : Boolean,
  languageOpt            : Option[String],
  remoteServerCredentials: Map[String, BasicCredentials],
  filterQueries          : Seq[FilterQuery[?]],
  // Support only one sort query for now
  sortQuery              : SortQuery[?],
  // Support no-pagination mode for compatibility reasons (i.e. old API returns all results)
  paginationOpt          : Option[Pagination]
) {

  def toXML: NodeInfo = {
    val baseElem =
      <search>{
        val remoteServersXml = remoteServerCredentials.toSeq.sortBy(_._1).flatMap { case (url, credentials) =>
          credentials.password.map { password =>
            <remote-server url={url} username={credentials.username} password={password}/>
          }
        }

        val filterQueriesXml = filterQueries.map(_.toXML).map(nodeInfoToElem)
        val sortQueryXml     = sortQuery.toXML
        val paginationXml    = paginationOpt.map(_.toXML).map(nodeInfoToElem)

        remoteServersXml ++ filterQueriesXml :+ sortQueryXml :+ paginationXml
       }</search>

    // toString method on Boolean returns "false"/"true"
    val allFormsAttOpt               = allForms.some.              map(_.toString).map(Form.attribute("all-forms"               , _))
    val ignoreAdminPermissionsAttOpt = ignoreAdminPermissions.some.map(_.toString).map(Form.attribute("ignore-admin-permissions", _))
    val languageAttOpt               = languageOpt                                .map(Form.attribute("xml:lang"                , _))

    Seq(allFormsAttOpt, ignoreAdminPermissionsAttOpt, languageAttOpt).flatten.foldLeft(baseElem)((elem, attribute) => elem % attribute)
  }

  def credentialsOpt(remoteServer: RemoteServer): Option[BasicCredentials] =
    remoteServerCredentials.get(remoteServer.url)

  // We only support Exact queries on app/form names for now, as we need to call FormRunnerPersistence.getProviders
  // with exact names. Substring queries would make sense, though, and should be supported.

  private val exactAppQueryOpt: Option[FilterQuery[String]] =
    filterQueries
      .collectFirst { case query @ FilterQuery(Metadata.AppName, _, _, MatchType.Exact, _) => query }
      .asInstanceOf[Option[FilterQuery[String]]] // TODO: fix type design/inference

  private val exactFormQueryOpt: Option[FilterQuery[String]] =
    filterQueries
      .collectFirst { case query @ FilterQuery(Metadata.FormName, _, _, MatchType.Exact, _) => query }
      .asInstanceOf[Option[FilterQuery[String]]] // TODO: fix type design/inference

  val latestFormVersionQueryOpt: Option[FilterQuery[FormDefinitionVersion]] =
    filterQueries
      .collectFirst { case query @ FilterQuery(Metadata.FormVersion, _, _, MatchType.Exact, FormDefinitionVersion.Latest) => query }
      .asInstanceOf[Option[FilterQuery[FormDefinitionVersion]]] // TODO: fix type design/inference

  val exactAppOpt: Option[String] =
    exactAppQueryOpt.map(_.value)

  val exactFormOpt: Option[String] =
    exactFormQueryOpt.map(_.value)

  // Compatibility mode: GET request with parameters encoded in URL; otherwise, POST request with parameters in body
  val pathSuffix: String            = FormProcessor.pathSuffix(compatibilityMode.flatOption(AppFormOpt(exactAppOpt, exactFormOpt)))
  val httpMethod: HttpMethod        = if (compatibilityMode) HttpMethod.GET else HttpMethod.POST
  val httpBodyOpt: Option[NodeInfo] = (! compatibilityMode).option(toXML)

  // Currently, only exact app/form queries will be processed by local providers and remote servers. Remote server
  // credentials are only needed by the proxy. Pagination is also done by the proxy.

  def formRequestForLocalProvider: FormRequest =
    formRequestForRemoteServer

  def formRequestForRemoteServer: FormRequest =
    copy(
      remoteServerCredentials = Map.empty,
      filterQueries           = Seq(exactAppQueryOpt, exactFormQueryOpt).flatten,
      paginationOpt           = None
    )
}

object FormRequest {
  def parseOrThrowBadRequest(formRequest: => FormRequest): FormRequest =
    Try(formRequest) match {
      case Success(formRequest) => formRequest
      case Failure(t)           => throw HttpStatusCodeException(StatusCode.BadRequest, throwable = t.some)
    }

  def apply(
    request            : Request,
    bodyFromPipelineOpt: Option[NodeInfo],
    appFormFromUrlOpt  : Option[AppFormOpt]
  ): FormRequest =
    if (request.getMethod == HttpMethod.GET) {
      // Compatibility with old API: read parameters from URL
      fromGetRequest(request, appFormFromUrlOpt)
    } else if (request.getMethod == HttpMethod.POST) {
      // Read parameters from POST body (ignore URL parameters)
      fromPostRequest(request, bodyFromPipelineOpt)
    } else {
      throw new IllegalArgumentException(s"Unsupported method: ${request.getMethod}")
    }

  private def fromGetRequest(request: Request, appFormFromUrlOpt: Option[AppFormOpt]): FormRequest =  {

    // Convert parameters extracted from URL to Query instances

    val appOpt   = appFormFromUrlOpt.map(_.app)
    val appQuery = appOpt.toSeq.map(FilterQuery.exactAppQuery)

    val formOpt   = appFormFromUrlOpt.flatMap(_.formOpt)
    val formQuery = formOpt.toSeq.map(FilterQuery.exactFormQuery)

    val allVersions      = request.getFirstParamAsString("all-versions").getOrElse("false").toBoolean
    val allVersionsQuery = (! allVersions).seq(FilterQuery.latestVersionsQuery)

    val modifiedSinceOpt   = request.getFirstParamAsString("modified-since").map(instantFromString)
    val modifiedSinceQuery = modifiedSinceOpt.toSeq.map(FilterQuery.modifiedSinceQuery)

    FormRequest(
      compatibilityMode       = true,
      allForms                = request.getFirstParamAsString("all-forms")               .getOrElse("false").toBoolean,
      ignoreAdminPermissions  = request.getFirstParamAsString("ignore-admin-permissions").getOrElse("false").toBoolean,
      languageOpt             = None,
      remoteServerCredentials = Map.empty,
      filterQueries           = appQuery ++ formQuery ++ allVersionsQuery ++ modifiedSinceQuery,
      sortQuery               = SortQuery.defaultSortQuery,
      paginationOpt           = None
    )
  }

  private def fromPostRequest(request: Request, bodyFromPipelineOpt: Option[NodeInfo]): FormRequest = {

    val bodyXml = bodyFromPipelineOpt.getOrElse {
      StaticXPath.orbeonDomToTinyTree(readOrbeonDom(PersistenceProxyProcessor.requestInputStream(request)))
    }

    FormRequest(bodyXml)
  }

  def apply(xml: NodeInfo): FormRequest = {

    val searchElement = xml.rootElement

    val remoteServerCredentials = (searchElement / "remote-server").map { remoteServer =>
      val url         = remoteServer.attValue("url")
      val credentials = BasicCredentials(
        username       = remoteServer.attValue("username"),
        password       = Some(remoteServer.attValue("password")),
        preemptiveAuth = true,
        domain         = None
      )
      url -> credentials
    }.toMap

    val filterQueries = searchElement.child("filter").map(FilterQuery.apply)

    // Check that query URLs match credentials URLs
    val remoteServerUrls = remoteServerCredentials.keySet
    filterQueries.map(_.localRemoteOrCombinator).collect { case Remote(url) => url }.distinct.foreach { queryUrl =>
      if (! remoteServerUrls.contains(queryUrl)) {
        throw new IllegalArgumentException(s"Query URL `$queryUrl` does not match any remote server credentials")
      }
    }

    FormRequest(
      compatibilityMode       = false,
      allForms                = searchElement.attValueOpt("all-forms")               .contains(true.toString),
      ignoreAdminPermissions  = searchElement.attValueOpt("ignore-admin-permissions").contains(true.toString),
      languageOpt             = searchElement.attValueOpt("*:lang"),
      remoteServerCredentials = remoteServerCredentials,
      filterQueries           = filterQueries,
      sortQuery               = searchElement.child("sort")      .headOption.map(SortQuery .apply).getOrElse(SortQuery.defaultSortQuery),
      paginationOpt           = searchElement.child("pagination").headOption.map(Pagination.apply)
    )
  }
}
