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
package org.orbeon.oxf.processor.sql;

import org.dom4j.Node;
import org.jaxen.Function;
import org.jaxen.FunctionContext;
import org.jaxen.UnresolvableException;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DatabaseContext;
import org.orbeon.oxf.processor.Datasource;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.xml.XPathContentHandler;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.helpers.NamespaceSupport;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Interpreter context for the SQL processor.
 */
class SQLProcessorInterpreterContext extends DatabaseContext {

    private OXFProperties.PropertySet propertySet;

    // Locator for datasource declaration, if any
    private Locator documentLocator;

    // Either one of those two must be set
    private String jndiName;
    private Datasource datasource;

    private PipelineContext pipelineContext;
    private Node input;
    private XPathContentHandler xpathContentHandler;
    private ContentHandler output;
    private NamespaceSupport namespaceSupport;

    private List executionContextStack;
    private List currentNodes;
    private List currentFunctions = new ArrayList();

    public SQLProcessorInterpreterContext(OXFProperties.PropertySet propertySet) {
        this.propertySet = propertySet;
    }

    public OXFProperties.PropertySet getPropertySet() {
        return propertySet;
    }

    private static class ExecutionContext {
        public ResultSet resultSet;
        public PreparedStatement preparedStatement;
        public String statementString;
        public boolean emptyResultSet;
        public int rowPosition;
        public int updateCount;
    }

    public void pushResultSet() {
        if (executionContextStack == null)
            executionContextStack = new ArrayList();
        executionContextStack.add(new ExecutionContext());
    }

    public PipelineContext getPipelineContext() {
        return pipelineContext;
    }

    public void setPipelineContext(PipelineContext pipelineContext) {
        this.pipelineContext = pipelineContext;
    }

    public void setConnection(Locator documentLocator, String datasourceName) throws Exception {
        this.documentLocator = documentLocator;
        jndiName = "jdbc/" + datasourceName;
    }

