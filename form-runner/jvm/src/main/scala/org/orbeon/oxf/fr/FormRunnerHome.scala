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

import io.circe.{Json, parser}
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence.findProvider
import org.orbeon.oxf.util.CoreCrossPlatformSupport.properties
import org.orbeon.oxf.util.DateUtilsUsingSaxon
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringReplacer._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.oxf.xforms.action.XFormsAPI.{insert, _}
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.{immutable => i}
import scala.util.Try


trait FormRunnerHome {

  private case class AvailableAndTime(available: Boolean, time: Long)

  private case class Form(
    appForm : AppForm,
    version : String,
    local   : Option[AvailableAndTime],
    remote  : Option[AvailableAndTime],
    ops     : Set[String]
  ) {

    import Form._

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

    private val SummaryOps = Set("*", "update", "read", "delete")
    private val NewOps     = Set("*", "create")
    private val AdminOp    = "admin"

    def apply(form: NodeInfo): Form = {

      val localTime  = form elemValueOpt "last-modified-time"
      val remoteTime = form elemValueOpt "remote-last-modified-time"

      Form(
        AppForm(
          form elemValue Names.AppName,
          form elemValue Names.FormName
        ),
        form elemValue Names.FormVersion,
        localTime  map (v => AvailableAndTime((form elemValue "available")        != "false", DateUtilsUsingSaxon.parseISODateOrDateTime(v))),
        remoteTime map (v => AvailableAndTime((form elemValue "remote-available") != "false", DateUtilsUsingSaxon.parseISODateOrDateTime(v))),
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
  def canReEncrypt(selection: String, forms: SequenceIterator): Boolean =
    formsForSelection(selection, forms).forall { form =>
      val provider = findProvider(form.appForm, FormOrData.Data).get
      providerPropertyAsBoolean(provider, property = "reencrypt", default = false)
    }

  // NOTE: It would be great if we could work on typed data, whether created from XML, JSON or an object
  // serialization. Here we juggle between XML and typed data.
  //@XPathFunction
  def joinLocalAndRemoteMetadata(
    local              : SequenceIterator,
    remote             : SequenceIterator,
    permissionInstance : NodeInfo
  ): SequenceIterator = {

    val combinedIndexIterator = {

      def makeKey(node: NodeInfo) =
        (node elemValue Names.AppName, node elemValue Names.FormName, node elemValue Names.FormVersion)

      def createIndex(it: SequenceIterator) = asScalaIterator(it) collect {
        case node: NodeInfo => makeKey(node) -> node
      } toMap

      val localIndex  = createIndex(local)
      val remoteIndex = createIndex(remote)

      (localIndex.keySet ++ remoteIndex.keySet).iterator map { key =>
        (localIndex.get(key), remoteIndex.get(key))
      }
    }

    def createNode(localAndOrRemote: (Option[NodeInfo], Option[NodeInfo])): NodeInfo = {

      def remoteElements(remoteNode: NodeInfo) = {

        def remoteElement(name: String) =
          elementInfo("remote-" + name, List(stringToStringValue(remoteNode elemValue name)))

        List(
          remoteElement("title"),
          remoteElement("available"),
          remoteElement("last-modified-time")
        )
      }

      localAndOrRemote match {
        case (Some(localNode), None) =>
          localNode
        case (None, Some(remoteNode)) =>
          // Don't just use remoteNode, because we need `remote-` prefixes for remote data
          elementInfo(
            "form",
            (remoteNode /@ "operations")     ++:
            (remoteNode / Names.FormVersion) ++:
            (remoteNode / Names.AppName)     ++:
            (remoteNode / Names.FormName)    ++:
            remoteElements(remoteNode)
          )
        case (Some(localNode), Some(remoteNode)) =>
          insert(origin = remoteElements(remoteNode), into = localNode, after = localNode / *, doDispatch = false)
          localNode
        case (None, None) =>
          throw new IllegalStateException
      }
    }

    (combinedIndexIterator map createNode).toList // https://github.com/orbeon/orbeon-forms/issues/6016
  }

  // Return remote servers information:
  //
  // 1. If the backward compatibility property (oxf.fr.production-server-uri) is present and not empty, try to use it
  //    and return a sequence of one string containing the server URL configured.
  // 2. Else try the JSON property (oxf.fr.home.remote-servers). If the property exists and is well-formed, return
  //    a flattened sequence of label/url pairs.
  // 3. Otherwise the empty sequence is returned.
  //@XPathFunction
  def remoteServersXPath: SequenceIterator = {

    import FormRunnerHome._

    def fromCompatibility =
      remoteServerFromCompatibilityProperty map (List(_))

    def fromJSON =
      remoteServersFromJSONProperty map { values =>
        values flatMap { case (label, uri) => label :: uri :: Nil }
      }

    def fromEither =
      fromCompatibility orElse fromJSON getOrElse List.empty[String]

    fromEither
  }
}

object FormRunnerHome {

  def tryRemoteServersFromString(json: String): Try[Vector[(String, String)]] =
    parser.parse(json).toTry flatMap tryRemoteServersFromJSON

  def tryRemoteServersFromJSON(json: Json): Try[Vector[(String, String)]] = Try {
    json.asArray match {
      case Some(elements) =>
        elements flatMap (_.asObject) collect {
          case fields =>

            def getFieldOrThrow(key: String) =
              fields(key) flatMap (_.asString) flatMap trimAllToOpt getOrElse (throw new IllegalArgumentException)

            getFieldOrThrow("label") -> getFieldOrThrow("url").dropTrailingSlash
        }
      case _ =>
        throw new IllegalArgumentException
    }
  }

  private def remoteServersFromJSONProperty: Option[i.Seq[(String, String)]] =
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

  private def remoteServerFromCompatibilityProperty: Option[String] = (
    properties.getStringOrURIAsStringOpt("oxf.fr.production-server-uri")
    flatMap trimAllToOpt
    map     (_.dropTrailingSlash)
  )
}
