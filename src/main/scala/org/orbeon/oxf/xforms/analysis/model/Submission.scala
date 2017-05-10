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
import org.orbeon.oxf.xforms.analysis.{ChildrenActionsTrait, ElementAnalysis, SimpleElementAnalysis, StaticStateContext}
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.oxf.xml.XMLConstants

class Submission(
  staticStateContext: StaticStateContext,
  element: Element,
  parent: Option[ElementAnalysis],
  preceding: Option[ElementAnalysis],
  scope: Scope
) extends SimpleElementAnalysis(staticStateContext, element, parent, preceding, scope)
    with ChildrenActionsTrait {

  // `resource` has precedence over `action`
  val avtActionOrResource =
    element.attributeValueOpt(XFormsConstants.RESOURCE_QNAME) orElse
      element.attributeValueOpt("action")                     getOrElse
      (throw new ValidationException("xf:submission: action attribute or resource attribute is missing.", locationData))

  val avtMethod =
    element.attributeValueOpt("method") getOrElse
    (throw new ValidationException("xf:submission: method attribute is missing.", locationData))

  val avtValidate         = element.attributeValue("validate")
  val avtRelevant         = element.attributeValue("relevant")
  val avtXXFormsCalculate = element.attributeValue(XFormsConstants.XXFORMS_CALCULATE_QNAME)
  val avtXXFormsUploads   = element.attributeValue(XFormsConstants.XXFORMS_UPLOADS_QNAME)
  val avtXXFormsAnnotate  = element.attributeValue(XFormsConstants.XXFORMS_ANNOTATE_QNAME)

  val avtSerialization    = element.attributeValue("serialization")

  // `targetref` is the new name as of May 2009, and `target` is still supported for backward compatibility.
  // This is an XPath expression when used with `replace="instance|text"` (other meaning possible post-XForms 1.1 for `replace="all"`).
  val targetref =
    element.attributeValueOpt("targetref") orElse
      element.attributeValueOpt(XFormsConstants.TARGET_QNAME)

  val avtMode               = element.attributeValue("mode")
  val avtVersion            = element.attributeValue("version")
  val avtIndent             = element.attributeValue("indent")
  val avtMediatype          = element.attributeValue("mediatype")
  val avtEncoding           = element.attributeValue("encoding")
  val avtOmitxmldeclaration = element.attributeValue("omit-xml-declaration")
  val avtStandalone         = element.attributeValue("standalone")

  val avtReplace =
    element.attributeValueOpt("replace") getOrElse
      XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL

  val replaceInstanceId     = element.attributeValue("instance")
  val xxfReplaceInstanceId  = element.attributeValue(XFormsConstants.XXFORMS_INSTANCE_QNAME)

  // XForms 1.1 changes back the default to the ampersand as of February 2009
  val avtSeparator =
    element.attributeValueOpt("separator") getOrElse "&"

  // Extension attributes
  val avtXXFormsUsername       = element.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME)
  val avtXXFormsPassword       = element.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME)
  val avtXXFormsPreemptiveAuth = element.attributeValue(XFormsConstants.XXFORMS_PREEMPTIVE_AUTHENTICATION_QNAME)
  val avtXXFormsDomain         = element.attributeValue(XFormsConstants.XXFORMS_DOMAIN_QNAME)

  val avtXXFormsReadonly       = element.attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME)
  val avtXXFormsDefaults       = element.attributeValue(XFormsConstants.XXFORMS_DEFAULTS_QNAME)
  val avtXXFormsShared         = element.attributeValue(XFormsConstants.XXFORMS_SHARED_QNAME)
  val avtXXFormsCache          = element.attributeValue(XFormsConstants.XXFORMS_CACHE_QNAME)

  val avtXXFormsTarget         = element.attributeValue(XFormsConstants.XXFORMS_TARGET_QNAME) // TODO: `Option`.
  val avtXXFormsHandleXInclude = element.attributeValue(XFormsConstants.XXFORMS_XINCLUDE)

  val xxfShowProgress          = ! ("false" == element.attributeValue(XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME))

  val fURLNorewrite = XFormsUtils.resolveUrlNorewrite(element)
  val urlType       = element.attributeValue(XMLConstants.FORMATTING_URL_TYPE_QNAME)

  // `cdata-section-elements`
  // `includenamespaceprefixes`
}