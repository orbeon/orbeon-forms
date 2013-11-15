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
package org.orbeon.oxf.fb

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML
import XML._
import org.orbeon.oxf.fr.FormRunner._
import scala.util.control.NonFatal
import org.orbeon.oxf.xforms.action.XFormsAPI._

trait PublishOps {

    // Publish a form and its attachments
    def publish(xhtml: NodeInfo, app: String, form: String, document: String): Unit = {

        try {
            val attachments =
                putWithAttachments(
                    data                   = xhtml.root,
                    toBaseURI              = "", // local publish
                    fromBasePath           = createFormDataBasePath("orbeon", "builder", isDraft = false, document),
                    toBasePath             = createFormDefinitionBasePath(app, form),
                    filename               = "form.xhtml",
                    commonQueryString      = s"document=$document",
                    forceAttachments       = false,
                    dataFormVersion        = Some("next"),
                    attachmentsFormVersion = Some("next")
                )
            setvalue(instanceRoot("fb-publish-instance").get / "attachments", attachments._1.size.toString)
            toggle("fb-publish-dialog-success")
        } catch {
            case NonFatal(t) ⇒
                toggle("fb-publish-dialog-error")
        }

        setfocus("fb-publish-dialog")
    }
}
