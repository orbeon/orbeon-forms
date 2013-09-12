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

    Namespace(XXFormsIndependentFunctionsNS) {

        Fun("get-request-method", classOf[XXFormsGetRequestMethod], 0, 0, STRING, ALLOWS_ONE)

        Fun("get-portlet-mode", classOf[XXFormsGetPortletMode], 0, 0, STRING, ALLOWS_ONE)

        Fun("get-window-state", classOf[XXFormsGetWindowState], 0, 0, STRING, ALLOWS_ONE)
    
        Fun("get-request-path", classOf[XXFormsGetRequestPath], 0, 0, STRING, ALLOWS_ONE)
    
        Fun("get-request-header", classOf[XXFormsGetRequestHeader], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("get-request-parameter", classOf[XXFormsGetRequestParameter], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("get-session-attribute", classOf[XXFormsGetSessionAttribute], 0, 1, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("set-session-attribute", classOf[XXFormsSetSessionAttribute], 0, 2, ITEM_TYPE, ALLOWS_ZERO,
            Arg(STRING, EXACTLY_ONE),
            Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        Fun("get-request-attribute", classOf[XXFormsGetRequestAttribute], 0, 1, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("set-request-attribute", classOf[XXFormsSetRequestAttribute], 0, 2, ITEM_TYPE, ALLOWS_ZERO,
            Arg(STRING, EXACTLY_ONE),
            Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        Fun("get-remote-user", classOf[XXFormsGetRemoteUser], 0, 0, STRING, ALLOWS_ZERO_OR_ONE)
    
        Fun("is-user-in-role", classOf[XXFormsIsUserInRole], 0, 1, BOOLEAN, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("call-xpl", classOf[XXFormsCallXPL], 0, 4, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_MORE),
            Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(STRING, ALLOWS_ZERO_OR_MORE)
        )

        Fun("evaluate", classOf[Evaluate], Evaluate.EVALUATE, 1, 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )

        Fun("evaluate-avt", classOf[XXFormsEvaluateAVT], 0, 1, 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("serialize", classOf[Serialize], 0, 2, STRING, EXACTLY_ONE,
            Arg(NODE_TYPE, ALLOWS_ZERO_OR_ONE),
            Arg(ITEM_TYPE, EXACTLY_ONE)
        )
    
        Fun("property", classOf[XXFormsProperty], 0, 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("properties-start-with", classOf[XXFormsPropertiesStartsWith], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("decode-iso9075-14", classOf[XXFormsDecodeISO9075], 0, 1, STRING, ALLOWS_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("encode-iso9075-14", classOf[XXFormsEncodeISO9075], 0, 1, STRING, ALLOWS_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("doc-base64", classOf[XXFormsDocBase64], XXFormsDocBase64.DOC_BASE64, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, ALLOWS_ZERO_OR_ONE)
        )
    
        Fun("doc-base64-available", classOf[XXFormsDocBase64], XXFormsDocBase64.DOC_BASE64_AVAILABLE, 1, BOOLEAN, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("form-urlencode", classOf[XXFormsFormURLEncode], 0, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(NODE_TYPE, EXACTLY_ONE)
        )
    
        Fun("rewrite-resource-uri", classOf[XXFormsRewriteResourceURI], 0, 1, STRING, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        Fun("rewrite-service-uri", classOf[XXFormsRewriteServiceURI], 0, 1, STRING, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )

        Fun("has-class", classOf[XXFormsHasClass], 0, 1, BOOLEAN, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        Fun("classes", classOf[XXFormsClasses], 0, 0, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        Fun("split", classOf[XXFormsSplit], 0, 0, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, ALLOWS_ZERO_OR_MORE),
            Arg(STRING, EXACTLY_ONE)
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
    }
}