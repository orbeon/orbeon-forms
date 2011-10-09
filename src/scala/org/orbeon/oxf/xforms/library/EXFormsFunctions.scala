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

import org.orbeon.oxf.xforms.function.exforms.{EXFormsSort, EXFormsRequired, EXFormsReadonly, EXFormsRelevant}
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.`type`.Type
import org.orbeon.oxf.xml.OrbeonFunctionLibrary

/**
 * eXforms functions.
 */
trait EXFormsFunctions extends OrbeonFunctionLibrary {

    Namespace(XFormsConstants.EXFORMS_NAMESPACE_URI) {
        
        // exf:relevant()
        Fun("relevant", classOf[EXFormsRelevant], 0, 0, 1, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        // exf:readonly()
        Fun("readonly", classOf[EXFormsReadonly], 0, 0, 1, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        // exf:required()
        Fun("required", classOf[EXFormsRequired], 0, 0, 1, BOOLEAN, EXACTLY_ONE,
            Arg(Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE)
        )
    
        // exf:sort()
        Fun("sort", classOf[EXFormsSort], 0, 2, 5, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
            Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
            Arg(STRING, EXACTLY_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE),
            Arg(STRING, ALLOWS_ZERO_OR_ONE)
        )
    }
}