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

import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.oxf.xforms.function.xxforms._
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.oxf.xforms.function.{Event, If}
import org.orbeon.oxf.xforms.function.exforms.EXFormsMIP

/*
 * Orbeon extension functions that depend on the XForms environment.
 */
trait XXFormsEnvFunctions extends OrbeonFunctionLibrary {

    // Define in early definition of subclass
    val XXFormsEnvFunctionsNS: Seq[String]

    Namespace(XXFormsEnvFunctionsNS) {
    
        // xxf:event()
        // NOTE: This is deprecated and just points to the event() function.
        Fun("event", classOf[Event], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("case", classOf[XXFormsCase], 0, 1, STRING, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )

        Fun("cases", classOf[XXFormsCases], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:repeat-current
        Fun("repeat-current", classOf[XXFormsRepeatCurrent], 0, 0, Type.NODE_TYPE, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:repeat-position
        Fun("repeat-position", classOf[XXFormsRepeatPosition], 0, 0, INTEGER, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:context
        Fun("context", classOf[XXFormsContext], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:repeat-nodeset
        Fun("repeat-nodeset", classOf[XXFormsRepeatNodeset], 0, 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )

        // xxf:bind
        Fun("bind", classOf[XXFormsBind], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:bind-property
        Fun("evaluate-bind-property", classOf[XXFormsEvaluateBindProperty], 0, 2, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(ANY_ATOMIC, EXACTLY_ONE) // QName or String
        )
    
        // xxf:valid
        Fun("valid", classOf[XXFormsValid], 0, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(BOOLEAN, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        // xxf:type
        Fun("type", classOf[XXFormsType], 0, 0, QNAME, ALLOWS_ZERO_OR_MORE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        // xxf:custom-mip
        Fun("custom-mip", classOf[XXFormsCustomMIP], 0, 2, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:get-request-parameter()
        Fun("invalid-binds", classOf[XXFormsInvalidBinds], 0, 0, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        // xxf:if
        Fun("if", classOf[If], 0, 3, STRING, EXACTLY_ONE,
            Arg(BOOLEAN, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:binding
        Fun("binding", classOf[XXFormsBinding], 0, 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )

        // xxf:binding-context
        Fun("binding-context", classOf[XXFormsBindingContext], 0, 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )

        // xxf:value
        Fun("value", classOf[XXFormsValue], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:mutable
        Fun("mutable-document", classOf[XXFormsMutableDocument], 0, 1, Type.NODE_TYPE, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, EXACTLY_ONE)
        )
    
        // xxf:component-context
        Fun("component-context", classOf[XXFormsComponentContext], 0, 0, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    
        // xxf:instance
        Fun("instance", classOf[XXFormsInstance], 0, 1, Type.NODE_TYPE, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        // xxf:index
        Fun("index", classOf[XXFormsIndex], 0, 0, INTEGER, EXACTLY_ONE,
            Arg(STRING, ALLOWS_ZERO_OR_ONE)
        )
    
        // xxf:list-models
        Fun("list-models", classOf[XXFormsListModels], 0, 0, STRING, ALLOWS_ZERO_OR_MORE)
    
        // xxf:list-instances
        Fun("list-instances", classOf[XXFormsListInstances], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:list-variables
        Fun("list-variables", classOf[XXFormsListVariables], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:get-variable
        Fun("get-variable", classOf[XXFormsGetVariable], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:itemset()
        Fun("itemset", classOf[XXFormsItemset], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        // xxf:format-message()
        Fun("format-message", classOf[XXFormsFormatMessage], 0, 2, STRING, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        // xxf:lang()
        Fun("lang", classOf[XXFormsLang], 0, 0, STRING, ALLOWS_ZERO_OR_ONE)

        // xxf:r()
        Fun("r", classOf[XXFormsResource], 0, 0, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:pending-uploads()
        Fun("pending-uploads", classOf[XXFormsPendingUploads], 0, 0, INTEGER, EXACTLY_ONE)
    
        // xxf:document-id()
        Fun("document-id", classOf[XXFormsDocumentId], 0, 0, STRING, EXACTLY_ONE)
    
        // xxf:document
        Fun("create-document", classOf[XXFormsCreateDocument], 0, 0, Type.NODE_TYPE, EXACTLY_ONE)
    
        // xxf:label, xxf:help, xxf:hint, xxf:alert
        Fun("label", classOf[XXFormsLHHA], 0, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
        Fun("help", classOf[XXFormsLHHA], 1, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
        Fun("hint", classOf[XXFormsLHHA], 2, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
        Fun("alert", classOf[XXFormsLHHA], 3, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )

        // xxf:visited
        Fun("visited", classOf[XXFormsVisited], 0, 1, BOOLEAN, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )

        // xxf:absolute-id
        Fun("absolute-id", classOf[XXFormsAbsoluteId], 0, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:control-element
        Fun("control-element", classOf[XXFormsControlElement], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        // xxf:extract-document
        Fun("extract-document", classOf[XXFormsExtractDocument], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
            Arg(Type.NODE_TYPE, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        // xxf:sort()
        // RFE: Support XSLT 2.0-features such as multiple sort keys
        Fun("sort", classOf[XXFormsSort], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(Type.ITEM_TYPE, EXACTLY_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE)
        )

        // xxf:relevant() (NOTE: also from exforms)
        Fun("relevant", classOf[EXFormsMIP], 0, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        // xxf:readonly() (NOTE: also from exforms)
        Fun("readonly", classOf[EXFormsMIP], 1, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        // xxf:required() (NOTE: also from exforms)
        Fun("required", classOf[EXFormsMIP], 2, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    }
}