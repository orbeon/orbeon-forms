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

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
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

    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindings.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        {
            final String levelAttribute;
            final QName levelQName;
            {
                final String tempLevelAttribute = actionElement.attributeValue("level");
                if (tempLevelAttribute == null) {
                    // "The default is "modal" if the attribute is not specified."
                    levelQName = XFormsConstants.XFORMS_MODAL_LEVEL_QNAME;
                    levelAttribute = levelQName.getName();
                } else {
                    levelAttribute = tempLevelAttribute;
                    levelQName = Dom4jUtils.extractAttributeValueQName(actionElement, "level");
                }
            }

            // Get message value
            // TODO: In the future, we should support HTML
            final String messageValue = XFormsUtils.getElementValue(actionInterpreter.getXBLContainer(),
                    actionInterpreter.getContextStack(), actionInterpreter.getSourceEffectiveId(actionElement), actionElement, false, null);

            if (LOG_APPEARANCES.get(levelQName) != null) {
                // Special log appearance

                final IndentedLogger indentedLogger = containingDocument.getIndentedLogger();
                if (XFormsConstants.XXFORMS_LOG_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logDebug("xforms:message", messageValue);
                } else if (XFormsConstants.XXFORMS_LOG_INFO_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logInfo("xforms:message", messageValue);
                } else if (XFormsConstants.XXFORMS_LOG_WARN_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logWarning("xforms:message", messageValue);
                } else if (XFormsConstants.XXFORMS_LOG_ERROR_DEBUG_LEVEL_QNAME.equals(levelQName)) {
                    indentedLogger.logError("xforms:message", messageValue);
                }

            } else if (SUPPORTED_APPEARANCES.get(levelQName) != null) {
                // Any other supported appearance are sent to the client

                final String level;
                if (levelQName.getNamespacePrefix().equals("")) {
                    level = levelAttribute;
                } else {
                    level = "{" + levelQName.getNamespaceURI() + "}" + levelQName.getName();
                }

                if (messageValue != null) {
                    // Store message for sending to client
                    containingDocument.addMessageToRun(messageValue, level);

                    // NOTE: In the future, we *may* want to save and resume the event state before and after
                    // displaying a message, in order to possibly provide a behavior which is more consistent with what
                    // users may expect regarding actions executed after xforms:message.
                }

            } else {
                // Unsupported appearance
                throw new OXFException("xforms:message element's 'level' attribute must have value: 'ephemeral'|'modeless'|'modal'|'xxforms:log-debug'|'xxforms:log-info'|'xxforms:log-warn'|'xxforms:log-error'.");
            }
        }
    }
}
