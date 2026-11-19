/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis.model


import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, WithChildrenTrait}
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

class Submission(
  index            : Int,
  elem             : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(
  index,
  elem,
  parent,
  preceding,
  staticId,
  prefixedId,
  namespaceMapping,
  scope,
  containerScope
) with WithChildrenTrait {

  // `resource` has precedence over `action`
  val avtActionOrResource =
    elem.attributeValueOpt(RESOURCE_QNAME) orElse
      elem.attributeValueOpt("action")     getOrElse (
        throw new ValidationException(
          "xf:submission: `action` attribute or `resource` attribute is missing.",
          locationData
        )
      )

  val avtMethod                  = elem.attributeValueOpt("method")

  val avtValidateOpt             = elem.attributeValueOpt("validate")
  val avtRelevantOpt             = elem.attributeValueOpt("relevant") // backward compatibility
  val avtNonRelevantOpt          = elem.attributeValueOpt("nonrelevant")
  val avtXxfCalculateOpt         = elem.attributeValueOpt(XXFORMS_CALCULATE_QNAME)
  val avtXxfUploadsOpt           = elem.attributeValueOpt(XXFORMS_UPLOADS_QNAME)
  val avtXxfRelevantAttOpt       = elem.attributeValueOpt(XXFORMS_RELEVANT_ATTRIBUTE_QNAME)
  val avtXxfKeepMipQNameOpt      = elem.attributeValueOpt(XXFORMS_KEEP_MIP_QNAME)
  val avtXxfAnnotateOpt          = elem.attributeValueOpt(XXFORMS_ANNOTATE_QNAME)

  val avtSerializationOpt        = elem.attributeValueOpt("serialization")
  val serializeOpt               = elem.attributeValueOpt("serialize")
  val targetrefOpt               = elem.attributeValueOpt("targetref") orElse elem.attributeValueOpt(TARGET_QNAME) // `target`: backward compatibility
  val avtModeOpt                 = elem.attributeValueOpt("mode")
  val avtResponseMustAwaitOpt    = elem.attributeValueOpt(XXFORMS_RESPONSE_MUST_AWAIT_QNAME)

  val avtVersionOpt              = elem.attributeValueOpt("version")
  val avtIndentOpt               = elem.attributeValueOpt("indent")
  val avtMediatypeOpt            = elem.attributeValueOpt("mediatype")
  val avtEncodingOpt             = elem.attributeValueOpt("encoding")
  val avtOmitXmlDeclarationOpt   = elem.attributeValueOpt("omit-xml-declaration")
  val avtStandalone              = elem.attributeValueOpt("standalone")

  val avtReplaceOpt              = elem.attributeValueOpt("replace")
  val replaceInstanceIdOpt       = elem.attributeValueOpt("instance")
  val xxfReplaceInstanceIdOpt    = elem.attributeValueOpt(XXFORMS_INSTANCE_QNAME)

  val avtSeparatorOpt            = elem.attributeValueOpt("separator")

  // Extension: credentials
  val avtXxfUsernameOpt          = elem.attributeValueOpt(XXFORMS_USERNAME_QNAME)
  val avtXxfPasswordOpt          = elem.attributeValueOpt(XXFORMS_PASSWORD_QNAME)
  val avtXxfPreemptiveAuthOpt    = elem.attributeValueOpt(XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME)
  val avtXxfDomainOpt            = elem.attributeValueOpt(XXFORMS_DOMAIN_QNAME)

  val avtXxfReadonlyOpt          = elem.attributeValueOpt(XXFORMS_READONLY_ATTRIBUTE_QNAME)
  val avtXxfDefaultsOpt          = elem.attributeValueOpt(XXFORMS_DEFAULTS_QNAME)
  val avtXxfSharedOpt            = elem.attributeValueOpt(XXFORMS_SHARED_QNAME)
  val avtXxfCacheOpt             = elem.attributeValueOpt(XXFORMS_CACHE_QNAME)
  val timeToLive                 = Instance.timeToLiveOrDefault(elem)

  val avtXxfTargetOpt            = elem.attributeValueOpt(XXFORMS_TARGET_QNAME)
  val avtXxfShowProgressOpt      = elem.attributeValueOpt(XXFORMS_SHOW_PROGRESS_QNAME)
  val avtXxfHandleXInclude       = elem.attributeValueOpt(XXFORMS_XINCLUDE)

  val avtUrlNorewrite            = elem.attributeValueOpt(XMLConstants.FORMATTING_URL_NOREWRITE_QNAME)
  val avtUrlType                 = elem.attributeValueOpt(XMLConstants.FORMATTING_URL_TYPE_QNAME)

  val avtResponseUrlType         = elem.attributeValueOpt(XXFORMS_RESPONSE_URL_TYPE)

  // `cdata-section-elements`
  // `includenamespaceprefixes`
}