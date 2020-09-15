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
package org.orbeon.oxf.processor.sql;

import org.orbeon.dom.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DatabaseContext;
import org.orbeon.oxf.processor.Datasource;
import org.orbeon.oxf.processor.sql.delegates.SQLProcessorOracleJDBC4Delegate;
import org.orbeon.oxf.processor.sql.delegates.SQLProcessorStandardDelegate;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xml.DeferredXMLReceiver;
import org.orbeon.oxf.xml.XPathXMLReceiver;
import org.orbeon.oxf.xml.dom.XmlLocationData;
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
public class SQLProcessorInterpreterContext extends DatabaseContext {

    private PropertySet propertySet;

    // Locator for datasource declaration, if any
    private Locator documentLocator;

    // Either one of those two must be set
    private String jndiName;
    private Datasource datasource;

    private PipelineContext pipelineContext;
    private Node input;
    private XPathXMLReceiver xpathReceiver;
    private DeferredXMLReceiver output;
    private NamespaceSupport namespaceSupport;

    private List executionContextStack;
    private List currentNodes;
    private List<SQLFunctionLibrary.SQLFunctionContext> functionContextStack = new ArrayList<SQLFunctionLibrary.SQLFunctionContext>();
    public static final String SQL_PROCESSOR_CONTEXT = "sql-processor-context"; // used by SQLProcessor and related

    public SQLProcessorInterpreterContext(PropertySet propertySet) {
        this.propertySet = propertySet;
    }

    public PropertySet getPropertySet() {
        return propertySet;
    }

    private static class ExecutionContext implements Cloneable {
        public ResultSet resultSet;
        public PreparedStatement preparedStatement;
        public String statementString;
        public String statementSHA;
        public boolean emptyResultSet;
        public boolean gotResults;
        public int updateCount;

        public int columnIndex;
        public String columnName;
        public String columnType;
        public String columnValue;

        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    public void pushContext() {
        if (executionContextStack == null)
            executionContextStack = new ArrayList();

        try {
            if (executionContextStack.size() == 0) {
                // Start with empty context
                executionContextStack.add(new ExecutionContext());
            } else {
                // Clone context data
                executionContextStack.add(getExecutionContext(0).clone());
            }
        } catch (CloneNotSupportedException e) {
            // Should not happen
            throw new OXFException(e);
        }
    }

    private ExecutionContext getExecutionContext(int level) {
        return (ExecutionContext) executionContextStack.get(executionContextStack.size() - 1 - level);
    }

    public void popContext() {
        executionContextStack.remove(executionContextStack.size() - 1);
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
                    // Try Tomcat delegate
                    try {
                        getClass().getClassLoader().loadClass("org.apache.tomcat.dbcp.dbcp.DelegatingPreparedStatement");
                        clazz = getClass().getClassLoader().loadClass("org.orbeon.oxf.processor.sql.delegates.SQLProcessorOracleTomcatDelegate");
                        SQLProcessor.logger.info("Using Oracle Tomcat delegate.");
                    } catch (Throwable t) {
                        // Ignore
                    }
                    // Try JBoss 7 delegate
                    // - since JBoss 7 supports JDBC 4, we shouldn't need this, and we should just be able to use SQLProcessorOracleJDBC4Delegate
                    if (clazz == null) {
                        try {
                            getClass().getClassLoader().loadClass("org.jboss.jca.adapters.jdbc.WrappedPreparedStatement");
                            clazz = getClass().getClassLoader().loadClass("org.orbeon.oxf.processor.sql.delegates.SQLProcessorOracleJBoss7Delegate");
                            SQLProcessor.logger.info("Using Oracle JBoss 7 delegate.");
                        } catch (Throwable t) {
                            // Ignore
                        }
                    }
                    // Try JBoss 6 delegate
                    if (clazz == null) {
                        try {
                            getClass().getClassLoader().loadClass("org.jboss.resource.adapter.jdbc.WrappedPreparedStatement");
                            clazz = getClass().getClassLoader().loadClass("org.orbeon.oxf.processor.sql.delegates.SQLProcessorOracleJBoss6Delegate");
                            SQLProcessor.logger.info("Using Oracle JBoss 6 delegate.");
                        } catch (Throwable t) {
                            // Ignore
                        }
                    }
                    // Then use the JDBC4 delegate
                    if (clazz == null) {
                        clazz = SQLProcessorOracleJDBC4Delegate.class;
                        SQLProcessor.logger.info("Using Oracle JDBC4 delegate.");
                    }
				} else {
                    // For JDBC drivers that properly implement the API
                    clazz = SQLProcessorStandardDelegate.class;
                }
                databaseDelegate = (DatabaseDelegate) clazz.newInstance();
                getContext(pipelineContext).delegates.put(delegateKey, databaseDelegate);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
        return databaseDelegate;
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

