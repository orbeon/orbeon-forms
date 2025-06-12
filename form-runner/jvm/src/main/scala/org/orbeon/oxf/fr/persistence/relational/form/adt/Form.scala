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

import org.orbeon.oxf.externalcontext.Credentials
import org.orbeon.oxf.fr.permission.{Operations, PermissionsAuthorization}
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.instantFromString
import org.orbeon.oxf.fr.{AppForm, FormDefinitionVersion, FormRunner, Names}
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps

import java.time.Instant


// Use a case class, so we'll be able to pattern match on it
case class OperationsList(ops: List[String])

case class FormMetadata(
  lastModifiedTime : Instant,
  lastModifiedByOpt: Option[String],
  created          : Option[Instant],
  title            : Map[String, String],
  available        : Boolean,
  permissionsOpt   : Option[NodeInfo], // Keep permissions as untyped XML for now
  operations       : OperationsList
) {

  // As of 2024-09-09, we only extract the titles, form availability, and permissions from the metadata SQL column. The
  // metadata fields present in that column are defined in RequestReader.MetadataElementsToKeep.

  def toXML: (Seq[xml.Elem], Seq[xml.Attribute]) = {

    // Use "last-modified-time" instead of "last-modified" for compatibility reasons
    val lastModifiedTimeXml  = <last-modified-time>{lastModifiedTime}</last-modified-time>
    val lastModifiedByXmlOpt = lastModifiedByOpt.map(lastModifiedBy => <last-modified-by>{lastModifiedBy}</last-modified-by>)
    val createdXmlOpt        = created.map(c => <created>{c}</created>)
    val titleXml             = title.map { case (lang, title) => <title xml:lang={lang}>{title}</title> }
    val availabilityXml      = <available>{available}</available>
    val permissionsXmlOpt    = permissionsOpt.map(nodeInfoToElem).map(Form.elemWithoutScope)

    // 2024-07-23: I thought that it might not be necessary to include in the response the `<permissions>` element,
    // since we compute here the required `operations` attribute. However, the Search API, as well as the persistence
    // proxy, require that the `<permissions>` element be returned.
    // https://doc.orbeon.com/form-runner/api/persistence/custom-persistence-providers#form-metadata-api
    val elems =
      Seq(lastModifiedTimeXml) ++
      lastModifiedByXmlOpt.toSeq ++
      createdXmlOpt.toSeq ++
      titleXml ++
      Seq(availabilityXml) ++
      permissionsXmlOpt.toSeq

    // Always include operations as an attribute, even if no operation is available (for compatibility reasons)
    val attributes = Seq(Form.attribute("operations", operations.ops.mkString(" ")))

    (elems, attributes)
  }

  def withOperationsFromPermissions(
    appForm                     : AppForm,
    hasAdminPermissionForAppForm: Boolean,
    credentialsOpt              : Option[Credentials]
  )(implicit
    indentedLogger              : IndentedLogger
  ): FormMetadata = {

    // Compute the operations the user can potentially perform
    val operations = {
      val adminOperation = hasAdminPermissionForAppForm.list("admin")

      val otherOperations =
        Operations.serialize(
          PermissionsAuthorization.authorizedOperationsForNoData(
            permissions    = FormRunner.permissionsFromElemOrProperties(permissionsOpt, appForm),
            credentialsOpt = credentialsOpt
          ),
          normalized = true
        )

      adminOperation ++ otherOperations
    }

    copy(operations = OperationsList(operations))
  }
}

object FormMetadata {
  def apply(xml: NodeInfo): Option[FormMetadata] =
    xml.elemValueOpt("last-modified-time").map { lastModifiedTimeString =>
      // Last modification time is mandatory. If it is missing, this means there isn't any metadata.
      FormMetadata(
        lastModifiedTime  = instantFromString(lastModifiedTimeString),
        lastModifiedByOpt = xml.elemValueOpt("last-modified-by"),
        created           = xml.elemValueOpt("created").map(instantFromString),
        title             = (xml / "title").map { title =>
          title.attValue("*:lang") -> title.stringValue
        }.toMap,
        available         = xml.elemValueOpt("available").map(_.toBoolean).getOrElse(true),
        permissionsOpt    = xml.child("permissions").headOption,
        operations        = OperationsList(xml.attValue("operations").splitTo[List]())
      )
    }
}

