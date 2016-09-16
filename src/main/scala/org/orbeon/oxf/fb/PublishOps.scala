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

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.ScalaUtils.StringOps
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

import scala.util.control.NonFatal

trait PublishOps {

  // Publish a form and its attachments
  //@XPathFunction
  def publish(xhtml: NodeInfo, app: String, form: String, document: String, formVersion: String): Unit = {

    try {
      val (beforeURLs, _, publishedVersion) =
        putWithAttachments(
          data              = xhtml.root,
          toBaseURI         = "", // local publish
          fromBasePath      = createFormDataBasePath("orbeon", "builder", isDraft = false, document),
          toBasePath        = createFormDefinitionBasePath(app, form),
          filename          = "form.xhtml",
          commonQueryString = encodeSimpleQuery(List("document" → document)),
          forceAttachments  = false,
          // Using "next" for attachments works as attachments are saved first, and the persistence layer
          // uses the latest version of the published forms (not attachments) to figure what the next
          // version is
          formVersion       = formVersion.trimAllToOpt
        )
      setvalue(instanceRoot("fb-publish-instance").get / "published-attachments", beforeURLs.size.toString)
      setvalue(instanceRoot("fb-publish-instance").get / "published-version",     publishedVersion.toString)
      toggle("fb-publish-dialog-success")
    } catch {
      case NonFatal(t) ⇒
        toggle("fb-publish-dialog-error")
    }

    setfocus("fb-publish-dialog", includes = Set.empty, excludes = Set.empty)
  }
}
