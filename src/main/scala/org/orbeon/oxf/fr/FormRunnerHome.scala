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

import org.orbeon.oxf.fr.FormRunner.{dropTrailingSlash ⇒ _, _}
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.StringReplacer._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI.{insert, _}
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.XML._

import scala.collection.{immutable ⇒ i}
import scala.util.Try
trait FormRunnerHome {

  private def appForm(s: String) = {
    val parts = s.splitTo[List]("/")
    parts(0) → parts(1)
  }

  private case class AvailableAndTime(available: Boolean, time: Long)

  private case class Form(app: String, form: String, local: Option[AvailableAndTime], remote: Option[AvailableAndTime], ops: Set[String]) {

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
        form elemValue "application-name",
        form elemValue "form-name",
        localTime  map (v ⇒ AvailableAndTime((form elemValue "available")        != "false", DateUtils.parseISODateOrDateTime(v))),
        remoteTime map (v ⇒ AvailableAndTime((form elemValue "remote-available") != "false", DateUtils.parseISODateOrDateTime(v))),
        stringToSet(form /@ "operations")
      )
    }
  }

  private def collectForms(forms: SequenceIterator, p: NodeInfo ⇒ Boolean = _ ⇒ true) =
    asScalaIterator(forms) collect { case form: NodeInfo if p(form) ⇒ Form(form) }

  private def formsForSelection(selection: String, forms: SequenceIterator) = {

    val appFormsSet = selection.splitTo[List]() map appForm toSet

    def formIsSelected(form: NodeInfo) =
      appFormsSet((form elemValue "application-name") → (form elemValue "form-name"))

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
    collectForms(forms) exists (f ⇒ f.isAdmin && f.isLocalUnavailable)

  //@XPathFunction
  def canSelectPublishedLocal(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f ⇒ f.isAdmin && f.isLocalAvailable)

  //@XPathFunction
  def canSelectUnpublishedRemote(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f ⇒ f.isAdmin && f.isRemoteUnavailable)

  //@XPathFunction
  def canSelectPublishedRemote(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f ⇒ f.isAdmin && f.isRemoteAvailable)

  //@XPathFunction
  def canSelectLocalNewer(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f ⇒ f.isAdmin && f.isLocalNewer)

  //@XPathFunction
  def canSelectRemoteNewer(selection: String, forms: SequenceIterator) =
    collectForms(forms) exists (f ⇒ f.isAdmin && f.isRemoteNewer)

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
    formsForSelection(selection, forms) exists (f ⇒ f.isLocalAvailable && f.isSummaryAllowed)

  //@XPathFunction
  def canNavigateNew(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) exists (f ⇒ f.isLocalAvailable && f.isNewAllowed)

  //@XPathFunction
  def canUpgradeLocal(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isLocal)

  //@XPathFunction
  def canUpgradeRemote(selection: String, forms: SequenceIterator) =
    formsForSelection(selection, forms) forall (_.isRemote)

  //@XPathFunction
  def publish(
    xhtml            : NodeInfo,
    toBaseURI        : String,
    app              : String,
    form             : String,
    username         : String,
    password         : String,
    forceAttachments : Boolean,
    formVersion      : String
  ): Unit =
    putWithAttachments(
      data              = xhtml.root,
      toBaseURI         = toBaseURI,
      fromBasePath      = createFormDefinitionBasePath(app, form),
      toBasePath        = createFormDefinitionBasePath(app, form),
      filename          = "form.xhtml",
      commonQueryString = "",
      forceAttachments  = forceAttachments,
      username          = username.trimAllToOpt,
      password          = password.trimAllToOpt,
      formVersion       = formVersion.trimAllToOpt
    )

  // NOTE: It would be great if we could work on typed data, whether created from XML, JSON or an object
  // serialization. Here we juggle between XML and typed data.
  //@XPathFunction
  def joinLocalAndRemoteMetadata(
    local              : SequenceIterator,
    remote             : SequenceIterator,
    permissionInstance : NodeInfo
  ): SequenceIterator = {

    val combinedIndexIterator = {

      def makeAppFormKey(node: NodeInfo) =
        (node elemValue "application-name", node elemValue "form-name")

      def createIndex(it: SequenceIterator) = asScalaIterator(it) collect {
        case node: NodeInfo ⇒ makeAppFormKey(node) → node
      } toMap

      val localIndex = createIndex(local)
      val remoteIndex = createIndex(remote)

      (localIndex.keySet ++ remoteIndex.keySet).iterator map { key ⇒
        key →(localIndex.get(key), remoteIndex.get(key))
      }
    }

    def createNode(localAndOrRemote: (Option[NodeInfo], Option[NodeInfo])): NodeInfo = {

      def remoteElements(remoteNode: NodeInfo) = {

        def remoteElement(name: String) =
          elementInfo("remote-" + name, stringToStringValue(remoteNode elemValue name))

        List(
          remoteElement("title"),
          remoteElement("available"),
          remoteElement("last-modified-time")
        )
      }

      localAndOrRemote match {
        case (Some(localNode), None) ⇒
          localNode
        case (None, Some(remoteNode)) ⇒
          // Don't just use remoteNode, because we need `remote-` prefixes for remote data
          elementInfo("form", (remoteNode / "application-name" head) :: (remoteNode / "form-name" head) :: remoteElements(remoteNode))
        case (Some(localNode), Some(remoteNode)) ⇒
          insert(origin = remoteElements(remoteNode), into = localNode, after = localNode / *, doDispatch = false)
          localNode
        case (None, None) ⇒
          throw new IllegalStateException
      }
    }

    for (((app, form), localAndOrRemote) ← combinedIndexIterator)
      yield createNode(localAndOrRemote)
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
      remoteServersFromJSONProperty map { values ⇒
        values flatMap { case (label, uri) ⇒ label :: uri :: Nil }
      }

    def fromEither =
      fromCompatibility orElse fromJSON getOrElse List.empty[String]

    fromEither
  }
}

object FormRunnerHome {

  import spray.json._

  def tryRemoteServersFromString(json: String) =
    Try(json.parseJson) flatMap tryRemoteServersFromJSON

  def tryRemoteServersFromJSON(json: JsValue) = Try {
    json match {
      case JsArray(elements) ⇒
        elements collect {
          case JsObject(fields) ⇒

            def stringValueOrThrow(v: JsValue) = (
              collectByErasedType[JsString](v)
              map       (_.value)
              flatMap   trimAllToOpt
              getOrElse (throw new IllegalArgumentException)
            )

            stringValueOrThrow(fields("label")) → dropTrailingSlash(stringValueOrThrow(fields("url")))
        }
      case other ⇒
        throw new IllegalArgumentException
    }
  }

  private def remoteServersFromJSONProperty: Option[i.Seq[(String, String)]] =
    Option(properties.getProperty("oxf.fr.home.remote-servers")) map { property ⇒
      Try(property.associatedValue(_.value.toString.parseJson)) flatMap tryRemoteServersFromJSON getOrElse {
        implicit val logger = inScopeContainingDocument.getIndentedLogger("form-runner")
        warn(
          s"incorrect JSON configuration for property `oxf.fr.home.remote-servers`",
          Seq("JSON" → property.value.toString)
        )
        Nil
      }
    }

  private def remoteServerFromCompatibilityProperty: Option[String] = (
    Option(properties.getStringOrURIAsString("oxf.fr.production-server-uri"))
    flatMap trimAllToOpt
    map     dropTrailingSlash
  )
}
