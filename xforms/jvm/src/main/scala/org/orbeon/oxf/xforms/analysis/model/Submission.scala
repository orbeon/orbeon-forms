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

import org.orbeon.dom.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.analysis.{WithChildrenTrait, ElementAnalysis, SimpleElementAnalysis, StaticStateContext}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.oxf.xml.XMLConstants

class Submission(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends SimpleElementAnalysis(
  staticStateContext,
  element,
  parent,
  preceding,
  scope
) with WithChildrenTrait {

  // `resource` has precedence over `action`
  val avtActionOrResource =
    element.attributeValueOpt(RESOURCE_QNAME) orElse
      element.attributeValueOpt("action")     getOrElse (
        throw new ValidationException(
          "xf:submission: `action` attribute or `resource` attribute is missing.",
          locationData
        )
      )

  val avtMethod                  = element.attributeValueOpt("method")

  val avtValidateOpt             = element.attributeValueOpt("validate")
  val avtRelevantOpt             = element.attributeValueOpt("relevant") // backward compatibility
  val avtNonRelevantOpt          = element.attributeValueOpt("nonrelevant")
  val avtXxfCalculateOpt         = element.attributeValueOpt(XXFORMS_CALCULATE_QNAME)
  val avtXxfUploadsOpt           = element.attributeValueOpt(XXFORMS_UPLOADS_QNAME)
  val avtXxfRelevantAttOpt       = element.attributeValueOpt(XXFORMS_RELEVANT_ATTRIBUTE_QNAME)
  val avtXxfAnnotateOpt          = element.attributeValueOpt(XXFORMS_ANNOTATE_QNAME)

  val avtSerializationOpt        = element.attributeValueOpt("serialization")
  val serializeOpt               = element.attributeValueOpt("serialize")
  val targetrefOpt               = element.attributeValueOpt("targetref") orElse element.attributeValueOpt(TARGET_QNAME) // `target`: backward compatibility
  val avtModeOpt                 = element.attributeValueOpt("mode")

  val avtVersionOpt              = element.attributeValueOpt("version")
  val avtIndentOpt               = element.attributeValueOpt("indent")
  val avtMediatypeOpt            = element.attributeValueOpt("mediatype")
  val avtEncodingOpt             = element.attributeValueOpt("encoding")
  val avtOmitXmlDeclarationOpt   = element.attributeValueOpt("omit-xml-declaration")
  val avtStandalone              = element.attributeValueOpt("standalone")

  val avtReplaceOpt              = element.attributeValueOpt("replace")
  val replaceInstanceIdOrNull    = element.attributeValue("instance")
  val xxfReplaceInstanceIdOrNull = element.attributeValue(XXFORMS_INSTANCE_QNAME)

  val avtSeparatorOpt            = element.attributeValueOpt("separator")

  // Extension: credentials
  val avtXxfUsernameOpt          = element.attributeValueOpt(XXFORMS_USERNAME_QNAME)
  val avtXxfPasswordOpt          = element.attributeValueOpt(XXFORMS_PASSWORD_QNAME)
  val avtXxfPreemptiveAuthOpt    = element.attributeValueOpt(XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME)
  val avtXxfDomainOpt            = element.attributeValueOpt(XXFORMS_DOMAIN_QNAME)

  val avtXxfReadonlyOpt          = element.attributeValueOpt(XXFORMS_READONLY_ATTRIBUTE_QNAME)
  val avtXxfDefaultsOpt          = element.attributeValueOpt(XXFORMS_DEFAULTS_QNAME)
  val avtXxfSharedOpt            = element.attributeValueOpt(XXFORMS_SHARED_QNAME)
  val avtXxfCacheOpt             = element.attributeValueOpt(XXFORMS_CACHE_QNAME)
  val timeToLive                 = Instance.timeToLiveOrDefault(element)

  val avtXxfTargetOpt            = element.attributeValueOpt(XXFORMS_TARGET_QNAME)
  val avtXxfShowProgressOpt      = element.attributeValueOpt(XXFORMS_SHOW_PROGRESS_QNAME)
  val avtXxfHandleXInclude       = element.attributeValueOpt(XXFORMS_XINCLUDE)

  val avtUrlNorewrite            = element.attributeValueOpt(XMLConstants.FORMATTING_URL_NOREWRITE_QNAME)
  val avtUrlType                 = element.attributeValueOpt(XMLConstants.FORMATTING_URL_TYPE_QNAME)

  val avtResponseUrlType         = element.attributeValueOpt(XXFORMS_RESPONSE_URL_TYPE)

  // `cdata-section-elements`
  // `includenamespaceprefixes`
}