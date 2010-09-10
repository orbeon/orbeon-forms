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
import org.junit.Test;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.test.ResourceManagerTestBase;
import org.orbeon.oxf.util.NumberUtils;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;

import java.util.*;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class XFormsStaticStateTest extends ResourceManagerTestBase {

    @Test
    public void testLHHAAnalysis() {
//        if (Version.instance().isPE()) { // only test this feature if we are the PE version
//            final XFormsStaticState staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/lhha.xml");
//            final Map<String, String> namespaces = new HashMap<String, String>();
//            namespaces.put("", "");
//
//            // Hold the current list of changes
//            final Set<String> currentChanges = new HashSet<String>();
//
//            final PathMapUIDependencies dependencies = new PathMapUIDependencies(staticState.getIndentedLogger(), staticState) {
//                @Override
//                protected Set<String> getModifiedPaths() {
//                    return currentChanges;
//                }
//            };
//
//            staticState.dumpAnalysis();
//        }
    }

    @Test
    public void testBindAnalysis() {
        if (Version.isPE()) { // only test this feature if we are the PE version
            final XFormsStaticState staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/binds.xml");

//            final PipelineContext pipelineContext = new PipelineContext();
//            staticState.dumpAnalysis(pipelineContext);

            // TODO: test computedBindExpressionsInstances and validationBindInstances
            {
                final Model model1 = staticState.getModel("model1");
                assertTrue(model1.figuredBindAnalysis);

                assertTrue(model1.bindInstances.contains("instance11"));
                assertTrue(model1.bindInstances.contains("instance12"));
                assertTrue(model1.bindInstances.contains("instance13"));
            }
            {
                final Model model2 = staticState.getModel("model2");
                assertTrue(model2.figuredBindAnalysis);

                assertFalse(model2.bindInstances.contains("instance21"));
            }
            {
                final Model model3 = staticState.getModel("model3");
                assertTrue(model3.figuredBindAnalysis);

                assertFalse(model3.bindInstances.contains("instance31"));
                assertTrue(model3.bindInstances.contains("instance32"));
            }
            {
                final Model model4 = staticState.getModel("model4");
                assertTrue(model4.figuredBindAnalysis);

                assertTrue(model4.bindInstances.contains("instance41"));
                assertFalse(model4.bindInstances.contains("instance42"));
            }
            {
                final Model model5 = staticState.getModel("model5");
                assertTrue(model5.figuredBindAnalysis);

                assertTrue(model5.validationBindInstances.contains("instance51"));
                assertFalse(model5.computedBindExpressionsInstances.contains("instance51"));

                assertFalse(model5.validationBindInstances.contains("instance52"));
                assertTrue(model5.computedBindExpressionsInstances.contains("instance52"));
            }
        }
    }

    @Test
    public void testXPathAnalysis() {
        if (Version.isPE()) { // only test this feature if we are the PE version
            final XFormsStaticState staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/form.xml");
            final Map<String, String> namespaces = new HashMap<String, String>();
            namespaces.put("", "");

            // Hold the current list of changes
            final Set<String> currentChanges = new HashSet<String>();

            final PathMapXPathDependencies dependencies = new PathMapXPathDependencies(staticState.getIndentedLogger(), staticState) {
                @Override
                protected Set<String> getModifiedPaths() {
                    return currentChanges;
                }
            };

            final PipelineContext pipelineContext = new PipelineContext();
            staticState.dumpAnalysis(pipelineContext);

            // == Value change to default ==================================================================================
            currentChanges.clear();
            currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('default')/a"));

            assertFalse(dependencies.requireBindingUpdate("trigger1"));
            assertFalse(dependencies.requireBindingUpdate("trigger2"));

            assertFalse(dependencies.requireBindingUpdate("select1"));
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
    }

    @Test
    public void testVariables() {
        if (Version.isPE()) { // only test this feature if we are the PE version
            final XFormsStaticState staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/variables.xml");
            final Map<String, String> namespaces = new HashMap<String, String>();
            namespaces.put("", "");

            // Hold the current list of changes
            final Set<String> currentChanges = new HashSet<String>();

            final PathMapXPathDependencies dependencies = new PathMapXPathDependencies(staticState.getIndentedLogger(), staticState) {
                @Override
                protected Set<String> getModifiedPaths() {
                    return currentChanges;
                }
            };

//            final PipelineContext pipelineContext = new PipelineContext();
//            staticState.dumpAnalysis(pipelineContext);

            // == Value change to default ==================================================================================
            currentChanges.clear();
            currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('default')/value"));

            assertFalse(dependencies.requireBindingUpdate("values"));
            assertTrue(dependencies.requireValueUpdate("values"));

            assertFalse(dependencies.requireBindingUpdate("repeat"));

            assertFalse(dependencies.requireBindingUpdate("value"));
            assertTrue(dependencies.requireValueUpdate("value"));

            assertFalse(dependencies.requireBindingUpdate("input"));
            assertTrue(dependencies.requireValueUpdate("input"));
        }
    }
    
    @Test
    public void testModelVariables() {
        if (Version.isPE()) { // only test this feature if we are the PE version
            final XFormsStaticState staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/model-variables.xml");
            final Map<String, String> namespaces = new HashMap<String, String>();
            namespaces.put("", "");

            // Hold the current list of changes
            final Set<String> currentChanges = new HashSet<String>();

            final PathMapXPathDependencies dependencies = new PathMapXPathDependencies(staticState.getIndentedLogger(), staticState) {
                @Override
                protected Set<String> getModifiedPaths() {
                    return currentChanges;
                }
            };

//            final PipelineContext pipelineContext = new PipelineContext();
//            staticState.dumpAnalysis(pipelineContext);

            // == Value change to instance1 ============================================================================
            currentChanges.clear();
            currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('instance1')"));

            // No binding update
            assertFalse(dependencies.requireBindingUpdate("output1"));
            assertFalse(dependencies.requireBindingUpdate("group2"));
            assertFalse(dependencies.requireBindingUpdate("output2a"));
            assertFalse(dependencies.requireBindingUpdate("output2b"));
            assertFalse(dependencies.requireBindingUpdate("group3"));
            assertFalse(dependencies.requireBindingUpdate("output3a"));
            assertFalse(dependencies.requireBindingUpdate("output3b"));
            // TODO: group4 has @context attribute so not analyzed
//            assertFalse(dependencies.requireBindingUpdate("group4"));
//            assertFalse(dependencies.requireBindingUpdate("output4a"));
//            assertFalse(dependencies.requireBindingUpdate("output4b"));

            // $mv pointing to model1 must update their value
            assertTrue(dependencies.requireValueUpdate("output1"));
            assertTrue(dependencies.requireValueUpdate("output2b"));
            assertTrue(dependencies.requireValueUpdate("output3a"));
            assertTrue(dependencies.requireValueUpdate("output3b"));
            assertTrue(dependencies.requireValueUpdate("output4a"));
            assertTrue(dependencies.requireValueUpdate("output4b"));
            assertTrue(dependencies.requireValueUpdate("output5a"));

            // $mv pointing to model2 must not update
            assertFalse(dependencies.requireValueUpdate("output2a"));
            assertFalse(dependencies.requireValueUpdate("output3c"));
            assertFalse(dependencies.requireValueUpdate("output4c"));
            assertFalse(dependencies.requireValueUpdate("output5b"));

            dependencies.refreshDone();
            
            // == Value change to instance2 ============================================================================
            currentChanges.clear();
            currentChanges.add(XPathAnalysis.getInternalPath(namespaces, "instance('instance2')"));

            // No binding update
            assertFalse(dependencies.requireBindingUpdate("output1"));
            assertFalse(dependencies.requireBindingUpdate("group2"));
            assertFalse(dependencies.requireBindingUpdate("output2a"));
            assertFalse(dependencies.requireBindingUpdate("output2b"));
            assertFalse(dependencies.requireBindingUpdate("group3"));
            assertFalse(dependencies.requireBindingUpdate("output3a"));
            assertFalse(dependencies.requireBindingUpdate("output3b"));
            // TODO: group4 has @context attribute so not analyzed
//            assertFalse(dependencies.requireBindingUpdate("group4"));
//            assertFalse(dependencies.requireBindingUpdate("output4a"));
//            assertFalse(dependencies.requireBindingUpdate("output4b"));

            // $mv pointing to model1 must not update their value
            assertFalse(dependencies.requireValueUpdate("output1"));
            assertFalse(dependencies.requireValueUpdate("output2b"));
            assertFalse(dependencies.requireValueUpdate("output3a"));
            assertFalse(dependencies.requireValueUpdate("output3b"));
            assertFalse(dependencies.requireValueUpdate("output4a"));
            assertFalse(dependencies.requireValueUpdate("output4b"));
            assertFalse(dependencies.requireValueUpdate("output5a"));

            // $mv pointing to model2 must update
            assertTrue(dependencies.requireValueUpdate("output2a"));
            assertTrue(dependencies.requireValueUpdate("output3c"));
            assertTrue(dependencies.requireValueUpdate("output4c"));
            assertTrue(dependencies.requireValueUpdate("output5b"));
        }
    }

    /**
     * Return an analyzed static state for the given XForms document URL.
     *
     * @param documentURL   URL to read and analyze
     * @return              static state
     */
    public static XFormsStaticState getStaticState(String documentURL) {
        return getStaticState(ProcessorUtils.createDocumentFromURL(documentURL, null));
    }

    /**
     * Return an analyzed static state for the given XForms document.
     *
     * @param formDocument  document to analyze
     * @return              static state
     */
    public static XFormsStaticState getStaticState(Document formDocument) {

        final PipelineContext pipelineContext = new PipelineContext();

        final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();

        final LocationDocumentResult documentResult = new LocationDocumentResult();
        identity.setResult(documentResult);

        final XFormsStaticState.Metadata metadata = new XFormsStaticState.Metadata();
        final XMLUtils.DigestContentHandler digestContentHandler = new XMLUtils.DigestContentHandler("MD5");
        final SAXStore annotatedTemplate = new SAXStore();

        // Read the input through the annotator and gather namespace mappings
        TransformerUtils.writeDom4j(formDocument, new XFormsAnnotatorContentHandler(annotatedTemplate, new XFormsExtractorContentHandler(new TeeXMLReceiver(identity, digestContentHandler), metadata), metadata));

        final String digest = NumberUtils.toHexString(digestContentHandler.getResult());

        // Get static state document and create static state object
        final Document staticStateDocument = documentResult.getDocument();
        return new XFormsStaticState(pipelineContext, staticStateDocument, digest, metadata);
    }
}
