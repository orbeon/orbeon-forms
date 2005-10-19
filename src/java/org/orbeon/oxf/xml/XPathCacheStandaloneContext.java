/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.xml;

import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.expr.VariableDeclaration;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.instruct.LocationMap;
import org.orbeon.saxon.instruct.SlotManager;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.om.NamespaceResolver;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.StaticError;
import org.orbeon.saxon.xpath.Variable;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.oxf.resources.URLFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.URIResolver;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import java.util.Comparator;
import java.util.Iterator;
import java.net.URL;
import java.io.IOException;

public class XPathCacheStandaloneContext extends StandaloneContext {

    private static final URIResolver URI_RESOLVER = new XPathCacheURIResolver();

    private int numSlots;
    private StandaloneContext origContext;
    private Configuration config = new Configuration() {
            public SlotManager makeSlotManager() {
                SlotManager sm = new SlotManager();
                sm.setNumberOfVariables(numSlots);
                return sm;
            }

        /**
         * Get the URIResolver used in this configuration
         *
         * @return the URIResolver. If no URIResolver has been set explicitly, the
         *         default URIResolver is used.
         */

        public URIResolver getURIResolver() {
            return URI_RESOLVER;
        }
    };


    /**
     * Create a StandaloneContext using the default Configuration and NamePool
     */
    public XPathCacheStandaloneContext(StandaloneContext origContext, int numSlots) {
        super();
        this.numSlots = numSlots;
        this.origContext = origContext;
    }

    /**
     * Bind a variable used in an XPath Expression to the XSLVariable element in which it is declared.
     * This method is provided for use by the XPath parser, and it should not be called by the user of
     * the API, or overridden, unless variables are to be declared using a mechanism other than the
     * declareVariable method of this class.
     */

    public VariableDeclaration bindVariable(int fingerprint) throws StaticError {
        return origContext.bindVariable(fingerprint);
    }

    /**
     * Clear all the declared namespaces, including the standard ones (xml, xslt, saxon).
     * Leave only the XML namespace and the default namespace (xmlns="")
     */

    public void clearAllNamespaces() {
        if(origContext != null)
            origContext.clearAllNamespaces();
    }

    /**
     * Clear all the declared namespaces, except for the standard ones (xml, xslt, saxon, xdt)
     */

    public void clearNamespaces() {
        if(origContext != null)
            origContext.clearNamespaces();
    }

    /**
     * Declare a named collation
     *
     * @param name       The name of the collation (technically, a URI)
     * @param comparator The Java Comparator used to implement the collating sequence
     * @param isDefault  True if this is to be used as the default collation
     */

    public void declareCollation(String name, Comparator comparator, boolean isDefault) {
        origContext.declareCollation(name, comparator, isDefault);
    }

    /**
     * Declare a namespace whose prefix can be used in expressions
     *
     * @param prefix The namespace prefix. Must not be null. Must not be the empty string
     *               ("") - unqualified names in an XPath expression always refer to the null namespace.
     * @param uri    The namespace URI. Must not be null.
     */

    public void declareNamespace(String prefix, String uri) {
        origContext.declareNamespace(prefix, uri);
    }

    /**
     * Declare a variable. A variable must be declared before an expression referring
     * to it is compiled.
     */

    public Variable declareVariable(String qname, Object initialValue) throws XPathException {
        return origContext.declareVariable(qname, initialValue);
    }

    /**
     * Get the Base URI of the stylesheet element, for resolving any relative URI's used
     * in the expression.
     * Used by the document() function, resolve-uri(), etc.
     *
     * @return "" if no base URI has been set
     */

    public String getBaseURI() {
        return origContext.getBaseURI();
    }

    /**
     * Get a named collation.
     *
     * @return the collation identified by the given name, as set previously using declareCollation.
     *         Return null if no collation with this name is found.
     */

    public Comparator getCollation(String name) {
        return origContext.getCollation(name);
    }

    /**
     * Get the system configuration
     */

    public Configuration getConfiguration() {
        if(config == null)
            return super.getConfiguration();
        else
            return config;
    }

    /**
     * Get the name of the default collation.
     *
     * @return the name of the default collation; or the name of the codepoint collation
     *         if no default collation has been defined
     */

    public String getDefaultCollationName() {
        return origContext.getDefaultCollationName();
    }

    /**
     * Get the default XPath namespace, as a namespace code that can be looked up in the NamePool
     */

    public short getDefaultElementNamespace() {
        return origContext.getDefaultElementNamespace();
    }

    /**
     * Get the default function namespace
     */

    public String getDefaultFunctionNamespace() {
        return origContext.getDefaultFunctionNamespace();
    }

    /**
     * Use this NamespaceContext to resolve a lexical QName
     *
     * @param qname      the lexical QName; this must have already been lexically validated
     * @param useDefault true if the default namespace is to be used to resolve an unprefixed QName
     * @param pool       the NamePool to be used
     * @return the integer fingerprint that uniquely identifies this name
     */

