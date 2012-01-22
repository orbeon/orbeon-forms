/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions

import org.dom4j.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction, XFormsActionInterpreter}
import org.orbeon.oxf.xforms.event.events.XXFormsSetindexEvent
import org.orbeon.oxf.xforms.control.XFormsControl

/**
 * 9.3.7 The setindex Element
 */
object XFormsSetindexAction {
    
    def executeSetindexAction(interpreter: XFormsActionInterpreter, actionElement: Element, repeatStaticId: String, index: Int) = {
        val indentedLogger = interpreter.indentedLogger
        
        // "This XForms Action begins by invoking the deferred update behavior."
        interpreter.containingDocument.synchronizeAndRefresh()
        
        // Find repeat control
        interpreter.resolveEffectiveControl(actionElement, repeatStaticId) match {
            case control: XFormsControl ⇒
                
                val repeatControl = Some(control) collect { case repeat: XFormsRepeatControl ⇒ repeat }
                
                if (indentedLogger.isDebugEnabled)
                    indentedLogger.logDebug("xforms:setindex", "setting index upon xforms:setindex",
                        "old index", repeatControl map (_.getIndex.toString) orNull,
                        "new index", index.toString)
                
                // Dispatch to any control so that other custom controls can implement the notion of "setindex"
                interpreter.container().dispatchEvent(new XXFormsSetindexEvent(interpreter.containingDocument(), control, index))
                
                // However at this time return the index only for repeat controls as we don't have a generic way to figure this out yet
                repeatControl map (_.getIndex) getOrElse -1
                
            case _ ⇒
                // "If there is a null search result for the target object and the source object is an XForms action such as
                // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
                if (indentedLogger.isDebugEnabled)
                    indentedLogger.logDebug("xforms:setindex", "index does not refer to an existing xforms:repeat element, ignoring action", "repeat id", repeatStaticId)
                
                -1
        }
    }
}

class XFormsSetindexAction extends XFormsAction {
    
    override def execute(actionContext: DynamicActionContext) {
        
        val interpreter = actionContext.interpreter
        val element = actionContext.analysis.element
        
        // Get the repeat static id
        val repeatStaticId = {
            // Check presence of mandatory attribute
            val repeatAttribute = Option(element.attributeValue("repeat")) getOrElse
                (throw new OXFException("Missing mandatory 'repeat' attribute on xforms:setindex element."))

            // Resolve AVT but return if we can't evaluate it (if no XPath context)
            Option(interpreter.resolveAVTProvideValue(element, repeatAttribute)) getOrElse
                (return)
        }
        
        // Determine the index
        val index = {

            val indexXPath = element.attributeValue("index")
            val contextStack = interpreter.actionXPathContext
            val indexString = interpreter.evaluateStringExpression(element, contextStack.getCurrentNodeset, contextStack.getCurrentPosition, "number(" + indexXPath + ")")

            try indexString.toInt
            catch { case _ ⇒ return } // "If the index evaluates to NaN the action has no effect."
        }
        
        // Execute
        XFormsSetindexAction.executeSetindexAction(interpreter, element, repeatStaticId, index)
    }
}