    public void pushFunctionContext(SQLFunctionLibrary.SQLFunctionContext ctx) {
        functionContextStack.add(ctx);
    }

    public SQLFunctionLibrary.SQLFunctionContext popFunctionContext() {
        return functionContextStack.remove(functionContextStack.size() - 1);
    }

    public SQLFunctionLibrary.SQLFunctionContext getFunctionContextOrNull() {
        return functionContextStack.isEmpty() ? null : functionContextStack.get(functionContextStack.size() - 1);
    }

    public void setResultSet(ResultSet resultSet) {
        final ExecutionContext executionContext = getExecutionContext(0);
        executionContext.resultSet = resultSet;
    }

    public void setStatement(PreparedStatement stmt) {
        final ExecutionContext executionContext = getExecutionContext(0);
        executionContext.preparedStatement = stmt;
    }

    public void setStatementString(String statementString) {
        final ExecutionContext executionContext = getExecutionContext(0);
        executionContext.statementString = statementString;
    }

    public void setColumnContext(int index, String name, String type, String value) {
        final ExecutionContext executionContext = getExecutionContext(0);
        executionContext.columnIndex = index;
        executionContext.columnName = name;
        executionContext.columnType = type;
        executionContext.columnValue = value;
    }

    public int getColumnIndex() {
        final ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.columnIndex;
    }

    public String getColumnName() {
        final ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.columnName;
    }

    public String getColumnType() {
        final ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.columnType;
    }

    public String getColumnValue() {
        final ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.columnValue;
    }

    public ResultSet getResultSet() {
        return getResultSet(0);
    }

    public ResultSet getResultSet(int level) {
        final ExecutionContext executionContext = getExecutionContext(level);
        return executionContext.resultSet;
    }

    public PreparedStatement getStatement(int level) {
        final ExecutionContext executionContext = getExecutionContext(level);
        return executionContext.preparedStatement;
    }

    public String getStatementString(int level) {
        final ExecutionContext executionContext = getExecutionContext(level);
        return executionContext.statementString;
    }

    public String getStatementString() {
        return getStatementString(0);
    }

    public String getStatementSHA(int level) {
        final ExecutionContext executionContext = getExecutionContext(level);
        if (executionContext.statementSHA == null) {
            final String fullSHA = SecureUtils.digestString(executionContext.statementString, "SHA1", "hex");
            executionContext.statementSHA = fullSHA.substring(0, 7);
        }
        return executionContext.statementSHA;
    }

    public String getStatementSHA() {
        return getStatementSHA(0);
    }

    public Connection getConnection() {
        if (jndiName != null) {
            // Connection was configured with as a JDBC datasource
            try {
                return getConnection(pipelineContext, jndiName);
            } catch (RuntimeException e) {
                if (documentLocator != null)
                    throw new ValidationException(e, XmlLocationData.apply(documentLocator));
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

    public XPathXMLReceiver getXPathContentHandler() {
        return xpathReceiver;
    }

    public void setXPathContentHandler(XPathXMLReceiver xpathReceiver) {
        this.xpathReceiver = xpathReceiver;
    }

    public DeferredXMLReceiver getOutput() {
        return output;
    }

    public void setOutput(DeferredXMLReceiver output) {
        this.output = output;
    }

    public boolean isEmptyResultSet() {
        final ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.emptyResultSet;
    }

    public void setEmptyResultSet(boolean emptyResultSet) {
        final ExecutionContext executionContext = getExecutionContext(0);
        executionContext.emptyResultSet = emptyResultSet;
    }

    public boolean isGotResults() {
        final ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.gotResults;
    }

    public void setGotResults(boolean gotResults) {
        final ExecutionContext executionContext = getExecutionContext(0);
        executionContext.gotResults = gotResults;
    }

    public int getUpdateCount() {
        final ExecutionContext executionContext = getExecutionContext(0);
        return executionContext.updateCount;
    }

    public void setUpdateCount(int updateCount) {
        final ExecutionContext executionContext = getExecutionContext(0);
        executionContext.updateCount = updateCount;
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
        Context context = (Context) pipelineContext.getAttribute(SQL_PROCESSOR_CONTEXT);
        if (context == null) {
            context = new Context();
            pipelineContext.setAttribute(SQL_PROCESSOR_CONTEXT, context);
        }
        return context;
    }

    private static class Context {
        // Map datasource names to delegates
        public Map delegates = new HashMap();
    }
}
