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

import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.xforms.action.XFormsAPI.insert
import org.orbeon.oxf.fb.FormBuilder

trait FormRunnerHome {

    private def appForm(s: String) = {
        val parts = split[List](s, "/")
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

        val appFormsSet = split[List](selection) map appForm toSet

        def formIsSelected(form: NodeInfo) =
            appFormsSet((form elemValue "application-name") → (form elemValue "form-name"))

        collectForms(forms, formIsSelected)
    }

    def isLocal(form: NodeInfo)  = Form(form).local.isDefined
    def isRemote(form: NodeInfo) = Form(form).remote.isDefined

    def isLocalAvailable(form: NodeInfo)    = Form(form).local  exists(_.available)
    def isRemoteAvailable(form: NodeInfo)   = Form(form).remote exists(_.available)

    def isLocalUnavailable(form: NodeInfo)  = Form(form).local  exists(! _.available)
    def isRemoteUnavailable(form: NodeInfo) = Form(form).remote exists(! _.available)

    def isLocalNewer(form: NodeInfo) = Form(form).isLocalNewer
    def isRemoteNewer(form: NodeInfo) = Form(form).isRemoteNewer

    def canSelectUnpublishedLocal(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (f ⇒ f.isAdmin && f.isLocalUnavailable)

    def canSelectPublishedLocal(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (f ⇒ f.isAdmin && f.isLocalAvailable)

    def canSelectUnpublishedRemote(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (f ⇒ f.isAdmin && f.isRemoteUnavailable)

    def canSelectPublishedRemote(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (f ⇒ f.isAdmin && f.isRemoteAvailable)

    def canSelectLocalNewer(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (f ⇒ f.isAdmin && f.isLocalNewer)

    def canSelectRemoteNewer(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (f ⇒ f.isAdmin && f.isRemoteNewer)

    def canPublishLocal(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isLocalUnavailable)

    def canUnpublishLocal(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isLocalAvailable)

    def canPublishRemote(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isRemoteUnavailable)

    def canUnpublishRemote(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isRemoteAvailable)

    def canPublishLocalToRemote(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isLocal)

    def canPublishRemoteToLocal(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isRemote)

    def canNavigateSummary(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) exists (f ⇒ f.isLocalAvailable && f.isSummaryAllowed)

    def canNavigateNew(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) exists (f ⇒ f.isLocalAvailable && f.isNewAllowed)

    def canUpgradeLocal(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isLocal)

    def canUpgradeRemote(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) forall (_.isRemote)

    def publish(xhtml: NodeInfo, toBaseURI: String, app: String, form: String, username: String, password: String, forceAttachments: Boolean): Unit =
        putWithAttachments(
            data              = xhtml.root,
            toBaseURI         = toBaseURI,
            fromBasePath      = createFormDefinitionBasePath(app, form),
            toBasePath        = createFormDefinitionBasePath(app, form),
            filename          = "form.xhtml",
            commonQueryString = "",
            forceAttachments  = forceAttachments,
            username          = nonEmptyOrNone(username),
            password          = nonEmptyOrNone(password),
            formVersion       = Some("next")
        )

    // NOTE: It would be great if we could work on typed data, whether created from XML, JSON or an object
    // serialization. Here we juggle between XML and typed data.
    def joinLocalAndRemoteMetadata(local: SequenceIterator, remote: SequenceIterator, permissionInstance: NodeInfo): SequenceIterator = {

        val combinedIndexIterator = {

            def makeAppFormKey(node: NodeInfo) =
                (node elemValue "application-name", node elemValue "form-name")

            def createIndex(it: SequenceIterator) = asScalaIterator(it) collect {
                case node: NodeInfo ⇒ makeAppFormKey(node) → node
            } toMap

            val localIndex  = createIndex(local)
            val remoteIndex = createIndex(remote)

            (localIndex.keySet ++ remoteIndex.keySet).iterator map { key ⇒
                key → (localIndex.get(key), remoteIndex.get(key))
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

        def relevantFormNode(formNode: NodeInfo, formName: String, hasAdminPermissions: Boolean, operations: List[String]): Boolean =
            (formNode / * nonEmpty) &&
            (hasAdminPermissions || ! (
                formName == "library" ||
                operations.isEmpty    ||
                (formNode elemValue "available") == "false")
            )

        def addOperations(formNode: NodeInfo, operations: List[String]): Unit =
            insert(into = formNode, origin = attributeInfo("operations", operations mkString " "))

        import FormBuilder._

        for {
            ((app, form), localAndOrRemote) ← combinedIndexIterator
            hasAdminPermissions = hasAdminPermissionsFor(permissionInstance, app, form)
            newNode             = createNode(localAndOrRemote)
            permissionsElement  = (newNode / "permissions" headOption).orNull
            operations          = (hasAdminPermissions list "admin") ::: allAuthorizedOperationsAssumingOwnerGroupMember(permissionsElement).to[List]
            if relevantFormNode(newNode, form, hasAdminPermissions, operations)
        } yield {
            addOperations(newNode, operations)
            newNode
        }
    }
}