case class Form(
  appForm         : AppForm,
  version         : FormDefinitionVersion.Specific,
  localMetadataOpt: Option[FormMetadata],
  remoteMetadata  : Map[String, FormMetadata]
) {

  def toXML: NodeInfo = {
    val appNameXml  = <application-name>{appForm.app}</application-name>
    val formNameXml = <form-name>{appForm.form}</form-name>
    val versionXml  = <form-version>{version.version}</form-version>

    val localMetadataXmlOpt        = localMetadataOpt.map(_.toXML)
    val localMetadataElemsOpt      = localMetadataXmlOpt.map(_._1)
    val localMetadataAttributesOpt = localMetadataXmlOpt.map(_._2)

    // Remote metadata elements
    val remoteMetadataElems = remoteMetadata.toSeq.sortBy(_._1).map { case (url, remoteFormMetadata) =>

      val (remoteMetadataElems, remoteMetadataAttributes) = remoteFormMetadata.toXML

      val remoteFormElem = <remote-server url={url}>{
        remoteMetadataElems
      }</remote-server>

      remoteMetadataAttributes.foldLeft(remoteFormElem)((elem, attribute) => elem % attribute)
    }

    val formElem = <form>{
      // Add local and remote metadata to base app/form/version
      Seq(appNameXml, formNameXml, versionXml) ++ localMetadataElemsOpt.toSeq ++ remoteMetadataElems
    }</form>

    // Add attribute to root <form> element
    localMetadataAttributesOpt.toSeq.flatten.foldLeft(formElem)((elem, attribute) => elem % attribute)
  }

  // Compute the operations the current user can perform from the permissions and filter out the form (i.e. return None)
  // if the user can't perform any operation.
  def withOperationsFromPermissions(
    fbPermissions         : Map[String, Set[String]],
    allForms              : Boolean,
    ignoreAdminPermissions: Boolean,
    credentialsOpt        : Option[Credentials]
  )(implicit
    indentedLogger        : IndentedLogger
  ): Option[Form] = {
    val hasAdminPermissionForAppForm  = {
      def canAccessEverything = fbPermissions.contains("*")
      def canAccessAppForm = {
        val formsUserCanAccess = fbPermissions.getOrElse(appForm.app, Set.empty)
        formsUserCanAccess.contains("*") || formsUserCanAccess.contains(appForm.form)
      }
      canAccessEverything || canAccessAppForm
    }

    // Add operations from permissions on local metadata, if any
    val localMetadataWithOperationsOpt = localMetadataOpt.map {
      _.withOperationsFromPermissions(appForm, hasAdminPermissionForAppForm, credentialsOpt)
    }

    val localAndRemoteMetadata                    = localMetadataWithOperationsOpt.toSeq ++ remoteMetadata.values.toSeq
    val atLeastOneLocalOrRemoteFormWithOperations = localAndRemoteMetadata.exists(_.operations.ops.nonEmpty)
    val atLeastOneLocalOrRemoteFormAvailable      = localAndRemoteMetadata.exists(_.available)

    val keepForm =
      allForms                                                   || // all forms are explicitly requested
      (hasAdminPermissionForAppForm && ! ignoreAdminPermissions) || // admins can see everything
      ! (
        appForm.form == Names.LibraryFormName       || // filter libraries
        ! atLeastOneLocalOrRemoteFormWithOperations || // filter forms on which user can't possibly do anything
        ! atLeastOneLocalOrRemoteFormAvailable         // filter forms marked as not available
      )

    keepForm.option(this.copy(localMetadataOpt = localMetadataWithOperationsOpt))
  }
}

object Form {

  val SummaryOps = Set("*", "update", "read", "delete")
  val NewOps     = Set("*", "create")
  val AdminOp    = "admin"

  def apply(xml: NodeInfo): Form =
    Form(
      appForm          = AppForm(
        app            = xml.elemValue("application-name"),
        form           = xml.elemValue("form-name")
      ),
      version          = FormDefinitionVersion.Specific(xml.elemValue("form-version").toInt),
      localMetadataOpt = FormMetadata(xml),
      remoteMetadata   = xml.child("remote-server").map { remoteServer =>
        val url         = remoteServer.attValue("url")
        val metadataOpt = FormMetadata(remoteServer)
        url -> metadataOpt.get
      }.toMap
    )

  def attribute(name: String, value: String): xml.Attribute =
    xml.Attribute(None.orNull, name, value, xml.Null)

  def elem(name: String, value: String, atts: (String, String)*): xml.Elem = {
    val attributes = atts.foldRight[xml.MetaData](xml.Null) {
      case ((name, value), acc) => xml.Attribute(None, name, xml.Text(value), acc)
    }
    xml.Elem(None.orNull, name, attributes, xml.TopScope, minimizeEmpty = true, Seq(xml.Text(value)) *)
  }

  def elemWithoutScope(elem: xml.Elem): xml.Elem =
    elem.copy(
      scope = xml.TopScope,
      child = elem.child.map {
        case e: xml.Elem => elemWithoutScope(e)
        case node        => node
      }
    )
}
