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
package org.orbeon.oxf.processor.xmldb;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.CacheableInputReader;
import org.orbeon.oxf.processor.Datasource;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the main XML:DB processor. It should be able to access all databases supporting the XML:DB API. It
 * implements convenience methods for derived processors.
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

    protected static final String ROOT_COLLECTION_PATH = "/db";
    protected static final String XMLDB_URI_PREFIX = "xmldb:";
    protected static final String XUPDATE_SERVICE_NAME = "XUpdateQueryService";
    protected static final String XPATH_SERVICE_NAME = "XPathQueryService";
    protected static final String COLLECTION_SERVICE_NAME = "CollectionManagementService";

    private static Map<String, Database> drivers = new HashMap<String, Database>();

    private Config readConfig(Document configDocument) {
        Config config = new Config();

        Element rootElement = configDocument.getRootElement();
        config.setOperation(rootElement.getName());
        config.setCollection(rootElement.attributeValue("collection"));
        config.setCreateCollection(rootElement.attributeValue("create-collection"));
        config.setResourceId(rootElement.attributeValue("resource-id"));

        config.setQuery(Dom4jUtils.objectToString(XPathUtils.selectObjectValue(configDocument, "/*/text() | /*/*")));

        final Map<String, String> namespaceContext = Dom4jUtils.getNamespaceContext(configDocument.getRootElement());
        // Not sure why 1) xml needs to be in there and 2) why eXist balks on it, but we remove it here for eXist
        namespaceContext.remove(XMLConstants.XML_PREFIX);
        config.setNamespaceContext(namespaceContext);

        return config;
    }

    protected Datasource getDatasource(PipelineContext pipelineContext) {
        return Datasource.getDatasource(pipelineContext, this, getInputByName(INPUT_DATASOURCE));
    }

    protected Config getConfig(PipelineContext pipelineContext) {
         return (Config) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_QUERY), new CacheableInputReader() {
            public Object read(PipelineContext context, ProcessorInput input) {
                // Use readInputAsSAX so that we can filter namespaces if needed
                LocationSAXContentHandler ch = new LocationSAXContentHandler();
                readInputAsSAX(context, input, new NamespaceCleanupXMLReceiver(ch, isSerializeXML11()));
                return readConfig(ch.getDocument());
            }
        });
    }

    protected synchronized static void ensureDriverRegistered(Datasource datasource) {
        String driverClassName = datasource.getDriverClassName();
        if (drivers.get(driverClassName) == null) {
            // Initialize database driver
            try {
                Class cl = Class.forName(driverClassName);
                Database database = (Database) cl.newInstance();
                DatabaseManager.registerDatabase(database);
                {
                    // This is specific for eXist
                    // TODO: move this to properties?
                    ExternalContext externalContext = NetUtils.getExternalContext();
                    String configurationFile = externalContext.getRealPath("WEB-INF/exist-conf.xml");
                    database.setProperty("create-database", "true");
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
    protected Collection getCollection(Datasource datasource, String collection) {
        ensureDriverRegistered(datasource);
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
     * @param datasource        the processor configuration
     * @param collectionName    identifies the collection in which resources are searched
     * @param createCollection  if true, create collection if it doesn't exist
     * @param resourceId        optional resource id on which the query is run
     * @param query             selects resources in the collection that must be searched
     * @param namespaceContext  namespace mappings
     * @param xmlReceiver       receiver where the resources are output
     */
    protected void query(Datasource datasource, String collectionName, boolean createCollection,
                         String resourceId, String query, Map<String, String> namespaceContext, XMLReceiver xmlReceiver) {
        ensureDriverRegistered(datasource);
        try {
            // Execute query
            ResourceSet result = executeQuery(datasource, collectionName, createCollection, resourceId, query, namespaceContext);

            // Output resources
            for (ResourceIterator i = result.getIterator(); i.hasMoreResources();) {
                Resource resource = i.nextResource();
                if (resource instanceof XMLResource) {
                    ((XMLResource) resource).getContentAsSAX(new DatabaseReadXMLReceiver(xmlReceiver));
                } else if (resource instanceof BinaryResource) {
                    XMLUtils.inputStreamToBase64Characters(new ByteArrayInputStream((byte[]) resource.getContent()), xmlReceiver);
                } else {
                    throw new OXFException("Unsupported resource type: " + resource.getClass());
                }
            }
        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

    protected void storeResource(Datasource datasource, String collectionName, boolean createCollection,
                                 String resourceName, String document) {
        ensureDriverRegistered(datasource);
        try {
            Collection collection = getCollection(datasource, collectionName);
            if (collection == null) {
                if (!createCollection)
                    throw new OXFException("Cannot find collection '" + collectionName + "'.");
                else
                    collection = createCollection(datasource, collectionName);
            }
            final Resource resource = collection.createResource(resourceName, XMLResource.RESOURCE_TYPE);
            resource.setContent(document);
            collection.storeResource(resource);

        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

    private ResourceSet executeQuery(Datasource datasource, String collectionName, boolean createCollection,
                                     String resourceId, String query, Map<String, String> namespaceContext) throws XMLDBException {

        Collection collection = getCollection(datasource, collectionName);
        if (collection == null) {
            if (!createCollection)
                throw new OXFException("Cannot find collection '" + collectionName + "'.");
            else
                collection = createCollection(datasource, collectionName);
        }
        final XPathQueryService xpathQueryService;
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
        if (namespaceContext != null) {
            for (final String prefix: namespaceContext.keySet()) {
                xpathQueryService.setNamespace(prefix, namespaceContext.get(prefix));
            }
        }

        // Log for debug
        logger.debug(query);

        // Execute query
        final ResourceSet result;
        if (resourceId == null)
            result = xpathQueryService.query(query);
        else
            result = xpathQueryService.queryResource(resourceId, query);

        return result;
    }

    /**
     * Insert a resource in a collection.
     *
     * @param pipelineContext   current context
     * @param datasource        the processor configuration
     * @param collectionName    identifies the collection in which to insert the resource
     * @param createCollection  if true, create collection if it doesn't exist
     * @param resourceId        id of the new resource
     * @param input             processor input containing the XML resource to insert
     */
    protected void insert(PipelineContext pipelineContext, Datasource datasource, String collectionName, boolean createCollection,
                          String resourceId, ProcessorInput input) {
        ensureDriverRegistered(datasource);
        try {
            Collection collection = getCollection(datasource, collectionName);
            if (collection == null) {
                if (!createCollection)
                    throw new OXFException("Cannot find collection '" + collectionName + "'.");
                else
                    collection = createCollection(datasource, collectionName);
            }
            // Create new XMLResource
            XMLResource xmlResource = (XMLResource) collection.createResource(resourceId, "XMLResource");

            // Write to the resource
            // NOTE: Writing comments is not supported yet
            ContentHandler contentHandler = xmlResource.setContentAsSAX();
            readInputAsSAX(pipelineContext, input, new NamespaceCleanupXMLReceiver(contentHandler, isSerializeXML11()));

            // Store resource
            collection.storeResource(xmlResource);
        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

    private boolean isSerializeXML11() {
        return getPropertySet().getBoolean("serialize-xml-11", false);
    }

    protected Collection createCollection(Datasource datasource, String collectionName) throws XMLDBException {
        Collection rootCollection = getCollection(datasource, ROOT_COLLECTION_PATH);
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
     * @param datasource        the processor configuration
     * @param collectionName    identifies the collection in which resources are updated
     * @param createCollection  if true, create collection if it doesn't exist
     * @param resourceId        optional resource id on which the query is run
     * @param query             the XUpdate query to run
     */
    protected void update(Datasource datasource, String collectionName, boolean createCollection,
                          String resourceId, String query) {
        ensureDriverRegistered(datasource);
        try {
            Collection collection = getCollection(datasource, collectionName);
            if (collection == null) {
                if (!createCollection)
                    throw new OXFException("Cannot find collection '" + collectionName + "'.");
                else
                    collection = createCollection(datasource, collectionName);
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

            // Update either all the resources in a collection, or a specific resource
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
     * @param datasource        the processor configuration
     * @param collectionName    identifies the collection in which resources are searched
     * @param createCollection  if true, create collection if it doesn't exist
     * @param resourceId        optional resource id on which the query is run
     * @param query             selects resources in the collection that must be deleted
     * @param namespaceContext  namespace mappings
     */
    protected void delete(Datasource datasource, String collectionName, boolean createCollection,
                          String resourceId, String query, Map<String, String> namespaceContext) {
        ensureDriverRegistered(datasource);
        try {
            // Execute query
            final ResourceSet result = executeQuery(datasource, collectionName, createCollection, resourceId, query, namespaceContext);

            if (result.getSize() > 0) {
                // Delete resources

                // NOTE: As of 2009-10-27, with eXist 1.2.5, the following doesn't work:
                //
                // resource.getParentCollection().removeResource(resource)
                //
                // So we implement a workaround: we go up to the resource from the root collection.

                final Collection rootCollection = getCollection(datasource, ROOT_COLLECTION_PATH);
                for (final ResourceIterator i = result.getIterator(); i.hasMoreResources();) {
                    final Resource resource = i.nextResource();

                    Collection parentCollection;
                    {
                        parentCollection = rootCollection;
                        final String[] subCollections = StringUtils.split(resource.getParentCollection().getName().substring(ROOT_COLLECTION_PATH.length()), '/');
                        for (final String subCollection: subCollections) {
                            parentCollection = parentCollection.getChildCollection(subCollection);
                        }
                    }

                    parentCollection.removeResource(resource);
                }
            }
        } catch (XMLDBException e) {
            throw new OXFException(e);
        }
    }

     protected void executeOperation(PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
        // Get datasource and configuration
        final Datasource datasource = getDatasource(pipelineContext);
        final Config config = getConfig(pipelineContext);

        if ("query".equals(config.getOperation())) {
            query(datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), config.getQuery(), config.getNamespaceContext(), xmlReceiver);
        } else if ("insert".equals(config.getOperation())) {
            insert(pipelineContext, datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), getInputByName(INPUT_DATA));
        } else if ("delete".equals(config.getOperation())) {
            delete(datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), config.getQuery(), config.getNamespaceContext());
        } else if ("update".equals(config.getOperation())) {
            update(datasource, config.getCollection(), "true".equals(config.getCreateCollection()), config.getResourceId(), config.getQuery());
        } else {
            // TODO: Handle location info
            throw new IllegalArgumentException("Invalid operation: " + config.getOperation());
        }
    }

    protected static class Config {
        private String operation;
        private String collection;
        private String createCollection;
        private String resourceId;
        private String query;
        private Map<String, String> namespaceContext;

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

        public Map<String, String> getNamespaceContext() {
            return namespaceContext;
        }

        public void setNamespaceContext(Map<String, String> namespaceContext) {
            this.namespaceContext = namespaceContext;
        }
    }

    /**
     * Clean-up the SAX output. Some databases, such as eXist, output incorrect SAX that causes issues down the line.
     */
    public static class DatabaseReadXMLReceiver extends ForwardingXMLReceiver {

        private int startDocumentLevel = 0;

        public DatabaseReadXMLReceiver(XMLReceiver xmlReceiver) {
            super(xmlReceiver);
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
