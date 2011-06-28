/**
 *  Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.script

import org.mozilla.javascript.Context
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl

class ScriptInterpreter(containingDocument: XFormsContainingDocument) {

    // VERY experimental implementation of a Java API exposed to JavaScript
    class ORBEON {
        class Controls {

            def setValue(controlId: String, value: String): Unit =
                getValueControl(controlId).storeExternalValue(value, null)

            def getValue(controlId: String) = getValueControl(controlId).getValue

            private def getValueControl(controlId: String) = containingDocument.getObjectByEffectiveId(controlId).asInstanceOf[XFormsValueControl]
        }

        private lazy val controls = new Controls
        def getControls = controls
    }

    private lazy val topLevelScope = {
        val cx = Context.enter()
        try {
            // "A scope is a set of JavaScript object" http://goo.gl/H8g5f
            // "initStandardObjects is an expensive method to call and it allocates a fair amount of memory"
            // TODO: Can this be shared among threads?
            cx.initStandardObjects()
        } finally {
            Context.exit()
        }
    }

    def runScript(scriptId: String): Unit = {
        // 1. Get compiled script
        val compiledScript = containingDocument.getStaticOps.getScripts.get(scriptId).asInstanceOf[ServerScript].compiledScript

        // 2. Execute script
        val result = {
            val cx = Context.enter()
            try {
                topLevelScope.put("ORBEON", topLevelScope, Context.javaToJS(new ORBEON, topLevelScope))
                try {
                    compiledScript.exec(cx, topLevelScope)
                } finally {
                    topLevelScope.delete("ORBEON")
                }
            } finally {
                Context.exit()
            }
        }

        //System.out.println("Script result: " + Context.toString(result))
    }
}