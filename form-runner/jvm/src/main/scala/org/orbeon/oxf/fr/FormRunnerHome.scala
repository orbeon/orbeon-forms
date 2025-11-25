/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import cats.implicits.catsSyntaxOptionId
import io.circe.{Json, parser}
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerPersistence.findProvider
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreCrossPlatformSupport.properties
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StringReplacer.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, DateUtilsUsingSaxon}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*

import scala.collection.immutable as i
import scala.util.Try


trait FormRunnerHome {

  private case class AvailableAndTime(available: Boolean, time: Long)

  // This is a subset of org.orbeon.oxf.fr.persistence.relational.form.adt.Form
  private case class Form(
    appForm : AppForm,
    version : String,
    local   : Option[AvailableAndTime],
    remote  : Option[AvailableAndTime],
    ops     : Set[String]
  ) {

    import org.orbeon.oxf.fr.persistence.relational.form.adt.Form.*

    def isLocalAvailable    = local  exists (_.available)
    def isRemoteAvailable   = remote exists (_.available)
    def isLocalUnavailable  = local  exists (! _.available)
    def isRemoteUnavailable = remote exists (! _.available)

    def isLocal = local.isDefined
    def isRemote = remote.isDefined

    def isLocalNewer  = isLocal && isRemote && local.get.time  > remote.get.time
    def isRemoteNewer = isLocal && isRemote && remote.get.time > local.get.time

    def isSummaryAllowed = ops intersect SummaryOps nonEmpty
    def isNewAllowed     = ops intersect NewOps nonEmpty
    def isAdmin          = ops(AdminOp)
  }

  private object Form {

    def apply(form: NodeInfo): Form = {

      def availableAndTime(nodeInfo: NodeInfo): AvailableAndTime =
        AvailableAndTime(
          (nodeInfo elemValue "available") != "false",
          DateUtilsUsingSaxon.parseISODateOrDateTime(nodeInfo elemValue "last-modified-time")
        )

      val localOpt  = form.some.filter(_.elemValueOpt("last-modified-time").isDefined)
      val remoteOpt = (form / "remote-server").headOption

      Form(
        AppForm(
          form elemValue Names.AppName,
          form elemValue Names.FormName
        ),
        form elemValue Names.FormVersion,
        localOpt  map availableAndTime,
        remoteOpt map availableAndTime,
        form attTokens "operations"
      )
    }
  }

  private def collectForms(forms: SequenceIterator, p: NodeInfo => Boolean = _ => true): Iterator[Form] =
    asScalaIterator(forms) collect { case form: NodeInfo if p(form) => Form(form) }

  private def formsForSelection(selection: String, forms: SequenceIterator): Iterator[Form] = {

    def appFormVersion(s: String) = {
      val parts = s.splitTo[List]("/")
      (parts(0), parts(1), parts(2))
    }

    val appFormsSet = selection.splitTo[List]() map appFormVersion toSet

    def formIsSelected(form: NodeInfo) =
      appFormsSet((form elemValue Names.AppName, form elemValue Names.FormName, form elemValue Names.FormVersion))

    collectForms(forms, formIsSelected)
  }

  def isLocal(form: NodeInfo)  = Form(form).local.isDefined
  def isRemote(form: NodeInfo) = Form(form).remote.isDefined

  //@XPathFunction
  def isLocalAvailable(form: NodeInfo)    = Form(form).local  exists(_.available)
  //@XPathFunction
  def isRemoteAvailable(form: NodeInfo)   = Form(form).remote exists(_.available)

  //@XPathFunction
  def isLocalUnavailable(form: NodeInfo)  = Form(form).local  exists(! _.available)
  //@XPathFunction
  def isRemoteUnavailable(form: NodeInfo) = Form(form).remote exists(! _.available)

  //@XPathFunction
  def isLocalNewer(form: NodeInfo) = Form(form).isLocalNewer
  //@XPathFunction
  def isRemoteNewer(form: NodeInfo) = Form(form).isRemoteNewer

  //@XPathFunction
  def canSelectUnpublishedLocal(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f => f.isAdmin && f.isLocalUnavailable)

  //@XPathFunction
  def canSelectPublishedLocal(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f => f.isAdmin && f.isLocalAvailable)

  //@XPathFunction
  def canSelectUnpublishedRemote(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f => f.isAdmin && f.isRemoteUnavailable)

