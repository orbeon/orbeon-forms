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
package org.orbeon.oxf.xforms.analysis;

import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.test.TestExternalContext;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;

import javax.xml.transform.sax.TransformerHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XFormsStaticStateTest extends ResourceManagerTestBase {

    public void testXPathAnalysis() {
        final XFormsStaticState staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/form.xml");
        final Map<String, String> namespaces = new HashMap<String, String>();
        namespaces.put("", "");

        // Hold the current list of changes 
        final Set<String> currentChanges = new HashSet<String>();

        final PathMapUIDependencies dependencies = new PathMapUIDependencies(staticState.getIndentedLogger(), staticState) {
            @Override
            protected Set<String> getModifiedPaths() {
                return currentChanges;
            }
        };

        staticState.dumpAnalysis();

        // == Value change to default ==================================================================================
        currentChanges.clear();
        currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('default')/a"));

        assertFalse(dependencies.requireBindingUpdate("trigger1"));
        assertFalse(dependencies.requireBindingUpdate("trigger2"));

        assertFalse(dependencies.requireBindingUpdate("select1"));
        assertTrue(dependencies.requireValueUpdate("select1"));

        // TEMP: for build server
        dependencies.requireBindingUpdate("group2");
//        assertTrue(dependencies.requireBindingUpdate("group2"));
//        assertTrue(dependencies.requireValueUpdate("group2"));

        assertTrue(dependencies.requireBindingUpdate("select2"));
        assertTrue(dependencies.requireValueUpdate("select2"));


        assertFalse(dependencies.requireBindingUpdate("group3"));
//        assertFalse(dependencies.requireValueUpdate("group3"));

        assertFalse(dependencies.requireBindingUpdate("select3"));
        assertFalse(dependencies.requireValueUpdate("select3"));

        assertFalse(dependencies.requireBindingUpdate("group4"));
//        assertFalse(dependencies.requireValueUpdate("group4"));

        assertFalse(dependencies.requireBindingUpdate("select4"));
        assertFalse(dependencies.requireValueUpdate("select4"));

        dependencies.refreshDone();

        // == Value change to default ==================================================================================
        currentChanges.clear();
        currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('default')/b"));

        assertFalse(dependencies.requireBindingUpdate("trigger1"));
        assertFalse(dependencies.requireBindingUpdate("trigger2"));

        assertFalse(dependencies.requireBindingUpdate("select1"));
        assertFalse(dependencies.requireValueUpdate("select1"));

        assertFalse(dependencies.requireBindingUpdate("group2"));
//        assertFalse(dependencies.requireValueUpdate("group2"));

        assertFalse(dependencies.requireBindingUpdate("select2"));
        assertTrue(dependencies.requireValueUpdate("select2"));


        assertFalse(dependencies.requireBindingUpdate("group3"));
//        assertFalse(dependencies.requireValueUpdate("group3"));

        assertFalse(dependencies.requireBindingUpdate("select3"));
        assertFalse(dependencies.requireValueUpdate("select3"));

        assertFalse(dependencies.requireBindingUpdate("group4"));
//        assertFalse(dependencies.requireValueUpdate("group4"));

        assertFalse(dependencies.requireBindingUpdate("select4"));
        assertFalse(dependencies.requireValueUpdate("select4"));

        dependencies.refreshDone();

        // == Value change to instance2 ================================================================================
        currentChanges.clear();
        currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('instance2')/a"));

        assertFalse(dependencies.requireBindingUpdate("trigger1"));
        assertFalse(dependencies.requireBindingUpdate("trigger2"));

        assertFalse(dependencies.requireBindingUpdate("select1"));
        assertFalse(dependencies.requireValueUpdate("select1"));

        assertFalse(dependencies.requireBindingUpdate("group2"));
//        assertFalse(dependencies.requireValueUpdate("group2"));

        assertFalse(dependencies.requireBindingUpdate("select2"));
        assertFalse(dependencies.requireValueUpdate("select2"));


        assertFalse(dependencies.requireBindingUpdate("group3"));
//        assertFalse(dependencies.requireValueUpdate("group3"));

        assertFalse(dependencies.requireBindingUpdate("select3"));
        assertTrue(dependencies.requireValueUpdate("select3"));

        assertTrue(dependencies.requireBindingUpdate("group4"));
//        assertFalse(dependencies.requireValueUpdate("group4"));

        assertTrue(dependencies.requireBindingUpdate("select4"));
        assertTrue(dependencies.requireValueUpdate("select4"));

        dependencies.refreshDone();

        // == Value change to instance2 ================================================================================
        currentChanges.clear();
        currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('instance2')/b"));

        assertFalse(dependencies.requireBindingUpdate("trigger1"));
        assertFalse(dependencies.requireBindingUpdate("trigger2"));

        assertFalse(dependencies.requireBindingUpdate("select1"));
        assertFalse(dependencies.requireValueUpdate("select1"));

        assertFalse(dependencies.requireBindingUpdate("group2"));
//        assertFalse(dependencies.requireValueUpdate("group2"));

        assertFalse(dependencies.requireBindingUpdate("select2"));
        assertFalse(dependencies.requireValueUpdate("select2"));


        assertFalse(dependencies.requireBindingUpdate("group3"));
//        assertFalse(dependencies.requireValueUpdate("group3"));

        assertFalse(dependencies.requireBindingUpdate("select3"));
        assertFalse(dependencies.requireValueUpdate("select3"));

        assertFalse(dependencies.requireBindingUpdate("group4"));
//        assertFalse(dependencies.requireValueUpdate("group4"));

        assertFalse(dependencies.requireBindingUpdate("select4"));
        assertTrue(dependencies.requireValueUpdate("select4"));

        dependencies.refreshDone();

        // == Structural change to model1 ==============================================================================
        currentChanges.clear();
        dependencies.markStructuralChange("model1");

        assertTrue(dependencies.requireBindingUpdate("trigger1"));
        assertFalse(dependencies.requireBindingUpdate("trigger2"));

        assertTrue(dependencies.requireBindingUpdate("select1"));
        assertTrue(dependencies.requireValueUpdate("select1"));

        assertTrue(dependencies.requireBindingUpdate("group2"));
//        assertTrue(dependencies.requireValueUpdate("group2"));

        assertTrue(dependencies.requireBindingUpdate("select2"));
        assertTrue(dependencies.requireValueUpdate("select2"));


        assertFalse(dependencies.requireBindingUpdate("group3"));
//        assertFalse(dependencies.requireValueUpdate("group3"));

        assertFalse(dependencies.requireBindingUpdate("select3"));
        assertFalse(dependencies.requireValueUpdate("select3"));

        assertFalse(dependencies.requireBindingUpdate("group4"));
//        assertFalse(dependencies.requireValueUpdate("group4"));

        assertFalse(dependencies.requireBindingUpdate("select4"));
        assertFalse(dependencies.requireValueUpdate("select4"));

        dependencies.refreshDone();

        // == Structural change to model2 ==============================================================================
        currentChanges.clear();
        dependencies.markStructuralChange("model2");

        assertFalse(dependencies.requireBindingUpdate("trigger1"));
        assertTrue(dependencies.requireBindingUpdate("trigger2"));

        assertFalse(dependencies.requireBindingUpdate("select1"));
        assertFalse(dependencies.requireValueUpdate("select1"));

        assertFalse(dependencies.requireBindingUpdate("group2"));
//        assertFalse(dependencies.requireValueUpdate("group2"));

        assertFalse(dependencies.requireBindingUpdate("select2"));
        assertFalse(dependencies.requireValueUpdate("select2"));


        assertTrue(dependencies.requireBindingUpdate("group3"));
//        assertTrue(dependencies.requireValueUpdate("group3"));

        assertTrue(dependencies.requireBindingUpdate("select3"));
        assertTrue(dependencies.requireValueUpdate("select3"));

        assertTrue(dependencies.requireBindingUpdate("group4"));
//        assertTrue(dependencies.requireValueUpdate("group4"));

        assertTrue(dependencies.requireBindingUpdate("select4"));
        assertTrue(dependencies.requireValueUpdate("select4"));

        dependencies.refreshDone();
    }

    /**
     * Return an analyzed static state for the given XForms document URL.
     *
     * @param documentURL   URL to read and analyze
     * @return              static state
     */
    private XFormsStaticState getStaticState(String documentURL) {
        final PipelineContext pipelineContext = new PipelineContext();
        final Document requestDocument = ProcessorUtils.createDocumentFromURL("oxf:/org/orbeon/oxf/xforms/analysis/request.xml", null);
        final ExternalContext externalContext = new TestExternalContext(pipelineContext, requestDocument);


        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();

        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);

        final XFormsAnnotatorContentHandler.Metadata metadata = new XFormsAnnotatorContentHandler.Metadata();
        final SAXStore annotatedSAXStore = new SAXStore(new XFormsExtractorContentHandler(externalContext, identity, metadata));

        // Read the input through the annotator and gather namespace mappings
        XMLUtils.urlToSAX(documentURL, new XFormsAnnotatorContentHandler(annotatedSAXStore, externalContext, metadata), false, false);

        // Get static state document and create static state object
        final Document staticStateDocument = documentResult.getDocument();
        final XFormsStaticState staticState = new XFormsStaticState(pipelineContext, staticStateDocument, metadata, annotatedSAXStore);
        staticState.analyzeIfNecessary(pipelineContext);
        return staticState;
    }
}
