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
package org.orbeon.oxf.test;

import org.apache.commons.lang.StringUtils;
import org.dom4j.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.ResourceManager;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.*;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ProcessorTest extends ResourceManagerTestBase {

    private static Context jndiContext;
    private static PipelineContext pipelineContext;

    private static final int THREAD_COUNT = 1;
    private static final int REPEAT_COUNT = 1;
    private static final String TEST_CONFIG = "oxf.test.config";

    @Test
    public void runTest() throws Throwable {
        test();
    }

    @Rule
        public TestName name= new TestName() {
        @Override
        public String getMethodName() {
            return getName();
        }
    };

    @Override
    public String toString() {
        return getName();
    }

    private static void setupContext() {
        try {
            // Initialize log4j
            LoggerFactory.initBasicLogger();

            ResourceManager resourceManager = ResourceManagerWrapper.instance();
            pipelineContext = new PipelineContext();
            jndiContext = new InitialContext();
            pipelineContext.setAttribute(ProcessorService.JNDI_CONTEXT, jndiContext);

            // Initialize log4j with a DOMConfiguration
            LoggerFactory.initLogger();

            // Use Tyrex for JNDI.
            System.getProperties().setProperty("java.naming.factory.initial", "tyrex.naming.MemoryContextFactory");

            // Run registry.
            XMLProcessorRegistry registry = new XMLProcessorRegistry();
            final String fname = "processors.xml";
            final org.dom4j.Document doc = resourceManager.getContentAsDOM4J(fname, XMLUtils.ParserConfiguration.XINCLUDE_ONLY, true);
            final DOMGenerator config = PipelineUtils.createDOMGenerator(doc, fname, DOMGenerator.ZeroValidity, fname);
            PipelineUtils.connect(config, "data", registry, "config");
            registry.start(pipelineContext);
        } catch (Exception e) {
            e.printStackTrace();
            throw new OXFException(e);
        }
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {

        String currentTestError = null;
        try {
            staticSetup();
            setupContext();
            
            final List<Object[]> parameters = new ArrayList<Object[]>();
            final Document tests; {
                final URLGenerator urlGenerator = new URLGenerator(System.getProperty(TEST_CONFIG), true);
                final DOMSerializer domSerializer = new DOMSerializer();
                PipelineUtils.connect(urlGenerator, "data", domSerializer, "data");
                domSerializer.start(pipelineContext);
                tests = domSerializer.getDocument(pipelineContext);
            }

            // If there are tests with a true "only" attribute but not a true "exclude" attribute, execute only those
            Iterator i = XPathUtils.selectIterator(tests, "(/tests/test | /tests/group/test)[ancestor-or-self::*/@only = 'true' and not(ancestor-or-self::*/@exclude = 'true')]");
            // Otherwise, run all tests that are not excluded
            if (!i.hasNext())
                i = XPathUtils.selectIterator(tests, "(/tests/test | /tests/group/test)[not(ancestor-or-self::*/@exclude = 'true')]");

            for (; i.hasNext();) {
                final Element testNode = (Element) i.next();
                final Element groupNode = testNode.getParent();
                if (testNode.attributeValue("ignore") != null)
                    continue;
                String description = testNode.attributeValue("description", "");
                if (groupNode.getName().equals("group")) {
                    String groupDescription = groupNode.attributeValue("description");
                    if (groupDescription != null)
                        description = groupDescription + " - " + description;
                }
                currentTestError = "Error when executing test with description: '" + description + "'";

                // Create processor and connect its inputs
                final Processor processor = ProcessorUtils.createProcessorWithInputs(testNode);
                processor.setId("Main Test Processor");

                // Connect outputs
                final List<DOMSerializer> domSerializers = new ArrayList<DOMSerializer>();
                final List<Document> expectedDocuments = new ArrayList<Document>();
                for (Iterator j = XPathUtils.selectIterator(testNode, "output"); j.hasNext();) {
                    final Element outputElement = (Element) j.next();
                    final String name = XPathUtils.selectStringValue(outputElement, "@name");
                    if (name == null || name.equals(""))
                        throw new OXFException("Output name is mandatory");

                    final Document doc = ProcessorUtils.createDocumentFromEmbeddedOrHref(outputElement, XPathUtils.selectStringValue(outputElement, "@href"));

                    expectedDocuments.add(doc);
                    final DOMSerializer domSerializer = new DOMSerializer();
                    PipelineUtils.connect(processor, name, domSerializer, "data");
                    domSerializers.add(domSerializer);
                }

                final String requestURL = testNode.attributeValue("request", "");

                parameters.add(new Object[] { description, processor, requestURL, domSerializers, expectedDocuments });
            }
            return parameters;
        } catch (Exception e) {
            System.err.println(currentTestError);
            e.printStackTrace();
            throw new OXFException(e);
        } catch (Throwable t) {
            System.err.println(currentTestError);
            t.printStackTrace();
            throw new OXFException(currentTestError);
        }
    }

    private String description;
    private Processor processor;
    private String requestURL;
    private List<DOMSerializer> domSerializers;
    private List<Document> expectedDocuments;

    public ProcessorTest(String description, Processor processor, String requestURL, List<DOMSerializer> domSerializers, List<Document> expectedDocuments) {
        this.description = description;
        this.requestURL = requestURL;
        this.processor = processor;
        this.domSerializers = domSerializers;
        this.expectedDocuments = expectedDocuments;
    }

    protected ProcessorTest(String name) {
        this.description = name;
    }

    public String getName() {
        return description;
    }

    /**
     * Run test and compare to expected result
     */
    private void test() throws Throwable {

        // Create threads
        TestThread[] threads = new TestThread[THREAD_COUNT];
        for (int t = 0; t < THREAD_COUNT; t++)
            threads[t] = new TestThread("Thread#" + t);

        // Start threads
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].start();
        }

        // Wait for all threads
        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i].join();

            // Check exception
            if (threads[i].getException() != null) {
                System.err.println(description);
                Throwable throwable = OXFException.getRootThrowable(threads[i].getException());
                throw throwable;
            }

            // Check if got expected result
            if (!threads[i].isExpectedEqualsActual()) {
                System.err.println(description);
                System.err.println("\nExpected data:\n" + threads[i].getExpectedDataStringFormatted());
                System.err.println("\nActual data:\n" + threads[i].getActualDataStringFormatted());
                fail();
            }
        }
    }

    /**
     * Remove the element the namespace declaration that are not used
     * in this element or child elements.
     */
    private synchronized void removeUnusedNamespaceDeclarations(Element element) {
        List<String> usedNamespaces = new ArrayList<String>();
        getUsedNamespaces(usedNamespaces, element);
        List declaredNamespaces = element.declaredNamespaces();
        for (Iterator i = declaredNamespaces.iterator(); i.hasNext();) {
            Namespace namespace = (Namespace) i.next();
            if (!usedNamespaces.contains(namespace.getURI())) {
                i.remove();
            }
        }
        for (Iterator i = element.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            removeUnusedNamespaceDeclarations(child);
        }
    }

    /**
     * Adds to the result list the URI of the namespaces actually used
     * by elements or attribute in this element or child elements.
     *
     * @param result List of String (URI)
     */
    private synchronized void getUsedNamespaces(List<String> result, Element element) {
        if (element != null) {
            if (!"".equals(element.getNamespaceURI()))
                result.add(element.getNamespaceURI());
            for (Iterator i = element.attributes().iterator(); i.hasNext();) {
                Attribute attribute = (Attribute) i.next();
                if (!attribute.getNamespaceURI().equals("")) {
                    result.add(attribute.getNamespaceURI());
                }
            }
            for (Iterator i = element.elements().iterator(); i.hasNext();) {
                Element child = (Element) i.next();
                getUsedNamespaces(result, child);
            }
        }
    }

    class TestThread extends Thread {

        private Throwable exception;
        private boolean expectedEqualsActual = true;
        private String expectedDataString;
        private String actualDataString;

        private String expectedDataStringFormatted;
        private String actualDataStringFormatted;

        public TestThread(String name) {
            super(name);
        }

        public void run() {
            int executionCount = 0;
            try {
                for (; executionCount < REPEAT_COUNT; executionCount++) {
                    // Create pipeline context
                    final PipelineContext pipelineContext = StringUtils.isNotEmpty(requestURL) ? createPipelineContextWithExternalContext(requestURL) : createPipelineContextWithExternalContext();
                    pipelineContext.setAttribute(ProcessorService.JNDI_CONTEXT, jndiContext);

                    // Get ExternalContext
                    final ExternalContext externalContext = NetUtils.getExternalContext();

                    StaticExternalContext.setStaticContext(new StaticExternalContext.StaticContext(externalContext, pipelineContext));
//                    pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);

                    try {
                        processor.reset(pipelineContext);
                        if (domSerializers.size() == 0) {
                            // Processor with no output: just run it
                            processor.start(pipelineContext);
                        } else {
                            // Get output and compare to expected result
                            final Iterator<DOMSerializer> domSerializersIterator = domSerializers.iterator();
                            final Iterator<Document> expectedNodesIterator = expectedDocuments.iterator();
                            while (domSerializersIterator.hasNext()) {

                                // Get expected
                                final DOMSerializer domSerializer = domSerializersIterator.next();
                                final Document expectedData = expectedNodesIterator.next();
                                // TODO: we want to remove that (avernet 2004-12-14)
                                removeUnusedNamespaceDeclarations(expectedData.getRootElement());

                                // Run serializer
                                domSerializer.start(pipelineContext);

                                // Get actual data
                                final Document actualData = domSerializer.getDocument(pipelineContext);
                                // TODO: we want to remove that (avernet 2004-12-14)
                                removeUnusedNamespaceDeclarations(actualData.getRootElement());

                                // Compare converting to strings
                                expectedDataString = Dom4jUtils.domToCompactString(expectedData);
                                actualDataString = Dom4jUtils.domToCompactString(actualData);
                                final boolean outputPassed = expectedDataString.equals(actualDataString);

                                // Display if test not passed
                                if (!outputPassed) {
                                    expectedEqualsActual = false;
                                    // Store pretty strings
                                    expectedDataStringFormatted = Dom4jUtils.domToPrettyString(expectedData);
                                    actualDataStringFormatted = Dom4jUtils.domToPrettyString(actualData);
                                    break;
                                }
                            }
                            // Don't bother repeating the test if it failed
                            if (!expectedEqualsActual)
                                break;
                        }
                    } finally {
                        StaticExternalContext.removeStaticContext();
                    }
                }
            } catch (Throwable e) {
                exception = e;
            }
        }

        public Throwable getException() {
            return exception;
        }

        public boolean isExpectedEqualsActual() {
            return expectedEqualsActual;
        }

        public String getExpectedDataStringFormatted() {
            return expectedDataStringFormatted;
        }

        public String getActualDataStringFormatted() {
            return actualDataStringFormatted;
        }
    }
}
