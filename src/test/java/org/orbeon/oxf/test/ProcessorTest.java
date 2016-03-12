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

import org.orbeon.errorified.Exceptions;
import org.orbeon.exception.*;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.Dom4j;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ProcessorTest extends ResourceManagerTestBase {

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

    @Parameterized.Parameters(name="{0}")
    public static Collection<Object[]> data() {

        String currentTestError = null;
        try {
            staticSetup();

            final List<Object[]> parameters = new ArrayList<Object[]>();
            final Document tests; {
                final URLGenerator urlGenerator = new URLGenerator(System.getProperty(TEST_CONFIG), true);
                final DOMSerializer domSerializer = new DOMSerializer();
                PipelineUtils.connect(urlGenerator, "data", domSerializer, "data");
                final PipelineContext pipelineContext = new PipelineContext();
                tests = domSerializer.runGetDocument(pipelineContext);
            }

            // If there are tests with a true "only" attribute but not a true "exclude" attribute, execute only those
            Iterator i = XPathUtils.selectNodeIterator(tests, "(/tests/test | /tests/group/test)[ancestor-or-self::*/@only = 'true' and not(ancestor-or-self::*/@exclude = 'true')]");
            // Otherwise, run all tests that are not excluded
            if (!i.hasNext())
                i = XPathUtils.selectNodeIterator(tests, "(/tests/test | /tests/group/test)[not(ancestor-or-self::*/@exclude = 'true')]");

            for (; i.hasNext();) {
                final Element testNode = (Element) i.next();
                final Element groupNode = testNode.getParent();
                if ("true".equals(testNode.attributeValue("ignore"))
                    || ("pe".equalsIgnoreCase(testNode.attributeValue("edition")) && ! Version.isPE())
                    || ("ce".equalsIgnoreCase(testNode.attributeValue("edition")) && Version.isPE()))
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
                for (Iterator j = XPathUtils.selectNodeIterator(testNode, "output"); j.hasNext();) {
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
        } catch (Throwable t) {
            System.err.println(currentTestError + OrbeonFormatter.format(t));
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

        TestRunner runner = new TestRunner();
        runner.run();

        // Check exception
        if (runner.getException() != null) {
            System.err.println(description);
            throw Exceptions.getRootThrowable(runner.getException());
        }

        // Check if got expected result
        if (! runner.isExpectedEqualsActual()) {
            System.err.println(description);
            System.err.println("\nExpected data:\n" + runner.getExpectedDataStringFormatted());
            System.err.println("\nActual data:\n" + runner.getActualDataStringFormatted());
            fail();
        }
    }

    class TestRunner implements Runnable {

        private Throwable exception;
        private boolean expectedEqualsActual = true;

        private String expectedDataStringFormatted;
        private String actualDataStringFormatted;

        public void run() {
            try {
                // Create pipeline context
                final PipelineContext pipelineContext = StringUtils.isNotEmpty(requestURL) ? createPipelineContextWithExternalContext(requestURL) : createPipelineContextWithExternalContext();

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

                        // Run serializer
                        final Document actualData = domSerializer.runGetDocument(pipelineContext);

                        // NOTE: We could make the comparison more configurable, for example to not collapse white space
                        final boolean outputPassed = Dom4j.compareDocumentsIgnoreNamespacesInScopeCollapse(expectedData, actualData);

                        // Display if test not passed
                        if (!outputPassed) {
                            expectedEqualsActual = false;
                            // Store pretty strings
                            expectedDataStringFormatted = Dom4jUtils.domToPrettyString(expectedData);
                            actualDataStringFormatted = Dom4jUtils.domToPrettyString(actualData);
                            break;
                        }
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