  //@XPathFunction
  def canSelectPublishedRemote(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f => f.isAdmin && f.isRemoteAvailable)

  //@XPathFunction
  def canSelectLocalNewer(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f => f.isAdmin && f.isLocalNewer)

  //@XPathFunction
  def canSelectRemoteNewer(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f => f.isAdmin && f.isRemoteNewer)

  //@XPathFunction
  def canPublishLocal(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isLocalUnavailable)

  //@XPathFunction
  def canUnpublishLocal(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isLocalAvailable)

  //@XPathFunction
  def canPublishRemote(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isRemoteUnavailable)

  //@XPathFunction
  def canUnpublishRemote(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isRemoteAvailable)

  //@XPathFunction
  def canPublishLocalToRemote(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isLocal)

  //@XPathFunction
  def canPublishRemoteToLocal(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isRemote)

  //@XPathFunction
  def canNavigateSummary(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) exists (f => f.isLocalAvailable && f.isSummaryAllowed)

  //@XPathFunction
  def canNavigateNew(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) exists (f => f.isLocalAvailable && f.isNewAllowed)

  //@XPathFunction
  def canUpgradeLocal(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isLocal)

  //@XPathFunction
  def canUpgradeRemote(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isRemote)

  //@XPathFunction
  def canReEncrypt(selection: String, forms: SequenceIterator): Boolean = {
    implicit val propertySet: PropertySet = CoreCrossPlatformSupport.properties
    formsForSelection(selection, forms).forall { form =>
      val provider = findProvider(form.appForm, FormOrData.Data).get
      providerPropertyAsBoolean(provider, property = "reencrypt", default = false)
    }
  }

  // Return remote servers information:
  //
  // 1. If the backward compatibility property (oxf.fr.production-server-uri) is present and not empty, try to use it
  //    and return a sequence of one string containing the server URL configured.
  // 2. Else try the JSON property (oxf.fr.home.remote-servers). If the property exists and is well-formed, return
  //    a flattened sequence of label/url pairs.
  // 3. Otherwise, the empty sequence is returned.
  //@XPathFunction
  def remoteServersXPath: SequenceIterator =
    FormRunnerHome.remoteServers.flatMap { remoteServer =>
      remoteServer.label.toSeq ++ Seq(remoteServer.url)
    }
}

object FormRunnerHome {

  case class RemoteServer(label: Option[String], url: String)

  def remoteServers: Seq[RemoteServer] = {

    def fromCompatibility =
      remoteServerFromCompatibilityProperty map (Seq(_))

    def fromJSON =
      remoteServersFromJSONProperty

    def fromEither =
      fromCompatibility orElse fromJSON getOrElse Seq.empty[RemoteServer]

    fromEither
  }

  // Used for tests
  def tryRemoteServersFromString(json: String): Try[Vector[RemoteServer]] =
    parser.parse(json).toTry flatMap tryRemoteServersFromJSON

  private def tryRemoteServersFromJSON(json: Json): Try[Vector[RemoteServer]] = Try {
    json.asArray match {
      case Some(elements) =>
        elements flatMap (_.asObject) collect {
          case fields =>

            def getFieldOrThrow(key: String) =
              fields(key) flatMap (_.asString) flatMap trimAllToOpt getOrElse (throw new IllegalArgumentException)

            RemoteServer(label = getFieldOrThrow("label").some, url = getFieldOrThrow("url").dropTrailingSlash)
        }
      case _ =>
        throw new IllegalArgumentException
    }
  }

  private def remoteServersFromJSONProperty: Option[i.Seq[RemoteServer]] =
    properties.getPropertyOpt("oxf.fr.home.remote-servers") map { property =>
      Try(
        property.associatedValue(p =>
          parser.parse(p.stringValue).toTry.getOrElse(throw new IllegalArgumentException(p.stringValue))
        )
      ) flatMap tryRemoteServersFromJSON getOrElse {
        implicit val logger = inScopeContainingDocument.getIndentedLogger("form-runner")
        warn(
          s"incorrect JSON configuration for property `oxf.fr.home.remote-servers`",
          Seq("JSON" -> property.stringValue)
        )
        Nil
      }
    }

  private def remoteServerFromCompatibilityProperty: Option[RemoteServer] = (
    properties.getStringOrURIAsStringOpt("oxf.fr.production-server-uri")
    flatMap trimAllToOpt
    map     (url => RemoteServer(label = None, url = url.dropTrailingSlash))
  )
}
