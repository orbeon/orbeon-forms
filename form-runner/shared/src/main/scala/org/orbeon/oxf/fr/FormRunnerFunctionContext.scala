/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.XFormsFunction


object FormRunnerFunctionContext {

  private val AttachmentIdKey    = "attachment-id"
  private val PdfTemplateNameKey = "pdf-template-name"

  // Extend XFormsFunction.Context with Form Runner specific values
  implicit class FormRunnerFunctionContextOps(private val context: XFormsFunction.Context) extends AnyVal {

    def attachmentIdOpt   : Option[String] = context.customValues.get(AttachmentIdKey)
    def pdfTemplateNameOpt: Option[String] = context.customValues.get(PdfTemplateNameKey)

    def withAttachmentId(attachmentId: String): XFormsFunction.Context =
      context.copy(customValues = context.customValues + (AttachmentIdKey -> attachmentId))

    def withPdfTemplateName(pdfTemplateNameOpt: Option[String]): XFormsFunction.Context =
      context.copy(customValues = context.customValues ++ pdfTemplateNameOpt.map(PdfTemplateNameKey -> _))
  }
}
