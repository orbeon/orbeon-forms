package org.orbeon.oxf.processor.xforms;

import org.dom4j.Document;
import org.orbeon.oxf.processor.xforms.event.XFormsServer;
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

    /**
     * Return model with the specified id, null if not found. If the id is the empty string, return
     * the default model, i.e. the first model.
     */
    public XFormsModel getModel(String id) {
        return (XFormsModel) ("".equals(id) ? models.get(0) : modelsMap.get(id));
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

        final String[] eventsToDispatch = { XFormsServer.XFORMS_MODEL_CONSTRUCT, XFormsServer.XFORMS_MODEL_DONE, XFormsServer.XFORMS_READY };
        for (int i = 0; i < eventsToDispatch.length; i++) {
            for (Iterator j = getModels().iterator(); j.hasNext();) {
                XFormsModel model = (XFormsModel) j.next();
                model.dispatchEvent(pipelineContext, eventsToDispatch[i]);
            }
        }
    }
}
