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
package org.orbeon.oxf.processor.xmldb;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.NamespaceCleanupContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This is the main XML:DB processor. It should be able to access all databases supporting the
 * XML:DB API. It implements convenience methods for derived processors.
 *
 * See http://www.xmldb.org/xapi/xapi-draft.html for more information about XML:DB.
 *
 * See also the eXist Javadocs at http://exist.sourceforge.net/api/index.html.
 */
public abstract class XMLDBProcessor extends ProcessorImpl {

    static Logger logger = LoggerFactory.createLogger(XMLDBProcessor.class);

    public static final String INPUT_DATASOURCE = "datasource";
    public static final String INPUT_QUERY = "query";

    public static final String XMLDB_DATASOURCE_URI = "http://www.orbeon.org/oxf/xmldb-datasource";
    public static final String XMLDB_QUERY_URI = "http://www.orbeon.org/oxf/xmldb-query";

    public static final String DRIVER_CLASS_NAME = "driver-class-name";
    public static final String URI_PROPERTY = "uri";
    public static final String USERNAME_PROPERTY = "username";
    public static final String PASSWORD_PROPERTY = "password";

    protected static final String ROOT_COLLECTION_PATH = "/db";
    protected static final String XMLDB_URI_PREFIX = "xmldb:";
    protected static final String XUPDATE_SERVICE_NAME = "XUpdateQueryService";
    protected static final String XPATH_SERVICE_NAME = "XPathQueryService";
    protected static final String COLLECTION_SERVICE_NAME = "CollectionManagementService";

    private static Map drivers = new HashMap();

