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
package org.orbeon.oxf.xforms.schema

import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.model.XFormsModelSchemaValidatorPlatform
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.xml.sax.InputSource

import java.net.URI


class MSVGrammarReaderController(
  containingDocument: XFormsContainingDocument,
  baseURL           : Option[String]
) extends MSVGrammarReaderControllerTrait {

  def resolveEntity(publicId: String, systemId: String): InputSource = {

    val resolvedUrlString =
      baseURL match {
        case Some(baseURL) =>
          URI.create(baseURL).resolve(systemId).toString
        case None if containingDocument != null =>
          XFormsCrossPlatformSupport.resolveServiceURL(containingDocument, null, systemId, UrlRewriteMode.Absolute)
        case None =>
          systemId
      }

    XFormsModelSchemaValidatorPlatform.resolveSchemaEntity(containingDocument, resolvedUrlString)
  }
}
