/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event;

/**
 * XForms events definitions.
 */
public class XFormsEvents {

    // Custom initialization events
    public static final String XXFORMS_INITIALIZE = "xxforms-initialize";
    public static final String XXFORMS_INITIALIZE_CONTROLS = "xxforms-initialize-controls";
    public static final String XXFORMS_INITIALIZE_STATE = "xxforms-initialize-state";

    // Custom submit event
    public static final String XXFORMS_SUBMIT = "xxforms-submit";

    // Standard sequences
    public static final String XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE = "xxforms-value-change-with-focus-change";

    // Standard XForms events
    public static final String XFORMS_MODEL_CONSTRUCT = "xforms-model-construct";
    public static final String XFORMS_MODEL_CONSTRUCT_DONE = "xforms-model-construct-done";
    public static final String XFORMS_READY = "xforms-ready";
    public static final String XFORMS_MODEL_DESTRUCT = "xforms-model-destruct";
    public static final String XFORMS_REBUILD = "xforms-rebuild";
    public static final String XFORMS_RECALCULATE = "xforms-recalculate";
    public static final String XFORMS_REVALIDATE = "xforms-revalidate";
    public static final String XFORMS_REFRESH = "xforms-refresh";
    public static final String XFORMS_RESET = "xforms-reset";
    public static final String XFORMS_SUBMIT = "xforms-submit";
    public static final String XFORMS_SUBMIT_DONE = "xforms-submit-done";

    public static final String XFORMS_VALUE_CHANGED = "xforms-value-changed";
    public static final String XFORMS_VALID = "xforms-valid";
    public static final String XFORMS_INVALID = "xforms-invalid";
    public static final String XFORMS_REQUIRED = "xforms-required";
    public static final String XFORMS_OPTIONAL = "xforms-optional";
    public static final String XFORMS_READWRITE = "xforms-readwrite";
    public static final String XFORMS_READONLY = "xforms-readonly";
    public static final String XFORMS_ENABLED = "xforms-enabled";
    public static final String XFORMS_DISABLED = "xforms-disabled";

    public static final String XFORMS_DESELECT = "xforms-deselect";
    public static final String XFORMS_SELECT = "xforms-select";

    public static final String XFORMS_INSERT = "xforms-insert";
    public static final String XFORMS_DELETE = "xforms-delete";

    public static final String XFORMS_SCROLL_FIRST = "xforms-scroll-first";
    public static final String XFORMS_SCROLL_LAST = "xforms-scroll-last";

    // DOM events
    public static final String XFORMS_DOM_ACTIVATE = "DOMActivate";
    public static final String XFORMS_DOM_FOCUS_OUT = "DOMFocusOut";
    public static final String XFORMS_DOM_FOCUS_IN = "DOMFocusIn";

    // Exceptions and errors
    public static final String XFORMS_LINK_EXCEPTION = "xforms-link-exception";
    public static final String XFORMS_LINK_ERROR = "xforms-link-error";
    public static final String XFORMS_COMPUTE_EXCEPTION = "xforms-compute-exception";
    public static final String XFORMS_SUBMIT_ERROR = "xforms-submit-error";

    private XFormsEvents() {}
}
