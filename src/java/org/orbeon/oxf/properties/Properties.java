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
package org.orbeon.oxf.properties;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.DOMSerializer;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;

import java.util.Set;

/**
 * This class provides access to global, configurable properties, as well as to processor-specific properties. This is
 * an example of properties file:
 *
 * <properties xmlns:xs="http://www.w3.org/2001/XMLSchema"
 *             xmlns:oxf="http://www.orbeon.com/oxf/processors">
 *
 *   <property as="xs:integer" name="oxf.cache.size" value="200"/>
 *   <property as="xs:string"  processor-name="oxf:page-flow" name="instance-passing" value="redirect"/>
 *
 * </properties>
 */
public class Properties {

    private static final Logger logger = LoggerFactory.createLogger(Properties.class);

    public static final String DEFAULT_PROPERTIES_URI = "oxf:/properties.xml";
    public static final String PROPERTIES_SCHEMA_URI = "http://www.orbeon.com/oxf/properties";

    private static final int RELOAD_DELAY = 5 * 1000;

    /**
     * The global Properties instance.
     */
    private static Properties instance;
    private static String propertiesURI = DEFAULT_PROPERTIES_URI;
    private static boolean initializing = false;

    /**
     * The property store.
     */
    private PropertyStore propertyStore = null;

    // Used for refresh
    private Processor urlGenerator;
    private DOMSerializer domSerializer;
    private long lastUpdate = Long.MIN_VALUE;

    private Properties() {
        // Don't allow creation from outside
    }

    /**
     * Set URI of the resource we will read the properties from.
     */
    public static void init(String propertiesURI) {
        Properties.propertiesURI = propertiesURI;
        instance();
    }

    /**
     * Invalidate all properties (for testing).
     */
    public static void invalidate() {
        instance = null;
    }

    /**
     * Return the global Properties.
     *
     * @return Properties
     */
    public static Properties instance() {
        if (instance == null) {
            instance = new Properties();
            instance.update();
        }
        return instance;
    }

    /**
     * Make sure we have the latest properties, and if we don't (resource changed), reload them.
     */
    private void update() {
        if (!initializing) {
            done:
            try {
                initializing = true;
                final long current = System.currentTimeMillis();

                if (lastUpdate + RELOAD_DELAY >= current) break done;

                // Create mini-pipeline to read properties if needed
                if (urlGenerator == null) {
                    urlGenerator = PipelineUtils.createURLGenerator(propertiesURI, true);// enable XInclude too
                    domSerializer = new DOMSerializer();
                    PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, domSerializer, ProcessorImpl.INPUT_DATA);
                }

                // Initialize pipeline
                final PipelineContext pipelineContext = new PipelineContext();
                boolean success = false;
                try {
                    urlGenerator.reset(pipelineContext);
                    domSerializer.reset(pipelineContext);

                    // Find whether we can skip reloading
                    if (propertyStore != null && domSerializer.findInputLastModified(pipelineContext) <= lastUpdate) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Not reloading properties because they have not changed.");
                        }
                        lastUpdate = current;
                        success = true;
                        break done;
                    }

                    if (logger.isDebugEnabled()) {
                        logger.debug("Reloading properties because timestamp indicates they may have changed.");
                    }

                    // Read updated properties document
                    domSerializer.start(pipelineContext);
                    final Document document = domSerializer.getDocument(pipelineContext);

                    if (document == null || document.content() == null || document.content().size() == 0) {
                        throw new OXFException("Failure to initialize Orbeon Forms properties");
                    }

                    propertyStore = new PropertyStore(document);

                    lastUpdate = current;

                    success = true;
                } finally {
                    pipelineContext.destroy(success);
                }
            } finally {
                initializing = false;
            }
        }
    }

    public PropertySet getPropertySet() {
        if (propertyStore == null)
            return null;
        update();
        return propertyStore.getGlobalPropertySet();
    }

    public PropertySet getPropertySet(final QName processorName) {
        if (propertyStore == null)
            return null;
        update();
        return propertyStore.getProcessorPropertySet(processorName);
    }

    public Set keySet() {
        if (propertyStore == null)
            return null;

        return propertyStore.getGlobalPropertySet().keySet();
    }
}
