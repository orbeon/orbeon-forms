package org.orbeon.oxf.xforms;

/**
 * XForms events definitions.
 */
public class XFormsEvents {
    // Custom initialization events
    public static final String XXFORMS_INITIALIZE = "xxforms-initialize";
    public static final String XXFORMS_INITIALIZE_CONTROLS = "xxforms-initialize-controls";

    // Standard sequences
    public static final String XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE = "xxforms-value-change-with-focus-change";

    // Standard XForms events and actions
    public static final String XFORMS_SETVALUE_ACTION = "setvalue";
    public static final String XFORMS_TOGGLE_ACTION = "toggle";
    public static final String XFORMS_ACTION_ACTION = "action";
    public static final String XFORMS_MODEL_CONSTRUCT = "xforms-model-construct";
    public static final String XFORMS_MODEL_DONE = "xforms-model-construct-done";
    public static final String XFORMS_READY = "xforms-ready";
    public static final String XFORMS_MODEL_DESTRUCT = "xforms-model-destruct";
    public static final String XFORMS_REBUILD = "xforms-rebuild";
    public static final String XFORMS_RECALCULATE = "xforms-recalculate";
    public static final String XFORMS_REVALIDATE = "xforms-revalidate";
    public static final String XFORMS_REFRESH = "xforms-refresh";
    public static final String XFORMS_RESET = "xforms-reset";

    public static final String XFORMS_VALUE_CHANGED = "xforms-value-changed";

    public static final String XFORMS_DOM_FOCUS_OUT = "DOMFocusOut";
    public static final String XFORMS_DOM_FOCUS_IN = "DOMFocusIn";
}