    public DatabaseDelegate getDelegate() {
        // Try to obtain delegate from context
        Context context = getContext(pipelineContext);
        String delegateKey = (jndiName != null) ? jndiName : datasource.toString();
        DatabaseDelegate databaseDelegate = (DatabaseDelegate) context.delegates.get(delegateKey);
        if (databaseDelegate == null) {
            // Delegate needs to be created
            try {
                DatabaseMetaData databaseMetaData = getConnection().getMetaData();
                String productName = databaseMetaData.getDatabaseProductName();
                Class clazz = null;
                if ("oracle".equalsIgnoreCase(productName)) {
                    // First try Tomcat (4.1 or greater)
                    try {
                        clazz = getClass().getClassLoader().loadClass("org.orbeon.oxf.processor.sql.SQLProcessorOracleTomcatDelegate");
                        SQLProcessor.logger.info("Using Oracle Tomcat delegate.");
                    } catch (Throwable t) {
                        // Ignore
                    }
                    // Then try WebLogic (8.1 or greater)
                    if (clazz == null) {
                        try {
                            clazz = getClass().getClassLoader().loadClass("org.orbeon.oxf.processor.sql.SQLProcessorOracleWebLogic81Delegate");
                            SQLProcessor.logger.info("Using Oracle WebLogic delegate.");
                        } catch (Throwable t) {
                            // Ignore
                        }
                    }
                    // Then try the generic delegate
                    if (clazz == null) {
                        try {
                            clazz = getClass().getClassLoader().loadClass("org.orbeon.oxf.processor.sql.SQLProcessorOracleGenericDelegate");
                            SQLProcessor.logger.info("Using Oracle generic delegate.");
                        } catch (Throwable t) {
                            clazz = SQLProcessorGenericDelegate.class;
                            SQLProcessor.logger.info("Could not load Oracle database delegate. Using generic delegate.");
                        }
                    }
                } else if ("HSQL Database Engine".equalsIgnoreCase(productName)) {
                    // HSQLDB
                    try {
                        clazz = getClass().getClassLoader().loadClass("org.orbeon.oxf.processor.sql.SQLProcessorHSQLDBDelegate");
                        SQLProcessor.logger.info("Using HSQLDB delegate.");
                    } catch (Throwable t) {
                        clazz = SQLProcessorGenericDelegate.class;
                        SQLProcessor.logger.info("Could not load HSQLDB database delegate. Using generic delegate.");
                    }
                } else {
                    clazz = SQLProcessorGenericDelegate.class;
                }
                databaseDelegate = (DatabaseDelegate) clazz.newInstance();
                getContext(pipelineContext).delegates.put(delegateKey, databaseDelegate);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
        return databaseDelegate;
    }

    public void popResultSet() {
        executionContextStack.remove(executionContextStack.size() - 1);
    }

    public void pushCurrentNode(Node node) {
        currentNodes.add(node);
    }

    public Node popCurrentNode() {
        return (Node) currentNodes.remove(currentNodes.size() - 1);
    }

    public Node getCurrentNode() {
        return (Node) currentNodes.get(currentNodes.size() - 1);
    }

    public void pushFunctions(Map functions) {
        currentFunctions.add(functions);
    }

    public Map popFunctions() {
        return (Map) currentFunctions.remove(currentFunctions.size() - 1);
    }

    private FunctionContext functionContext = new FunctionContext() {
        public Function getFunction(String namespaceURI, String prefix, String localName) throws UnresolvableException {

            String fullName = (namespaceURI == null || "".equals(namespaceURI))
                    ? localName : "{" + namespaceURI + "}" + localName;

            for (int i = currentFunctions.size() - 1; i >= 0; i--) {
                Map current = (Map) currentFunctions.get(i);
                Function function = (Function) current.get(fullName);
                if (function != null) {
                    return function;
                }
            }

            // If the namespace is local, we know the function is not declared
            if (SQLProcessor.SQL_NAMESPACE_URI.equals(namespaceURI))
                throw new UnresolvableException("Undeclared function: {" + namespaceURI + "}" + localName);

            // Nothing found, but maybe it is one of the default functions
            return null;
        }
    };

    public FunctionContext getFunctionContext() {
        return functionContext;
    }

    private ExecutionContext getExecutionContext(int level) {
        return (ExecutionContext) executionContextStack.get(executionContextStack.size() - 1 - level);
    }

    public void setResultSet(ResultSet resultSet) {
        ExecutionContext executionContext = getExecutionContext(0);
        executionContext.resultSet = resultSet;
    }

    public void setStatement(PreparedStatement stmt) {
        ExecutionContext executionContext = getExecutionContext(0);
        executionContext.preparedStatement = stmt;
    }

    public void setStatementString(String statementString) {
        ExecutionContext executionContext = getExecutionContext(0);
        executionContext.statementString = statementString;
    }

    public ResultSet getResultSet(int level) {
        ExecutionContext executionContext = getExecutionContext(level);
        return executionContext.resultSet;
    }

    public PreparedStatement getStatement(int level) {
        ExecutionContext executionContext = getExecutionContext(level);
        return executionContext.preparedStatement;
    }

    public String getStatementString(int level) {
        ExecutionContext executionContext = getExecutionContext(level);
        return executionContext.statementString;
    }

    public String getStatementString() {
        return getStatementString(0);
    }

    public Connection getConnection() {
        if (jndiName != null) {
            // Connection was configured with as a JDBC datasource
            try {
                return getConnection(pipelineContext, jndiName);
            } catch (RuntimeException e) {
                if (documentLocator != null)
                    throw new ValidationException(e, new LocationData(documentLocator));
                else
                    throw e;
            }
        } else if (datasource != null) {
            // Connection was configured with an internal datasource
            return getConnection(pipelineContext, datasource);
        } else {
            throw new OXFException("No datasource configured, cannot get connection to database.");
        }
    }

    public Node getInput() {
        return input;
    }

    /**
     * Set the input document. This also determines the default current node
     * for all XPath expressions.
     */
    public void setInput(Node input) {
        this.input = input;
        currentNodes = new ArrayList();
        currentNodes.add(input);
    }

    /**
     * Set optional Datasource object. If null, the configuration has to contain a reference to a
     * datasource.
     *
     * @param datasource  Datasource object or null
     */
    public void setDatasource(Datasource datasource) {
        this.datasource = datasource;
    }

    public XPathContentHandler getXPathContentHandler() {
        return xpathContentHandler;
    }

    public void setXPathContentHandler(XPathContentHandler xpathContentHandler) {
        this.xpathContentHandler = xpathContentHandler;
    }

    public ContentHandler getOutput() {
        return output;
    }

    public void setOutput(ContentHandler output) {
        this.output = output;
    }

    public boolean isEmptyResultSet() {
        ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.emptyResultSet;
    }

    public void setEmptyResultSet(boolean emptyResultSet) {
        ExecutionContext executionContext = getExecutionContext(0);
        executionContext.emptyResultSet = emptyResultSet;
    }

    public int getUpdateCount() {
        ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.updateCount;
    }

    public void setUpdateCount(int updateCount) {
        ExecutionContext executionContext = getExecutionContext(0);
        executionContext.updateCount = updateCount;
    }

    public int getRowPosition() {
        ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.rowPosition;
    }

    public void setRowPosition(int rowPosition) {
        ExecutionContext executionContext = getExecutionContext(0);
        executionContext.rowPosition = rowPosition;
    }

    public NamespaceSupport getNamespaceSupport() {
        return namespaceSupport;
    }

    public Map getPrefixesMap() {
        Map prefixesMap = new HashMap();
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            String prefix = (String) e.nextElement();
            prefixesMap.put(prefix, namespaceSupport.getURI(prefix));
        }
        return prefixesMap;
    }

    public void setNamespaceSupport(NamespaceSupport namespaceSupport) {
        this.namespaceSupport = namespaceSupport;
    }

//        public void processNamespaces(String uri, String localname, String qName, Attributes attributes) {
//            for (int i = 0; i < attributes.getLength(); i++) {
//                String name = attributes.getQName(i);
//                if (name.startsWith("xmlns:")) {
//                    maybeDeclarePrefix(name.substring(6), attributes.getValue(i));
//                }
//            }
//            int index = qName.indexOf(':');
//            if (index != -1) {
//                maybeDeclarePrefix(qName.substring(0, index), uri);
//            }
//        }

//        private void maybeDeclarePrefix(String prefix, String uri) {
//            String supportURI = namespaceSupport.getURI(prefix);
//            if (!uri.equals(supportURI)) {
//                namespaceSupport.declarePrefix(prefix, uri);
//            }
//        }

    public void declarePrefix(String prefix, String uri) {
        namespaceSupport.declarePrefix(prefix, uri);
    }

    private Context getContext(PipelineContext pipelineContext) {
        Context context = (Context) pipelineContext.getAttribute(PipelineContext.SQL_PROCESSOR_CONTEXT);
        if (context == null) {
            context = new Context();
            pipelineContext.setAttribute(PipelineContext.SQL_PROCESSOR_CONTEXT, context);
        }
        return context;
    }

    private static class Context {
        // Map datasource names to delegates
        public Map delegates = new HashMap();
    }
}
