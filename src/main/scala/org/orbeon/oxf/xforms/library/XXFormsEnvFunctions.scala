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
import org.orbeon.oxf.xforms.function.{Bind, XXFormsValid, Event, If}
import org.orbeon.oxf.xforms.function.exforms.EXFormsMIP

/*
 * Orbeon extension functions that depend on the XForms environment.
 */
trait XXFormsEnvFunctions extends OrbeonFunctionLibrary {

    // Define in early definition of subclass
    val XXFormsEnvFunctionsNS: Seq[String]

    Namespace(XXFormsEnvFunctionsNS) {

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

        Fun("repeat-current", classOf[XXFormsRepeatCurrent], 0, 0, Type.NODE_TYPE, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )

        Fun("repeat-position", classOf[XXFormsRepeatPosition], 0, 0, INTEGER, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("context", classOf[XXFormsContext], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("repeat-nodeset", classOf[XXFormsRepeatNodeset], 0, 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("evaluate-bind-property", classOf[XXFormsEvaluateBindProperty], 0, 2, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(ANY_ATOMIC, EXACTLY_ONE) // QName or String
        )
    
        Fun("valid", classOf[XXFormsValid], 0, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(BOOLEAN, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        Fun("type", classOf[XXFormsType], 0, 0, QNAME, ALLOWS_ZERO_OR_MORE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        Fun("custom-mip", classOf[XXFormsCustomMIP], 0, 2, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("invalid-binds", classOf[XXFormsInvalidBinds], 0, 0, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        Fun("if", classOf[If], 0, 3, STRING, EXACTLY_ONE,
            Arg(BOOLEAN, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("binding", classOf[XXFormsBinding], 0, 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )

        Fun("binding-context", classOf[XXFormsBindingContext], 0, 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )

        Fun("value", classOf[XXFormsValue], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("mutable-document", classOf[XXFormsMutableDocument], 0, 1, Type.NODE_TYPE, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, EXACTLY_ONE)
        )
    
        Fun("component-context", classOf[XXFormsComponentContext], 0, 0, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
    
        Fun("instance", classOf[XXFormsInstance], 0, 1, Type.NODE_TYPE, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        Fun("index", classOf[XXFormsIndex], 0, 0, INTEGER, EXACTLY_ONE,
            Arg(STRING, ALLOWS_ZERO_OR_ONE)
        )
    
        Fun("list-models", classOf[XXFormsListModels], 0, 0, STRING, ALLOWS_ZERO_OR_MORE)
    
        Fun("list-instances", classOf[XXFormsListInstances], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("list-variables", classOf[XXFormsListVariables], 0, 1, STRING, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("get-variable", classOf[XXFormsGetVariable], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("itemset", classOf[XXFormsItemset], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        Fun("format-message", classOf[XXFormsFormatMessage], 0, 2, STRING, EXACTLY_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        Fun("lang", classOf[XXFormsLang], 0, 0, STRING, ALLOWS_ZERO_OR_ONE)

        Fun("r", classOf[XXFormsResource], 0, 1, 2, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("pending-uploads", classOf[XXFormsPendingUploads], 0, 0, INTEGER, EXACTLY_ONE)
    
        Fun("document-id", classOf[XXFormsDocumentId], 0, 0, STRING, EXACTLY_ONE)
    
        Fun("create-document", classOf[XXFormsCreateDocument], 0, 0, Type.NODE_TYPE, EXACTLY_ONE)
    
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

        Fun("visited", classOf[XXFormsVisited], 0, 1, BOOLEAN, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )

        Fun("absolute-id", classOf[XXFormsAbsoluteId], 0, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )

        Fun("client-id", classOf[XXFormsClientId], 0, 1, STRING, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("control-element", classOf[XXFormsControlElement], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
            Arg(STRING, EXACTLY_ONE)
        )
    
        Fun("extract-document", classOf[XXFormsExtractDocument], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
            Arg(Type.NODE_TYPE, EXACTLY_ONE),
            Arg(STRING, EXACTLY_ONE),
            Arg(BOOLEAN, EXACTLY_ONE)
        )
    
        // RFE: Support XSLT 2.0-features such as multiple sort keys
        Fun("sort", classOf[XXFormsSort], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(Type.ITEM_TYPE, EXACTLY_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE)
        )

        // NOTE: also from exforms
        Fun("relevant", classOf[EXFormsMIP], 0, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        // NOTE: also from exforms
        Fun("readonly", classOf[EXFormsMIP], 1, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        // NOTE: also from exforms
        Fun("required", classOf[EXFormsMIP], 2, 0, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )

        // Now available in XForms 2.0
        Fun("bind", classOf[Bind], 0, 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(STRING, EXACTLY_ONE)
        )
    }
}