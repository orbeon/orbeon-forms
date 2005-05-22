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
package org.orbeon.oxf.xforms;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.event.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents an XForms containing document.
 *
 * The containing document includes:
 *
 * o XForms models (including multiple instances)
 * o XForms controls
 * o Event handlers hierarchy
 */
public class XFormsContainingDocument implements org.orbeon.oxf.xforms.event.EventTarget {

    private List models;
    private Map modelsMap = new HashMap();
    private XFormsControls xFormsControls;

    public XFormsContainingDocument(List models, Document controlsDocument) {
        this.models = models;
        this.xFormsControls = new XFormsControls(this, controlsDocument);

        for (Iterator i = models.iterator(); i.hasNext();) {
            XFormsModel model = (XFormsModel) i.next();
            if (model.getModelId() != null)
                modelsMap.put(model.getModelId(), model);
            model.setControls(xFormsControls);
        }
    }

    /**
     * Return model with the specified id, null if not found. If the id is the empty string, return
     * the default model, i.e. the first model.
     */
    public XFormsModel getModel(String modelId) {
        return (XFormsModel) ("".equals(modelId) ? models.get(0) : modelsMap.get(modelId));
    }

    /**
     * Get a list of all the models in this document.
     */
    public List getModels() {
        return models;
    }

    /**
     * Return the XForms controls.
     */
    public XFormsControls getXFormsControls() {
        return xFormsControls;
    }

    /**
     * Initialize the XForms engine.
     */
    public void initialize(PipelineContext pipelineContext) {
        // NOP for now
    }

    /**
     * Get object with the id specified.
     */
    public Object geObjectById(PipelineContext pipelineContext, String id) {

        // Search in models
        for (Iterator i = models.iterator(); i.hasNext();) {
            XFormsModel model = (XFormsModel) i.next();
            final Object resultObject = model.getObjectByid(pipelineContext, id);
            if (resultObject != null)
                return resultObject;
        }

        // Search in controls
        return xFormsControls.getElementById(pipelineContext, id);
    }

    /**
     * Execute an external event on control with id controlId and event eventName.
     */
    public void executeExternalEvent(PipelineContext pipelineContext, String controlId, String eventName, String eventValue) {

        // Get control element and event handler element
        Element controlElement = xFormsControls.getElementById(pipelineContext, controlId);

        // Interpret event
        XFormsGenericEvent xformsEvent = new XFormsGenericEvent(controlElement, eventValue);
        interpretEvent(pipelineContext, xformsEvent, eventName);
    }

    private void interpretEvent(final PipelineContext pipelineContext, XFormsGenericEvent xformsEvent, String eventName) {

        if (XFormsEvents.XFORMS_DOM_ACTIVATE.equals(eventName)) {
            // 4.4.1 The DOMActivate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            xFormsControls.dispatchEvent(pipelineContext, xformsEvent, eventName);

        } else if (XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE.equals(eventName)) {
            // 4.6.7 Sequence: Value Change with Focus Change

            // 1. xforms-recalculate
            // 2. xforms-revalidate
            // 3. [n] xforms-valid/xforms-invalid; xforms-enabled/xforms-disabled; xforms-optional/xforms-required; xforms-readonly/xforms-readwrite
            // 4. xforms-value-changed
            // 5. DOMFocusOut
            // 6. DOMFocusIn
            // 7. xforms-refresh
            // Reevaluation of binding expressions must occur before step 3 above.

            // Set current context to control
            xFormsControls.setBinding(pipelineContext, xformsEvent.getControlElement());

            // Set value into the instance
            XFormsInstance.setValueForNode(xFormsControls.getCurrentSingleNode(), xformsEvent.getValue());

            // Dispatch events
            XFormsModel model = xFormsControls.getCurrentModel();
            model.dispatchEvent(pipelineContext, xformsEvent, XFormsEvents.XFORMS_RECALCULATE);
            model.dispatchEvent(pipelineContext, xformsEvent, XFormsEvents.XFORMS_REVALIDATE);

            xFormsControls.dispatchEvent(pipelineContext, xformsEvent, XFormsEvents.XFORMS_DOM_FOCUS_OUT);
            xFormsControls.dispatchEvent(pipelineContext, xformsEvent, XFormsEvents.XFORMS_VALUE_CHANGED);

            // TODO (missing new control information!)
            //xFormsControls.dispatchEvent(pipelineContext, XFormsEvents.XFORMS_DOM_FOCUS_IN, newControlElement);

            model.dispatchEvent(pipelineContext, new XFormsRefreshEvent());

        } else {
            throw new OXFException("Invalid event requested: " + eventName);
        }
    }

    public void dispatchEvent(final PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        dispatchEvent(pipelineContext, xformsEvent, xformsEvent.getEventName());
    }

    public void dispatchEvent(PipelineContext pipelineContext, XFormsGenericEvent XFormsEvent, String eventName) {
        if (XFormsEvents.XXFORMS_INITIALIZE.equals(eventName)) {
            // 4.2 Initialization Events

            // 1. Dispatch xforms-model-construct to all models
            // 2. Dispatch xforms-model-construct-done to all models
            // 3. Dispatch xforms-ready to all models

            final String[] eventsToDispatch = { XFormsEvents.XFORMS_MODEL_CONSTRUCT, XFormsEvents.XFORMS_MODEL_DONE, XFormsEvents.XFORMS_READY };
            for (int i = 0; i < eventsToDispatch.length; i++) {
                if (XFormsEvents.XFORMS_MODEL_DONE.equals(eventsToDispatch[i])) {
                    dispatchEvent(pipelineContext, XFormsEvent, XFormsEvents.XXFORMS_INITIALIZE_CONTROLS);
                }
                for (Iterator j = getModels().iterator(); j.hasNext();) {
                    XFormsModel model = (XFormsModel) j.next();
                    model.dispatchEvent(pipelineContext, XFormsEvent, eventsToDispatch[i]);
                }
            }
        } else if (XFormsEvents.XXFORMS_INITIALIZE_STATE.equals(eventName)) {
            // Restore models state
            for (Iterator j = getModels().iterator(); j.hasNext();) {
                XFormsModel model = (XFormsModel) j.next();
                model.dispatchEvent(pipelineContext, XFormsEvent, XFormsEvents.XXFORMS_INITIALIZE_STATE);
            }
            dispatchEvent(pipelineContext, XFormsEvent, XFormsEvents.XXFORMS_INITIALIZE_CONTROLS);
        } else if (XFormsEvents.XXFORMS_INITIALIZE_CONTROLS.equals(eventName)) {
            // Make sure controls are initialized
            xFormsControls.initialize(pipelineContext);
        } else {
            throw new OXFException("Invalid event dispatched: " + eventName);
        }
    }

}
