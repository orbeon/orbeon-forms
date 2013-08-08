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

trait FormRunnerHome {

    private def appForm(s: String) = {
        val parts = split[List](s, "/")
        parts(0) → parts(1)
    }

    private case class AvailableAndTime(available: Boolean, time: Long)

    private case class Form(app: String, form: String, local: Option[AvailableAndTime], remote: Option[AvailableAndTime], ops: Set[String]) {
        def isLocalAvailable    = local  exists (_.available)
        def isRemoteAvailable   = remote exists (_.available)
        def isLocalUnavailable  = local  exists (! _.available)
        def isRemoteUnavailable = remote exists (! _.available)

        def isLocal = local.isDefined
        def isRemote = remote.isDefined

        def isLocalNewer  = isLocal && isRemote && local.get.time  > remote.get.time
        def isRemoteNewer = isLocal && isRemote && remote.get.time > local.get.time
    }

    private object Form {
        def apply(form: NodeInfo): Form = {
            val localTime  = form / "last-modified-time"
            val remoteTime = form / "remote-last-modified-time"

            Form(form / "application-name" stringValue,
                form / "form-name" stringValue,
                localTime.nonEmpty  option AvailableAndTime(! (form / "available"        === "false"), DateUtils.parseISODateOrDateTime(localTime.stringValue)),
                remoteTime.nonEmpty option AvailableAndTime(! (form / "remote-available" === "false"), DateUtils.parseISODateOrDateTime(remoteTime.stringValue)),
                stringToSet(form /@ "operations")
            )
        }
    }

    private def collectNodes(forms: SequenceIterator) =
        asScalaIterator(forms) collect { case form: NodeInfo ⇒ form }

    private def collectForms(forms: SequenceIterator, p: NodeInfo ⇒ Boolean = _ ⇒ true) =
        asScalaIterator(forms) collect { case form: NodeInfo if p(form) ⇒ Form(form) }

    private def formsForSelection(selection: String, forms: SequenceIterator) = {

        val appFormsSet = split[List](selection) map appForm toSet

        def formIsSelected(form: NodeInfo) =
            appFormsSet((form / "application-name" stringValue) → (form / "form-name" stringValue))

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
        collectForms(forms) exists (_.isLocalUnavailable)

    def canSelectPublishedLocal(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (_.isLocalAvailable)

    def canSelectUnpublishedRemote(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (_.isRemoteUnavailable)

    def canSelectPublishedRemote(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (_.isRemoteAvailable)

    def canSelectLocalNewer(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (_.isLocalNewer)

    def canSelectRemoteNewer(selection: String, forms: SequenceIterator) =
        collectForms(forms) exists (_.isRemoteNewer)

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

    private val SummaryOps = Set("*", "update", "read", "delete")
    private val NewOps     = Set("*", "create")

    def canNavigateSummary(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) exists (f ⇒ f.isLocal && (f.ops intersect SummaryOps nonEmpty))
    
    def canNavigateNew(selection: String, forms: SequenceIterator) =
        formsForSelection(selection, forms) exists (f ⇒ f.isLocal && (f.ops intersect NewOps nonEmpty))

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
            password          = nonEmptyOrNone(password)
        )
}
