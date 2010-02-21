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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.ResourceManager;
import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Principal;
import java.util.*;

public class ProcessorTest extends TestCase {

    private static Context jndiContext;
    private static PipelineContext pipelineContext;


    private static final int THREAD_COUNT = 1;
    private static final int REPEAT_COUNT = 1;
    private static final String TEST_CONFIG = "oxf.test.config";

    public static void main(String args[]) {
        junit.textui.TestRunner.run(ProcessorTest.suite());
    }

    static {
        try {
            // Initialize log4j
            LoggerFactory.initBasicLogger();

            jndiContext = new InitialContext();

            // Setup resource manager
            Map props = new HashMap();
            java.util.Properties properties = System.getProperties();
            for (Enumeration e = properties.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                if (name.startsWith("oxf.resources."))
                    props.put(name, properties.getProperty(name));
            }
            ResourceManagerWrapper.init(props);
            ResourceManager resourceManager = ResourceManagerWrapper.instance();

            org.orbeon.oxf.properties.Properties.init("oxf:/ops/unit-tests/properties.xml");
            pipelineContext = new PipelineContext();
            pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, jndiContext);

            // Initialize log4j with a DOMConfiguration
            LoggerFactory.initLogger();

            // Use Tyrex for JNDI.
            System.getProperties().setProperty("java.naming.factory.initial", "tyrex.naming.MemoryContextFactory");

            // Run registry.
            XMLProcessorRegistry registry = new XMLProcessorRegistry();
            final String fname = "processors.xml";
            final org.dom4j.Document doc = resourceManager.getContentAsDOM4J(fname);
            final DOMGenerator config = PipelineUtils.createDOMGenerator
                    (doc, fname, DOMGenerator.ZeroValidity, fname);
            PipelineUtils.connect(config, "data", registry, "config");
            registry.start(pipelineContext);
        } catch (Exception e) {
            e.printStackTrace();
            throw new OXFException(e);
        }
    }

    public static Test suite() {
        String currentTestError = null;
        try {
            TestSuite suite = new TestSuite();

            final boolean useParserXinclude = true;
            final Document tests;
            if (useParserXinclude) {
                final URLGenerator urlGenerator = new URLGenerator(System.getProperty(TEST_CONFIG), true);
                final DOMSerializer domSerializer = new DOMSerializer();
                PipelineUtils.connect(urlGenerator, "data", domSerializer, "data");
                domSerializer.start(pipelineContext);
                tests = domSerializer.getDocument(pipelineContext);
            } else {
                // Create processor
                final QName xincludeProcessorName = XMLConstants.XINCLUDE_PROCESSOR_QNAME;
                final ProcessorFactory xincludeProcessorFactory = ProcessorFactoryRegistry.lookup(xincludeProcessorName);
                if (xincludeProcessorFactory == null)
                    throw new OXFException("Cannot find processor factory with name '"
                            + xincludeProcessorName.getNamespacePrefix() + ":" + xincludeProcessorName.getName() + "'");

                final Processor xincludeProcessor = xincludeProcessorFactory.createInstance();

                // Connect input
                final URLGenerator urlGenerator = new URLGenerator(System.getProperty(TEST_CONFIG));
                PipelineUtils.connect(urlGenerator, "data", xincludeProcessor, "config");

                // Connect output
                final DOMSerializer domSerializer = new DOMSerializer();
                PipelineUtils.connect(xincludeProcessor, "data", domSerializer, "data");

                final PipelineContext pipelineContext = new PipelineContext();
                domSerializer.start(pipelineContext);
                tests = domSerializer.getDocument(pipelineContext);
            }

            // If there's a test with the "only" attribute, execute only this one
            Iterator i = XPathUtils.selectIterator(tests, "(/tests/test | /tests/group/test)[@only = 'true'][1] | /tests/group[@only = 'true']/test");
            if (!i.hasNext())
                i = XPathUtils.selectIterator(tests, "/tests/group/test[not(@exclude = 'true')] | /tests/test[not(@exclude = 'true')]");

            for (; i.hasNext();) {
                Element testNode = (Element) i.next();
                Element groupNode = testNode.getParent();
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
                Processor processor = ProcessorUtils.createProcessorWithInputs(testNode);
                processor.setId("Main Test Processor");

                // Connect outputs
                List domSerializers = new ArrayList();
                List expectedDocuments = new ArrayList();
                for (Iterator j = XPathUtils.selectIterator(testNode, "output"); j.hasNext();) {
                    Element outputElement = (Element) j.next();
                    String name = XPathUtils.selectStringValue(outputElement, "@name");
                    if (name == null || name.equals(""))
                        throw new OXFException("Output name is mandatory");

                    Document doc = ProcessorUtils.createDocumentFromEmbeddedOrHref(outputElement, XPathUtils.selectStringValue(outputElement, "@href"));

                    expectedDocuments.add(doc);
                    DOMSerializer domSerializer = new DOMSerializer();
                    PipelineUtils.connect(processor, name, domSerializer, "data");
                    domSerializers.add(domSerializer);
                }

                suite.addTest(new ProcessorTest(description, processor, domSerializers, expectedDocuments));
            }
            return suite;
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
    private List domSerializers;
    private List expectedDocuments;

    private ProcessorTest(String description, Processor processor, List domSerializers, List expectedDocuments) {
        super("test");
        this.description = description;
        this.processor = processor;
        this.domSerializers = domSerializers;
        this.expectedDocuments = expectedDocuments;
    }

    protected ProcessorTest(String name) {
        super(name);
        this.description = name;
    }

    public String getName() {
        return description;
    }

    /**
     * Run test and compare to expected result
     */
    public void test() throws Throwable {

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
    synchronized private void removeUnusedNamespaceDeclarations(Element element) {
        List usedNamespaces = new ArrayList();
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
    synchronized private void getUsedNamespaces(List result, Element element) {
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
                    final PipelineContext pipelineContext = new PipelineContext();
                    pipelineContext.setAttribute(PipelineContext.JNDI_CONTEXT, jndiContext);

                    // Create ExternalContext
                    // TODO: wondering why we use ServletExternalContext here
                    final ExternalContext externalContext = new ServletExternalContext(new TestServletContext(), pipelineContext, new HashMap(), new TestHttpServletRequest(), new TestHttpServletResponse()) {
                        public String getRealPath(String path) {
                            if (path.equals("WEB-INF/exist-conf.xml")) {
                                return ResourceManagerWrapper.instance().getRealPath("/ops/unit-tests/exist-conf.xml");
                            } else {
                                return super.getRealPath(path);
                            }
                        }
                    };
                    StaticExternalContext.setStaticContext(new StaticExternalContext.StaticContext(externalContext, pipelineContext));
                    pipelineContext.setAttribute(PipelineContext.EXTERNAL_CONTEXT, externalContext);

                    try {
                        processor.reset(pipelineContext);
                        if (domSerializers.size() == 0) {
                            // Processor with no output: just run it
                            processor.start(pipelineContext);
                        } else {
                            // Get output and compare to expected result
                            final Iterator domSerializersIterator = domSerializers.iterator();
                            final Iterator expectedNodesIterator = expectedDocuments.iterator();
                            while (domSerializersIterator.hasNext()) {

                                // Get expected
                                final DOMSerializer domSerializer = (DOMSerializer) domSerializersIterator.next();
                                final Document expectedData = (Document) expectedNodesIterator.next();
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

class TestServletContext implements ServletContext {

    // Implement all attribute-related methods so that the application context works
    private Map attributes = new HashMap();

    public Object getAttribute(String s) {
        return attributes.get(s);
    }

    public Enumeration getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    public void removeAttribute(String s) {
        attributes.remove(s);
    }

    public void setAttribute(String s, Object o) {
        attributes.put(s, o);
    }

    public ServletContext getContext(String s) {
        return null;
    }

    public String getInitParameter(String s) {
        return null;
    }

    public Enumeration getInitParameterNames() {
        return null;
    }

    public int getMajorVersion() {
        return 0;
    }

    public String getMimeType(String s) {
        return null;
    }

    public int getMinorVersion() {
        return 0;
    }

    public RequestDispatcher getNamedDispatcher(String s) {
        return null;
    }

    public String getRealPath(String s) {
        return null;
    }

    public RequestDispatcher getRequestDispatcher(String s) {
        return null;
    }

    public URL getResource(String s) throws MalformedURLException {
        return null;
    }

    public InputStream getResourceAsStream(String s) {
        return null;
    }

    public Set getResourcePaths(String s) {
        return null;
    }

    public String getServerInfo() {
        return null;
    }

    public Servlet getServlet(String s) throws ServletException {
        return null;
    }

    public String getServletContextName() {
        return null;
    }

    public Enumeration getServletNames() {
        return null;
    }

    public Enumeration getServlets() {
        return null;
    }

    public void log(Exception e, String s) {
    }

    public void log(String s) {
    }

    public void log(String s, Throwable throwable) {
    }
}

class TestHttpServletRequest implements HttpServletRequest {
    public String getAuthType() {
        return null;
    }

    public String getContextPath() {
        return "/orbeon";
    }

    public Cookie[] getCookies() {
        return new Cookie[0];
    }

    public long getDateHeader(String s) {
        return 0;
    }

    public String getHeader(String s) {
        return null;
    }

    public Enumeration getHeaderNames() {
        return Collections.enumeration(Collections.EMPTY_LIST);
    }

    public Enumeration getHeaders(String s) {
        return Collections.enumeration(Collections.EMPTY_LIST);
    }

    public int getIntHeader(String s) {
        return 0;
    }

    public String getMethod() {
        return "POST";
    }

    public String getPathInfo() {
        return "/some-path";
    }

    public String getPathTranslated() {
        return null;
    }

    public String getQueryString() {
        return null;
    }

    public String getRemoteUser() {
        return null;
    }

    public String getRequestedSessionId() {
        return null;
    }

    public String getRequestURI() {
        return "http://www.orbeon.com/oxf/some-path";
    }

    public StringBuffer getRequestURL() {
        return new StringBuffer("http://www.orbeon.com/oxf/some-path");
    }

    public String getServletPath() {
        return "";
    }

    public HttpSession getSession() {
        return null;
    }

    public HttpSession getSession(boolean b) {
        return null;
    }

    public Principal getUserPrincipal() {
        return null;
    }

    public boolean isRequestedSessionIdFromCookie() {
        return false;
    }

    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    public boolean isRequestedSessionIdValid() {
        return false;
    }

    public boolean isUserInRole(String s) {
        return false;
    }

    public Object getAttribute(String s) {
        return null;
    }

    public Enumeration getAttributeNames() {
        return null;
    }

    public String getCharacterEncoding() {
        return null;
    }

    public int getContentLength() {
        return 0;
    }

    public String getContentType() {
        return null;
    }

    public ServletInputStream getInputStream() throws IOException {
        return null;
    }

    public Locale getLocale() {
        return null;
    }

    public Enumeration getLocales() {
        return null;
    }

    public String getParameter(String s) {
        return null;
    }

    public Map getParameterMap() {
        return null;
    }

    public Enumeration getParameterNames() {
        return null;
    }

    public String[] getParameterValues(String s) {
        return new String[0];
    }

    public String getProtocol() {
        return null;
    }

    public BufferedReader getReader() throws IOException {
        return null;
    }

    public String getRealPath(String s) {
        return null;
    }

    public String getRemoteAddr() {
        return null;
    }

    public String getRemoteHost() {
        return null;
    }

    public RequestDispatcher getRequestDispatcher(String s) {
        return null;
    }

    public String getScheme() {
        return null;
    }

    public String getServerName() {
        return null;
    }

    public int getServerPort() {
        return 0;
    }

    public boolean isSecure() {
        return false;
    }

    public void removeAttribute(String s) {
    }

    public void setAttribute(String s, Object o) {
    }

    public void setCharacterEncoding(String s) throws UnsupportedEncodingException {
    }

    public int getRemotePort() {
        return 0;
    }

    public String getLocalName() {
        return null;
    }

    public String getLocalAddr() {
        return null;
    }

    public int getLocalPort() {
        return 0;
    }
}

class TestHttpServletResponse implements HttpServletResponse {
    public void addCookie(Cookie cookie) {
    }

    public void addDateHeader(String s, long l) {
    }

    public void addHeader(String s, String s1) {
    }

    public void addIntHeader(String s, int i) {
    }

    public boolean containsHeader(String s) {
        return false;
    }

    public String encodeRedirectURL(String s) {
        return null;
    }

    public String encodeRedirectUrl(String s) {
        return null;
    }

    public String encodeURL(String s) {
        return null;
    }

    public String encodeUrl(String s) {
        return null;
    }

    public void sendError(int i) throws IOException {
    }

    public void sendError(int i, String s) throws IOException {
    }

    public void sendRedirect(String s) throws IOException {
    }

    public void setDateHeader(String s, long l) {
    }

    public void setHeader(String s, String s1) {
    }

    public void setIntHeader(String s, int i) {
    }

    public void setStatus(int i) {
    }

    public void setStatus(int i, String s) {
    }

    public void flushBuffer() throws IOException {
    }

    public int getBufferSize() {
        return 0;
    }

    public String getCharacterEncoding() {
        return null;
    }

    public Locale getLocale() {
        return null;
    }

    public ServletOutputStream getOutputStream() throws IOException {
        return null;
    }

    public PrintWriter getWriter() throws IOException {
        return null;
    }

    public boolean isCommitted() {
        return false;
    }

    public void reset() {
    }

    public void resetBuffer() {
    }

    public void setBufferSize(int i) {
    }

    public void setContentLength(int i) {
    }

    public void setContentType(String s) {
    }

    public void setLocale(Locale locale) {
    }

    public void setCharacterEncoding(String s) {
    }

    public String getContentType() {
        return null;
    }
}