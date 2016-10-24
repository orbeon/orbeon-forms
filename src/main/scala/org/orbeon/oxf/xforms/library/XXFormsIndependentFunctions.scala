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
package org.orbeon.oxf.xforms.library

import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.`type`.Type._
import org.orbeon.saxon.functions.{Serialize, Evaluate}
import org.orbeon.oxf.xforms.function.xxforms._
import org.orbeon.oxf.xml.OrbeonFunctionLibrary

/*
* Orbeon extension functions that don't depend on the XForms environment.
*/
trait XXFormsIndependentFunctions extends OrbeonFunctionLibrary {

  // Define in early definition of subclass
  val XXFormsIndependentFunctionsNS: Seq[String]

  // Some functions are independent but support checking the XForms document first. This configures this behavior.
  val tryXFormsDocument: Boolean

  Namespace(XXFormsIndependentFunctionsNS) {

    val tryXFormsDocumentOp = if (tryXFormsDocument) 1 else 0

    Fun("get-request-method", classOf[XXFormsGetRequestMethod], op = 0, min = 0, STRING, ALLOWS_ONE)

    Fun("get-portlet-mode", classOf[XXFormsGetPortletMode], op = 0, min = 0, STRING, ALLOWS_ONE)

    Fun("get-window-state", classOf[XXFormsGetWindowState], op = 0, min = 0, STRING, ALLOWS_ONE)

    Fun("get-request-path", classOf[XXFormsGetRequestPath], op = tryXFormsDocumentOp, 0, STRING, ALLOWS_ONE)

    Fun("get-request-header", classOf[XXFormsGetRequestHeader], op = tryXFormsDocumentOp, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("get-request-parameter", classOf[XXFormsGetRequestParameter], op = tryXFormsDocumentOp, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("get-session-attribute", classOf[XXFormsGetSessionAttribute], op = 0, min = 1, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("set-session-attribute", classOf[XXFormsSetSessionAttribute], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO,
      Arg(STRING, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("get-request-attribute", classOf[XXFormsGetRequestAttribute], op = 0, min = 1, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("set-request-attribute", classOf[XXFormsSetRequestAttribute], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO,
      Arg(STRING, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("username"                   , classOf[XXFormsUsername],              op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)
    Fun("get-remote-user"            , classOf[XXFormsUsername],              op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)
    Fun("user-group"                 , classOf[XXFormsUserGroup],             op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE)
    Fun("user-roles"                 , classOf[XXFormsUserRoles],             op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)
    Fun("user-organizations"         , classOf[XXFormsUserOrganizations],     op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE)

    Fun("user-ancestor-organizations", classOf[XXFormsAncestorOrganizations], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("is-user-in-role", classOf[XXFormsIsUserInRole], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("call-xpl", classOf[XXFormsCallXPL], op = 0, min = 4, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE),
      Arg(STRING, ALLOWS_ZERO_OR_MORE),
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("evaluate", classOf[Evaluate], op = Evaluate.EVALUATE, min = 1, max = 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("evaluate-avt", classOf[XXFormsEvaluateAVT], op = 0, min = 1, max = 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("serialize", classOf[Serialize], op = 0, min = 2, STRING, EXACTLY_ONE,
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_ONE),
      Arg(ITEM_TYPE, EXACTLY_ONE)
    )

    Fun("property", classOf[XXFormsProperty], op = 0, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("properties-start-with", classOf[XXFormsPropertiesStartsWith], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("decode-iso9075-14", classOf[XXFormsDecodeISO9075], op = 0, min = 1, STRING, ALLOWS_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("encode-iso9075-14", classOf[XXFormsEncodeISO9075], op = 0, min = 1, STRING, ALLOWS_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("doc-base64", classOf[XXFormsDocBase64], op = XXFormsDocBase64.DOC_BASE64, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("doc-base64-available", classOf[XXFormsDocBase64], op = XXFormsDocBase64.DOC_BASE64_AVAILABLE, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("form-urlencode", classOf[XXFormsFormURLEncode], op = 0, min = 1, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(NODE_TYPE, EXACTLY_ONE)
    )

    Fun("rewrite-resource-uri", classOf[XXFormsRewriteResourceURI], op = 0, min = 1, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("rewrite-service-uri", classOf[XXFormsRewriteServiceURI], op = 0, min = 1, STRING, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(BOOLEAN, EXACTLY_ONE)
    )

    Fun("has-class", classOf[XXFormsHasClass], op = 0, min = 1, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, EXACTLY_ONE),
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("classes", classOf[XXFormsClasses], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("split", classOf[XXFormsSplit], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("trim", classOf[XXFormsTrim], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_MORE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("is-blank", classOf[XXFormsIsBlank], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("non-blank", classOf[XXFormsNonBlank], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_MORE)
    )

    Fun("forall", classOf[XXFormsForall], op = 0, min = 2, BOOLEAN, EXACTLY_ONE,
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("exists", classOf[XXFormsExists], op = 0, min = 2, BOOLEAN, EXACTLY_ONE,
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, EXACTLY_ONE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
      Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    )

    Fun("image-metadata", classOf[XXFormsImageMetadata], op = 0, min = 2, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
      Arg(ITEM_TYPE, EXACTLY_ONE),
      Arg(STRING, EXACTLY_ONE)
    )

    Fun("json-to-xml", classOf[JsonStringToXml], op = 0, min = 0, NODE_TYPE, ALLOWS_ZERO_OR_ONE,
      Arg(STRING, ALLOWS_ZERO_OR_ONE)
    )

    Fun("xml-to-json", classOf[XmlToJsonString], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(NODE_TYPE, ALLOWS_ZERO_OR_ONE)
    )
  }
}