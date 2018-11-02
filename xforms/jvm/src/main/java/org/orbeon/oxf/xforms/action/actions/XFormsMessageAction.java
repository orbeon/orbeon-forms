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
package org.orbeon.oxf.xforms.action.actions;

import org.orbeon.dom.Element;
import org.orbeon.dom.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * 10.12 The message Element
 */
public class XFormsMessageAction extends XFormsAction {

    private static final Map<QName, String> SUPPORTED_APPEARANCES = new HashMap<QName, String>();
    private static final Map<QName, String> LOG_APPEARANCES = new HashMap<QName, String>();

    static {
        // Standard levels
        SUPPORTED_APPEARANCES.put(XFormsConstants.XFORMS_MODAL_LEVEL_QNAME, "");
        SUPPORTED_APPEARANCES.put(XFormsConstants.XFORMS_MODELESS_LEVEL_QNAME, "");
        SUPPORTED_APPEARANCES.put(XFormsConstants.XFORMS_EPHEMERAL_LEVEL_QNAME, "");

        // Extension levels
        LOG_APPEARANCES.put(XFormsConstants.XXFORMS_LOG_DEBUG_LEVEL_QNAME, "");
        LOG_APPEARANCES.put(XFormsConstants.XXFORMS_LOG_INFO_DEBUG_LEVEL_QNAME, "");
        LOG_APPEARANCES.put(XFormsConstants.XXFORMS_LOG_WARN_DEBUG_LEVEL_QNAME, "");
        LOG_APPEARANCES.put(XFormsConstants.XXFORMS_LOG_ERROR_DEBUG_LEVEL_QNAME, "");

        SUPPORTED_APPEARANCES.putAll(LOG_APPEARANCES);
    }

    public void execute(XFormsActionInterpreter actionInterpreter, Element actionElement,
                        Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.containingDocument();

        {
            final String levelAttribute;
            final QName levelQName;
            {
                final String tempLevelAttribute = actionElement.attributeValue(XFormsConstants.LEVEL_QNAME);
                if (tempLevelAttribute == null) {
                    // "The default is "modal" if the attribute is not specified."
                    levelQName = XFormsConstants.XFORMS_MODAL_LEVEL_QNAME;
                    levelAttribute = levelQName.localName();
                } else {
                    levelAttribute = tempLevelAttribute;
                    levelQName = Dom4jUtils.extractAttributeValueQName(actionElement, XFormsConstants.LEVEL_QNAME);
                }
            }

            // Get message value
            final String messageValue; {
                final String elementValue =
                    XFormsUtils.getElementValue(
                    actionInterpreter.container(),
                    actionInterpreter.actionXPathContext(),
                    actionInterpreter.getSourceEffectiveId(actionElement),
                    actionElement,
                    false,
                    false,
                    null
                );

                // If we got a null consider the message to be an empty string
                messageValue = elementValue != null ? elementValue : "";
            }

            if (LOG_APPEARANCES.get(levelQName) != null) {
                // Special log appearance

                final IndentedLogger indentedLogger = containingDocument.indentedLogger();
                if (XFormsConstants.XXFORMS_LOG_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logDebug("xf:message", messageValue);
                } else if (XFormsConstants.XXFORMS_LOG_INFO_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logInfo("xf:message", messageValue);
                } else if (XFormsConstants.XXFORMS_LOG_WARN_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logWarning("xf:message", messageValue);
                } else if (XFormsConstants.XXFORMS_LOG_ERROR_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logError("xf:message", messageValue);
                }

            } else if (SUPPORTED_APPEARANCES.get(levelQName) != null) {
                // Any other supported appearance are sent to the client

                final String level;
                if (levelQName.namespace().prefix().equals("")) {
                    level = levelAttribute;
                } else {
                    level = "{" + levelQName.namespace().uri() + "}" + levelQName.localName();
                }

                // Store message for sending to client
                containingDocument.addMessageToRun(messageValue, level);

                // NOTE: In the future, we *may* want to save and resume the event state before and after
                // displaying a message, in order to possibly provide a behavior which is more consistent with what
                // users may expect regarding actions executed after xf:message.

            } else {
                // Unsupported appearance
                throw new OXFException("xf:message element's 'level' attribute must have value: 'ephemeral'|'modeless'|'modal'|'xxf:log-debug'|'xxf:log-info'|'xxf:log-warn'|'xxf:log-error'.");
            }
        }
    }
}