    private Datasource readDatasource(Document datasourceDocument) {
        Datasource datasource = new Datasource();

        // Try local configuration first
        String driverClassName = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + DRIVER_CLASS_NAME);
        String url = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + URI_PROPERTY);
        String username = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + USERNAME_PROPERTY);
        String password = XPathUtils.selectStringValueNormalize(datasourceDocument, "/*/" + PASSWORD_PROPERTY);

        // Override with properties if needed
        datasource.setDriverClassName(driverClassName != null ? driverClassName : getPropertySet().getString(DRIVER_CLASS_NAME));
        datasource.setUri(url != null ? url : getPropertySet().getString(URI_PROPERTY));
        datasource.setUsername(username != null ? username : getPropertySet().getString(USERNAME_PROPERTY));
        datasource.setPassword(password != null ? password : getPropertySet().getString(PASSWORD_PROPERTY));

        return datasource;
    }

    private Config readConfig(Document configDocument) {
        Config config = new Config();

        Element rootElement = configDocument.getRootElement();
        config.setOperation(rootElement.getName());
        config.setCollection(rootElement.attributeValue("collection"));
        config.setCreateCollection(rootElement.attributeValue("create-collection"));
        config.setResourceId(rootElement.attributeValue("resource-id"));

        config.setQuery(XMLUtils.objectToString(XPathUtils.selectObjectValue(configDocument, "/*/node()")));
        config.setNamespaceContext(XMLUtils.getNamespaceContext(configDocument.getRootElement()));

        return config;
    }

    protected Datasource getDatasource(PipelineContext pipelineContext) {
         return (Datasource) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_DATASOURCE), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                return readDatasource(readInputAsDOM4J(context, INPUT_DATASOURCE));
            }
        });
    }

    protected Config getConfig(PipelineContext pipelineContext) {
//        readInputAsSAX(pipelineContext, INPUT_CONFIG, new SAXDebuggerProcessor.DebugContentHandler(new ForwardingContentHandler(null, false)));
         return (Config) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_QUERY), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                // Use readInputAsSAX so that we can filter namespaces if needed
                LocationSAXContentHandler ch = new LocationSAXContentHandler();
                readInputAsSAX(context, input, new NamespaceCleanupContentHandler(ch, isSerializeXML11()));
                return readConfig(ch.getDocument());
            }
        });
    }

    private synchronized static void ensureDriverRegistered(PipelineContext pipelineContext, Datasource datasource) {
        String driverClassName = datasource.getDriverClassName();
        if (drivers.get(driverClassName) == null) {
            // Initialize database driver
            try {
                Class cl = Class.forName(driverClassName);
                Database database = (Database) cl.newInstance();
                DatabaseManager.registerDatabase(database);
                {
                    // This is specific for eXist
                    ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                    String configurationFile = externalContext.getRealPath("WEB-INF/exist-conf.xml");
                    database.setProperty("create-database", "true");
//                    database.setProperty("database-id", "oxf");
                    database.setProperty("configuration", configurationFile);
                }
                drivers.put(driverClassName, database);
            } catch (Exception e) {
                throw new OXFException("Cannot register XML:DB driver for class name: " + datasource.getDriverClassName(), e);
            }
        }
    }

    /*
     * Examples of datasourceURI / collection / XML:DB collection name combinations:
     *
     * 1. xmldb:exist:///
     *    /db/orbeon/bizdoc-example
     *    xmldb:exist:///db/orbeon/bizdoc-example
     * 2. xmldb:exist://localhost:9999/exist/xmlrpc
     *    /db/orbeon/bizdoc-example
     *    xmldb:exist://localhost:9999/exist/xmlrpc/db/orbeon/bizdoc-example
     */
    private Collection getCollection(PipelineContext pipelineContext, Datasource datasource, String collection) {
        ensureDriverRegistered(pipelineContext, datasource);
        try {
            String datasourceURI = datasource.getUri();
            if (!datasourceURI.startsWith(XMLDB_URI_PREFIX))
                throw new OXFException("Invalid XML:DB URI: " + datasourceURI);
            if (!collection.startsWith("/"))
                throw new OXFException("Collection name must start with a '/': " + collection);

            // This makes sure that we have a correct URI syntax
            URI uri = new URI(datasourceURI.substring(XMLDB_URI_PREFIX.length()));

            // Rebuild a URI string
            String xmldbCollectionName = XMLDB_URI_PREFIX + uri.getScheme() + "://"
                    + (uri.getAuthority() == null ? "" : uri.getAuthority())
                    + (uri.getPath() == null ? "" : uri.getPath());
            if (xmldbCollectionName.endsWith("/"))
                xmldbCollectionName = xmldbCollectionName.substring(0, xmldbCollectionName.length() - 1);
            xmldbCollectionName = xmldbCollectionName + collection;

            return DatabaseManager.getCollection(xmldbCollectionName, datasource.getUsername(), datasource.getPassword());
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Query resources from the database.
     *
     * @param datasource         the processor configuration
     * @param collectionName identifies the collection in which resources are searched
     * @param resourceId     optional resource id on which the query is run
     * @param query          selects resources in the collection that must be searched
     * @param contentHandler ContentHandler where the resources are output
     */
    protected void query(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query, Map namespaceContext, ContentHandler contentHandler) {
        ensureDriverRegistered(pipelineContext, datasource);
        try {
            // Execute query
            ResourceSet result = executeQuery(pipelineContext, datasource, collectionName, createCollection, resourceId, query, namespaceContext);

            // Output resources
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources();) {
                Resource resource = i.nextResource();
                if (resource instanceof XMLResource) {
                    ((XMLResource) resource).getContentAsSAX(new DatabaseReadContentHandler(contentHandler));
                } else if (resource instanceof BinaryResource) {
                    XMLUtils.inputStreamToBase64Characters(new ByteArrayInputStream((byte[]) resource.getContent()), contentHandler);
                } else {
                    throw new OXFException("Unsupported resource type: " + resource.getClass());
                }
            }
        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

//    private static class EmptyResourceSet implements ResourceSet {
//        public void addResource(Resource resource) throws XMLDBException {
//            throw new UnsupportedOperationException();
//        }
//
//        public void clear() throws XMLDBException {
//            throw new UnsupportedOperationException();
//        }
//
//        public ResourceIterator getIterator() throws XMLDBException {
//            return new ResourceIterator() {
//                public boolean hasMoreResources() {
//                    return false;
//                }
//
//                public Resource nextResource() {
//                    return null;
//                }
//            };
//        }
//
//        public Resource getMembersAsResource() throws XMLDBException {
//            return null;
//        }
//
//        public Resource getResource(long l) throws XMLDBException {
//            return null;
//        }
//
//        public long getSize() throws XMLDBException {
//            return 0;
//        }
//
//        public void removeResource(long l) throws XMLDBException {
//            throw new UnsupportedOperationException();
//        }
//    }

    private ResourceSet executeQuery(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query, Map namespaceContext) throws XMLDBException {
        Collection collection = getCollection(pipelineContext, datasource, collectionName);
        if (collection == null) {
            if (!createCollection)
                throw new OXFException("Cannot find collection '" + collectionName + "'.");
            else
                collection = createCollection(pipelineContext, datasource, collectionName);
        }
        XPathQueryService xpathQueryService;
        try {
            // For eXist, this is the same as XQueryService
            xpathQueryService = (XPathQueryService) collection.getService(XPATH_SERVICE_NAME, "1.0");
        } catch (XMLDBException e) {
            if (e.errorCode == ErrorCodes.NO_SUCH_SERVICE)
                throw new OXFException("XML:DB " + XPATH_SERVICE_NAME + " does not exist.", e);
            else
                throw e;
        }
        if (xpathQueryService == null)
            throw new OXFException("XML:DB " + XPATH_SERVICE_NAME + " does not exist.");

        // Configure service (this is particular for eXist)
        // TODO: Should be configurable, but with what mechanism? 
        try {
            xpathQueryService.setProperty("highlight-matches", "no");
        } catch (Exception e) {
            logger.debug("Unable to set eXist highlight-matches", e);
        }

        // Set namespaces
        for (Iterator i = namespaceContext.keySet().iterator(); i.hasNext();) {
            String prefix = (String) i.next();
            String uri = (String) namespaceContext.get(prefix);
            xpathQueryService.setNamespace(prefix, uri);
        }

        // Log for debug
        logger.debug(query);

        // Execute query
        ResourceSet result;
        if (resourceId == null)
            result = xpathQueryService.query(query);
        else
            result = xpathQueryService.queryResource(resourceId, query);

        return result;
    }

    /**
     * Insert a resource in a collection.
     *
     * @param pipelineContext pipeline context
     * @param datasource      the processor configuration
     * @param collectionName  identifies the collection in which to insert the resource
     * @param input           processor input containing the XML resource to insert
     */
    protected void insert(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, ProcessorInput input) {
        ensureDriverRegistered(pipelineContext, datasource);
        try {
            Collection collection = getCollection(pipelineContext, datasource, collectionName);
            if (collection == null) {
                if (!createCollection)
                    throw new OXFException("Cannot find collection '" + collectionName + "'.");
                else
                    collection = createCollection(pipelineContext, datasource, collectionName);
            }
            // Create new XMLResource
            XMLResource xmlResource = (XMLResource) collection.createResource(resourceId, "XMLResource");

            // Write to the resource
            ContentHandler contentHandler = xmlResource.setContentAsSAX();
            readInputAsSAX(pipelineContext, input, new NamespaceCleanupContentHandler(contentHandler, isSerializeXML11()));

            // Store resource
            collection.storeResource(xmlResource);
        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

    private boolean isSerializeXML11() {
        return getPropertySet().getBoolean("serialize-xml-11", false).booleanValue();
    }

    private Collection createCollection(PipelineContext pipelineContext, Datasource datasource, String collectionName) throws XMLDBException {
        Collection rootCollection = getCollection(pipelineContext, datasource, ROOT_COLLECTION_PATH);
        if (rootCollection == null)
            throw new OXFException("Cannot find root collection '" + ROOT_COLLECTION_PATH + "'.");

        CollectionManagementService mgtService = (CollectionManagementService)
                rootCollection.getService(COLLECTION_SERVICE_NAME, "1.0");

        if (!collectionName.startsWith(ROOT_COLLECTION_PATH + "/"))
            throw new OXFException("Collection name must start with '" + ROOT_COLLECTION_PATH + "': " + collectionName);

        return mgtService.createCollection(collectionName.substring(ROOT_COLLECTION_PATH.length() + 1));
    }

    /**
     * Update resources in the database.
     *
     * @param datasource         the processor configuration
     * @param collectionName identifies the collection in which resources are updated
     * @param resourceId     optional resource id on which the query is run
     * @param query          the XUpdate query to run
     */
    protected void update(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query) {
        ensureDriverRegistered(pipelineContext, datasource);
        try {
            Collection collection = getCollection(pipelineContext, datasource, collectionName);
            if (collection == null) {
                if (!createCollection)
                    throw new OXFException("Cannot find collection '" + collectionName + "'.");
                else
                    collection = createCollection(pipelineContext, datasource, collectionName);
            }
            XUpdateQueryService xUpdateQueryService;
            try {
                xUpdateQueryService = (XUpdateQueryService) collection.getService(XUPDATE_SERVICE_NAME, "1.0");
            } catch (XMLDBException e) {
                if (e.errorCode == ErrorCodes.NO_SUCH_SERVICE)
                    throw new OXFException("XML:DB " + XUPDATE_SERVICE_NAME + " does not exist.", e);
                else
                    throw e;
            }
            if (xUpdateQueryService == null)
                throw new OXFException("XML:DB " + XUPDATE_SERVICE_NAME + " does not exist.");

            // Udpdate either all the resources in a collection, or a specific resource
            if (resourceId == null)
                xUpdateQueryService.update(query);
            else
                xUpdateQueryService.updateResource(resourceId, query);
        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Delete resources from the database.
     *
     * @param datasource         the processor configuration
     * @param collectionName identifies the collection in which resources are searched
     * @param resourceId     optional resource id on which the query is run
     * @param query          selects resources in the collection that must be deleted
     */
    protected void delete(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection, String resourceId, String query, Map namespaceContext) {
        ensureDriverRegistered(pipelineContext, datasource);
        try {
            // Execute query
            ResourceSet result = executeQuery(pipelineContext, datasource, collectionName, createCollection, resourceId, query, namespaceContext);

            // Delete resources based on query result
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources();) {
                Resource resource = i.nextResource();
                resource.getParentCollection().removeResource(resource);
            }
        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

     protected void executeOperation(PipelineContext pipelineContext, ContentHandler contentHandler) {
        // Get datasource and configuration
        Datasource datasource = getDatasource(pipelineContext);
        Config config = getConfig(pipelineContext);

        if ("query".equals(config.getOperation())) {
            query(pipelineContext, datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), config.getQuery(), config.getNamespaceContext(), contentHandler);
        } else if ("insert".equals(config.getOperation())) {
            insert(pipelineContext, datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), getInputByName(INPUT_DATA));
        } else if ("delete".equals(config.getOperation())) {
            delete(pipelineContext, datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), config.getQuery(), config.getNamespaceContext());
        } else if ("update".equals(config.getOperation())) {
            update(pipelineContext, datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), config.getQuery());
        } else {
            // TODO: Handle location info
            throw new IllegalArgumentException("Invalid operation: " + config.getOperation());
        }
    }

    protected static class Datasource {
        private String driverClassName;
        private String uri;
        private String username;
        private String password;

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    protected static class Config {
        private String operation;
        private String collection;
        private String createCollection;
        private String resourceId;
        private String query;
        private Map namespaceContext;

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getCreateCollection() {
            return createCollection;
        }

        public void setCreateCollection(String createCollection) {
            this.createCollection = createCollection;
        }

        public String getResourceId() {
            return resourceId;
        }

        public void setResourceId(String resourceId) {
            this.resourceId = resourceId;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public Map getNamespaceContext() {
            return namespaceContext;
        }

        public void setNamespaceContext(Map namespaceContext) {
            this.namespaceContext = namespaceContext;
        }
    }

    /**
     * Clean-up the SAX output. Some databases, such as eXist, output incorrect SAX that causes
     * issues down the line.
     */
    public static class DatabaseReadContentHandler extends ForwardingContentHandler {

        private int startDocumentLevel = 0;

        public DatabaseReadContentHandler(ContentHandler contentHandler) {
            super(contentHandler);
        }

        public void startDocument() throws SAXException {
            if (startDocumentLevel++ == 0)
                super.startDocument();
        }

        public void endDocument() throws SAXException {
            if (--startDocumentLevel == 0)
                super.endDocument();
        }
    }
}
