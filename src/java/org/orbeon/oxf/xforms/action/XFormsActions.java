package org.orbeon.oxf.xforms.action;

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

    private static final Map actions = new HashMap();

    static {
        actions.put(XFORMS_ACTION_ACTION, "");
        actions.put(XFORMS_DISPATCH_ACTION, "");
        actions.put(XFORMS_REBUILD_ACTION, "");
        actions.put(XFORMS_RECALCULATE_ACTION, "");
        actions.put(XFORMS_REVALIDATE_ACTION, "");
        actions.put(XFORMS_REFRESH_ACTION, "");
        actions.put(XFORMS_SETFOCUS_ACTION, "");
        actions.put(XFORMS_LOAD_ACTION, "");
        actions.put(XFORMS_SETVALUE_ACTION, "");
        actions.put(XFORMS_SEND_ACTION, "");
        actions.put(XFORMS_RESET_ACTION, "");
        actions.put(XFORMS_MESSAGE_ACTION, "");
        actions.put(XFORMS_TOGGLE_ACTION, "");
        actions.put(XFORMS_INSERT_ACTION, "");
        actions.put(XFORMS_DELETE_ACTION, "");
        actions.put(XFORMS_SETINDEX_ACTION, "");
    }

    public static boolean isActionName(String name) {
        return actions.get(name) != null;
    }

    private XFormsActions() {}
}
