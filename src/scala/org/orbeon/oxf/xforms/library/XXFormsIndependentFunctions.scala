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
import org.orbeon.oxf.xforms.XFormsConstants

/*
* Orbeon extension functions that don't depend on the XForms environment.
*/
trait XXFormsIndependentFunctions extends OrbeonFunctionLibrary {
    
    Namespace(XFormsConstants.XXFORMS_NAMESPACE_URI) {
    
        // xxforms:get-request-path()
        Fun("get-request-path", classOf[XXFormsGetRequestPath], 0, 0, 0, STRING, ALLOWS_ONE)
    
        // xxforms:get-request-header()
        Fun("get-request-header", classOf[XXFormsGetRequestHeader], 0, 1, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:get-request-parameter()
        Fun("get-request-parameter", classOf[XXFormsGetRequestParameter], 0, 1, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:get-session-attribute()
        Fun("get-session-attribute", classOf[XXFormsGetSessionAttribute], 0, 1, 2, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:set-session-attribute()
        Fun("set-session-attribute", classOf[XXFormsSetSessionAttribute], 0, 2, 2, ITEM_TYPE, ALLOWS_ZERO,
            Arg(STRING, EXACTLY_ONE),
            Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        // xxforms:get-request-attribute()
        Fun("get-request-attribute", classOf[XXFormsGetRequestAttribute], 0, 1, 2, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:set-request-attribute()
        Fun("set-request-attribute", classOf[XXFormsSetRequestAttribute], 0, 2, 2, ITEM_TYPE, ALLOWS_ZERO,
            Arg(STRING, EXACTLY_ONE),
            Arg(ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        // xxforms:get-remote-user()
        Fun("get-remote-user", classOf[XXFormsGetRemoteUser], 0, 0, 0, STRING, ALLOWS_ZERO_OR_ONE)
    
        // xxforms:is-user-in-role(xs:string) as xs:boolean
        Fun("is-user-in-role", classOf[XXFormsIsUserInRole], 0, 1, 1, BOOLEAN, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:call-xpl
        Fun("call-xpl", classOf[XXFormsCallXPL], 0, 4, 4, NODE_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_MORE),
            Arg(NODE_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(STRING, ALLOWS_ZERO_OR_MORE)
        )
    
        // xxforms:evaluate
        Fun("evaluate", classOf[Evaluate], Evaluate.EVALUATE, 1, 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:evaluate-avt
        Fun("evaluate-avt", classOf[XXFormsEvaluateAVT], 0, 1, 10, ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:serialize
        Fun("serialize", classOf[Serialize], 0, 2, 2, STRING, EXACTLY_ONE,
            Arg(NODE_TYPE, ALLOWS_ZERO_OR_ONE),
            Arg(ITEM_TYPE, EXACTLY_ONE)
        )
    
        // xxforms:property
        Fun("property", classOf[XXFormsProperty], 0, 1, 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:properties-start-with
        Fun("properties-start-with", classOf[XXFormsPropertiesStartsWith], 0, 1, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:decode-iso9075
        Fun("decode-iso9075-14", classOf[XXFormsDecodeISO9075], 0, 1, 1, STRING, ALLOWS_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:encode-iso9075
        Fun("encode-iso9075-14", classOf[XXFormsEncodeISO9075], 0, 1, 1, STRING, ALLOWS_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:doc-base64
        Fun("doc-base64", classOf[XXFormsDocBase64], XXFormsDocBase64.DOC_BASE64, 1, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, ALLOWS_ZERO_OR_ONE)
        )
    
        // xxforms:doc-base64-available
        Fun("doc-base64-available", classOf[XXFormsDocBase64], XXFormsDocBase64.DOC_BASE64_AVAILABLE, 1, 1, BOOLEAN, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxforms:form-urlencode
        Fun("form-urlencode", classOf[XXFormsFormURLEncode], 0, 1, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(NODE_TYPE, EXACTLY_ONE)
        )
    
        // xxforms:rewrite-resource-uri()
        Fun("rewrite-resource-uri", classOf[XXFormsRewriteResourceURI], 0, 1, 2, STRING, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        // xxforms:rewrite-service-uri()
        Fun("rewrite-service-uri", classOf[XXFormsRewriteServiceURI], 0, 1, 2, STRING, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    }
}