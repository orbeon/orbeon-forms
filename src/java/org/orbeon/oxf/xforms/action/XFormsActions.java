/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action;

import org.apache.log4j.Logger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.action.actions.*;
import org.orbeon.oxf.xml.XMLUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * XForms actions definitions.
 */
public class XFormsActions {

    public static final String LOGGING_CATEGORY = "action";
    public static final Logger logger = LoggerFactory.createLogger(XFormsActions.class);

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
    public static final String XXFORMS_INVALIDATE_INSTANCE_ACTION = "invalidate-instance";
    public static final String XXFORMS_INVALIDATE_INSTANCES_ACTION = "invalidate-instances";
    public static final String XXFORMS_ONLINE_ACTION = "online";
    public static final String XXFORMS_OFFLINE_ACTION = "offline";
    public static final String XXFORMS_OFFLINE_SAVE_ACTION = "offline-save";
    public static final String XXFORMS_JOIN_SUBMISSIONS_ACTION = "join-submissions";

    private static final Map<String, XFormsAction> ACTIONS = new HashMap<String, XFormsAction>();

    static {
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_ACTION_ACTION), new XFormsActionAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_DISPATCH_ACTION), new XFormsDispatchAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_REBUILD_ACTION), new XFormsRebuildAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_RECALCULATE_ACTION), new XFormsRecalculateAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_REVALIDATE_ACTION), new XFormsRevalidateAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_REFRESH_ACTION), new XFormsRefreshAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SETFOCUS_ACTION), new XFormsSetfocusAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_LOAD_ACTION), new XFormsLoadAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SETVALUE_ACTION), new XFormsSetvalueAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SEND_ACTION), new XFormsSendAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_RESET_ACTION), new XFormsResetAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_MESSAGE_ACTION), new XFormsMessageAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_TOGGLE_ACTION), new XFormsToggleAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_INSERT_ACTION), new XFormsInsertAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_DELETE_ACTION), new XFormsDeleteAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, XFORMS_SETINDEX_ACTION), new XFormsSetindexAction());

        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_SCRIPT_ACTION), new XXFormsScriptAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_SHOW_ACTION), new XXFormsShowAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_HIDE_ACTION), new XXFormsHideAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_INVALIDATE_INSTANCE_ACTION), new XXFormsInvalidateInstanceAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_INVALIDATE_INSTANCES_ACTION), new XXFormsInvalidateInstancesAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_ONLINE_ACTION), new XXFormsOnlineAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_OFFLINE_ACTION), new XXFormsOfflineAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_OFFLINE_SAVE_ACTION), new XXFormsOfflineSaveAction());
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, XXFORMS_JOIN_SUBMISSIONS_ACTION), new XXFormsJoinSubmissions());

        // Also support xbl:handler
        ACTIONS.put(XMLUtils.buildExplodedQName(XFormsConstants.XBL_NAMESPACE_URI, XFormsConstants.XBL_HANDLER_QNAME.getName()), new XFormsActionAction());
    }

    /**
     * Return the action with the given namespace URI and namespace local name, null if there is no such action.
     *
     * @param uri           action namespace URI
     * @param localname     action local name
     * @return              XFormsAction or null
     */
    public static XFormsAction getAction(String uri, String localname) {
        return ACTIONS.get(XMLUtils.buildExplodedQName(uri, localname));
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
