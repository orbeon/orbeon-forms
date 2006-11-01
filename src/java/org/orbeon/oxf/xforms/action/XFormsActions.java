/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.action.actions.*;
import org.orbeon.oxf.xml.XMLUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * XForms actions definitions.
 */
public class XFormsActions {
    // Standard XForms actions
    public static final String XFORMS_ACTION_ACTION = "action";
    public static final String XFORMS_DISPATCH_ACTION = "dispatch";
    public static final String XFORMS_REBUILD_ACTION = "rebuild";
    public static final String XFORMS_RECALCULATE_ACTION = "recalculate";
    public static final String XFORMS_REVALIDATE_ACTION = "revalidate";
    public static final String XFORMS_REFRESH_ACTION = "refresh";
    public static final String XFORMS_SETFOCUS_ACTION = "setfocus";
    public static final String XFORMS_LOAD_ACTION = "load";
    public static final String XFORMS_SETVALUE_ACTION = "setvalue";
    public static final String XFORMS_SEND_ACTION = "send";
    public static final String XFORMS_RESET_ACTION = "reset";
    public static final String XFORMS_MESSAGE_ACTION = "message";
    public static final String XFORMS_TOGGLE_ACTION = "toggle";
    public static final String XFORMS_INSERT_ACTION = "insert";
    public static final String XFORMS_DELETE_ACTION = "delete";
    public static final String XFORMS_SETINDEX_ACTION = "setindex";

    // Extension actions
    public static final String XXFORMS_SCRIPT_ACTION = "script";
    public static final String XXFORMS_SHOW_ACTION = "show";
    public static final String XXFORMS_HIDE_ACTION = "hide";

    private static final Map actions = new HashMap();

    static {
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_ACTION_ACTION), new XFormsActionAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_DISPATCH_ACTION), new XFormsDispatchAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_REBUILD_ACTION), new XFormsRebuildAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_RECALCULATE_ACTION), new XFormsRecalculateAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_REVALIDATE_ACTION), new XFormsRevalidateAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_REFRESH_ACTION), new XFormsRefreshAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SETFOCUS_ACTION), new XFormsSetfocusAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_LOAD_ACTION), new XFormsLoadAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SETVALUE_ACTION), new XFormsSetvalueAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SEND_ACTION), new XFormsSendAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_RESET_ACTION), new XFormsResetAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_MESSAGE_ACTION), new XFormsMessageAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_TOGGLE_ACTION), new XFormsToggleAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_INSERT_ACTION), new XFormsInsertAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_DELETE_ACTION), new XFormsDeleteAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SETINDEX_ACTION), new XFormsSetindexAction());

        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_SCRIPT_ACTION), new XXFormsScriptAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_SHOW_ACTION), new XXFormsShowAction());
        actions.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_HIDE_ACTION), new XXFormsHideAction());
    }

    /**
     * Return the action with the given namespace URI and namespace local name, null if there is no such action.
     *
     * @param uri           action namespace URI
     * @param localname     action local name
     * @return              XFormsAction or null
     */
    public static XFormsAction getAction(String uri, String localname) {
        return (XFormsAction) actions.get(XMLUtils.buildExplodedQName(uri, localname));
    }

    /**
     * Return true if and only if the given action exists
     *
     * @param uri           action namespace URI
     * @param localname     action local name
     * @return              true if the action exists
     */
    public static boolean isActionName(String uri, String localname) {
        return getAction(uri, localname) != null;
    }

    private XFormsActions() {}
}
