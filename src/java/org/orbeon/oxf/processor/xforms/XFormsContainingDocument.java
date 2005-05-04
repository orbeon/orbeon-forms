package org.orbeon.oxf.processor.xforms;

import org.dom4j.Document;
import org.orbeon.oxf.processor.xforms.event.XFormsEvent;
import org.orbeon.oxf.pipeline.api.PipelineContext;

import java.util.*;

/**
 * Represents an XForms containing document.
 *
 * Includes:
 *
 * o models
 * o instances
 * o controls / handlers hierarchy
 */
public class XFormsContainingDocument {

    private List models = new ArrayList();
    private Map modelsMap = new HashMap();
    private Document controlsDocument;

    public XFormsContainingDocument(Document controlsDocument) {
        this.controlsDocument = controlsDocument;
    }

    public void addModel(XFormsModel model) {
        models.add(model);
        modelsMap.put(model.getId(), model);
    }

    public XFormsModel getModel(String id) {
        return (XFormsModel) modelsMap.get(id);
    }

    public List getModels() {
        return models;
    }

    /**
     * Initialize the XForms engine.
     */
    public void initialize(PipelineContext pipelineContext) {
        // 4.2 Initialization Events

        // 1. Dispatch xforms-model-construct to all models
        // 2. Dispatch xforms-model-construct-done to all models
        // 3. Dispatch xforms-ready to all models

        final String[] eventsToDispatch = { XFormsEvent.XFORMS_MODEL_CONSTRUCT, XFormsEvent.XFORMS_MODEL_DONE, XFormsEvent.XFORMS_READY };
        for (int i = 0; i < eventsToDispatch.length; i++) {
            for (Iterator j = getModels().iterator(); j.hasNext();) {
                XFormsModel model = (XFormsModel) j.next();
                model.dispatchEvent(pipelineContext, eventsToDispatch[i]);
            }
        }
    }
}
