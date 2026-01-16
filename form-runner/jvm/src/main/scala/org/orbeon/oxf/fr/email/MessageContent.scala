/**
 *  Copyright (C) 2025 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.email

import org.orbeon.oxf.fr.FormRunner.formRunnerProperty
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.FormRunnerParams
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps}
import org.orbeon.xforms.XFormsNames


case class MessageContent(content: String, html: Boolean)

case object MessageContent {
  def apply(nodeInfo: NodeInfo): MessageContent =
    MessageContent(
      content = nodeInfo.stringValue,
      html    = nodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType)
    )

  def apply(
    template        : EmailMetadata.Template,
    evaluatedParams : EvaluatedParams)(implicit
    formRunnerParams: FormRunnerParams
  ): MessageContent = {

    val messageContentFromTemplateOpt    = template.body.map(part =>  MessageContent(part.text, part.isHTML))
    lazy val messageContentFromResources = MessageContent((frc.currentFRResources / "email" / "body").head)
    val messageContent                   = messageContentFromTemplateOpt.getOrElse(messageContentFromResources)

    val contentWithParams = messageContent.copy(
      content = evaluatedParams.processedTemplate(messageContent.html, messageContent.content)
    )

    if (contentWithParams.html) {
      // Wrap HTML content along with inline CSS, if any
      val inlineCssOpt   = formRunnerProperty("oxf.fr.email.css.custom.inline").flatMap(_.trimAllToOpt)
      val inlineCss      = inlineCssOpt.map(css => s"<head><style type=\"text/css\">$css</style></head>").getOrElse("")
      val wrappedContent = s"<html>$inlineCss<body>${contentWithParams.content}</body></html>"

      contentWithParams.copy(content = wrappedContent)
    } else {
      // Non-wrapped, non-HTML content
      contentWithParams
    }
  }
}
