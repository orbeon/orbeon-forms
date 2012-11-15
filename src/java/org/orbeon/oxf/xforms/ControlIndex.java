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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.*;

import java.util.*;

public class ControlIndex {

    private final boolean isNoscript;   // whether we are in noscript mode

    // Index of all controls in the tree by effective id
    // Order is desired so we iterate controls in the order they were added
    private Map<String, XFormsControl> effectiveIdsToControls = new LinkedHashMap<String, XFormsControl>();

    // Map<String type, LinkedHashMap<String effectiveId, XFormsControl control>>
    // No need for order here
    private Map<String, Map<String, XFormsControl>> controlTypes = new HashMap<String, Map<String, XFormsControl>>();

    ControlIndex(boolean noscript) {
        isNoscript = noscript;
    }

    /**
     * Index a single controls.
     *
     * @param control           control to index
     */
    public void indexControl(XFormsControl control) {

        // Remember by effective id
        effectiveIdsToControls.put(control.getEffectiveId(), control);

        // Also index children actions
        for (final XFormsActionControl actionControl : control.getChildrenActions())
            effectiveIdsToControls.put(actionControl.getEffectiveId(), actionControl);

        // Remember by control type (for certain controls we know we need)
        if (mustMapControl(control)) {
            Map<String, XFormsControl> controlsMap = controlTypes.get(control.localName());
            if (controlsMap == null) {
                controlsMap = new LinkedHashMap<String, XFormsControl>(); // need for order here!
                controlTypes.put(control.localName(), controlsMap);
            }

            controlsMap.put(control.getEffectiveId(), control);
        }
    }

    /**
     * Deindex a single control.
     *
     * @param control           control to deindex
     */
    public void deindexControl(XFormsControl control) {

        if (effectiveIdsToControls != null) {
            // Remove by effective id
            effectiveIdsToControls.remove(control.getEffectiveId());

            // Also remove children actions
            for (final XFormsActionControl actionControl : control.getChildrenActions())
                effectiveIdsToControls.remove(actionControl.getEffectiveId());
        }

        // Remove by control type (for certain controls we know we need)
        if (mustMapControl(control)) {
            if (controlTypes != null) {
                final Map controlsMap = controlTypes.get(control.localName());
                if (controlsMap != null) {
                    controlsMap.remove(control.getEffectiveId());
                }
            }
        }
    }

    private boolean mustMapControl(XFormsControl control) {

        // Remember:
        // xf:upload
        // xf:repeat
        // xxf:dialog
        // xf:select[@appearance = 'full'] in noscript mode
        return control instanceof XFormsUploadControl
                || control instanceof XFormsRepeatControl
                || control instanceof XXFormsDialogControl
                || (isNoscript && control instanceof XFormsSelectControl && ((XFormsSelectControl) control).isFullAppearance());
    }

    public XFormsControl getControl(String effectiveId) {
        return effectiveIdsToControls.get(effectiveId);
    }

    public Map<String, XFormsControl> getEffectiveIdsToControls() {
        return effectiveIdsToControls;
    }

    public Map<String, XFormsControl> getUploadControls() {
        return (controlTypes != null) ? controlTypes.get(XFormsConstants.UPLOAD_NAME) : null;
    }

    public Map<String, XFormsControl> getRepeatControls() {
        if (controlTypes == null)
            return Collections.emptyMap();
        else {
            final Map<String, XFormsControl> repeatMap = controlTypes.get(XFormsConstants.REPEAT_NAME);
            return repeatMap != null ? repeatMap : Collections.<String, XFormsControl>emptyMap();
        }
    }

    public Map<String, XFormsControl> getDialogControls() {
        return (controlTypes != null) ? controlTypes.get(XFormsConstants.XXFORMS_DIALOG_NAME) : null;
    }

    /**
     * Return the list of xf:select[@appearance = 'full'] in noscript mode.
     *
     * @return LinkedHashMap
     */
    public Map<String, XFormsControl> getSelectFullControls() {
        final Map<String, XFormsControl> result = (controlTypes != null) ? controlTypes.get("select") : null;
        return (result == null) ? Collections.<String, XFormsControl>emptyMap() : result;
    }

    public void clear() {
        effectiveIdsToControls = null;
        controlTypes = null;
    }
}