    public int getFingerprint(String qname, boolean useDefault, NamePool pool) {
        return origContext.getFingerprint(qname, useDefault, pool);
    }

    /**
     * Get the function library containing all the in-scope functions available in this static
     * context
     */

    public FunctionLibrary getFunctionLibrary() {
        return origContext.getFunctionLibrary();
    }

    /**
     * Get the line number of the expression within that container.
     * Used to construct error messages.
     *
     * @return -1 always
     */

    public int getLineNumber() {
        return origContext.getLineNumber();
    }

    public LocationMap getLocationMap() {
        return origContext.getLocationMap();
    }

    /**
     * Get the NamePool used for compiling expressions
     */

    public NamePool getNamePool() {
        return origContext.getNamePool();
    }

    public NamespaceResolver getNamespaceResolver() {
        return super.getNamespaceResolver();
    }

    /**
     * Get the stack frame map containing the slot number allocations for the variables declared
     * in this static context
     */

    public SlotManager getStackFrameMap() {
        return origContext.getStackFrameMap();
    }

    /**
     * Get the system ID of the container of the expression. Used to construct error messages.
     *
     * @return "" always
     */

    public String getSystemId() {
        return origContext.getSystemId();
    }

    /**
     * Get the URI for a prefix, using the declared namespaces as
     * the context for namespace resolution. The default namespace is NOT used
     * when the prefix is empty.
     * This method is provided for use by the XPath parser.
     *
     * @param prefix The prefix
     * @throws org.orbeon.saxon.xpath.XPathException
     *          if the prefix is not declared
     */

    public String getURIForPrefix(String prefix) throws XPathException {
        return origContext.getURIForPrefix(prefix);
    }

    /**
     * Get the namespace URI corresponding to a given prefix. Return null
     * if the prefix is not in scope.
     *
     * @param prefix     the namespace prefix
     * @param useDefault true if the default namespace is to be used when the
     *                   prefix is ""
     * @return the uri for the namespace, or null if the prefix is not in scope.
     *         Return "" if the prefix maps to the null namespace.
     */

    public String getURIForPrefix(String prefix, boolean useDefault) {
        return origContext.getURIForPrefix(prefix, useDefault);
    }

    public boolean isImportedSchema(String namespace) {
        return origContext.isImportedSchema(namespace);
    }

    /**
     * Determine whether Backwards Compatible Mode is used
     *
     * @return false; XPath 1.0 compatibility mode is not supported in the standalone
     *         XPath API
     */

    public boolean isInBackwardsCompatibleMode() {
        return origContext.isInBackwardsCompatibleMode();
    }

    /**
     * Issue a compile-time warning. This method is used during XPath expression compilation to
     * output warning conditions. The default implementation writes the message to System.err. To
     * change the destination of messages, create a subclass of StandaloneContext that overrides
     * this method.
     */

    public void issueWarning(String s) {
        origContext.issueWarning(s);
    }

    /**
     * Get an iterator over all the prefixes declared in this namespace context. This will include
     * the default namespace (prefix="") and the XML namespace where appropriate
     */

    public Iterator iteratePrefixes() {
        return origContext.iteratePrefixes();
    }

    /**
     * Set the base URI in the static context
     */

    public void setBaseURI(String baseURI) {
        origContext.setBaseURI(baseURI);
    }

    /**
     * Set the default function namespace
     */

    public void setDefaultFunctionNamespace(String uri) {
        origContext.setDefaultFunctionNamespace(uri);
    }

    /**
     * Set the function library to be used
     */

    public void setFunctionLibrary(FunctionLibrary lib) {
        origContext.setFunctionLibrary(lib);
    }

    public void setLocationMap(LocationMap locationMap) {
        origContext.setLocationMap(locationMap);
    }

    /**
     * Set all the declared namespaces to be the namespaces that are in-scope for a given node.
     * In addition, the standard namespaces (xml, xslt, saxon) are declared.
     *
     * @param node The node whose in-scope namespaces are to be used as the context namespaces.
     *             Note that this will have no effect unless this node is an element.
     */

    public void setNamespaces(NodeInfo node) {
        origContext.setNamespaces(node);
    }

    /**
     * Create a StandaloneContext using the default Configuration and NamePool
     */
    private static class XPathCacheURIResolver implements URIResolver {
        public Source resolve(String href, String base) throws TransformerException {
            try {
                // Saxon Document.makeDoc() changes the base to "" if it is null
                if ("".equals(base))
                    base = null;
                URL url = URLFactory.createURL(base, href);
                return new SAXSource(XMLUtils.newSAXParser(false, true).getXMLReader(), new InputSource(url.openStream()));
            } catch (SAXException e) {
                throw new TransformerException(e);
            } catch (IOException e) {
                throw new TransformerException(e);
            }

        }
    }

}

