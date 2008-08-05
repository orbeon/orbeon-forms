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
package org.orbeon.oxf.properties;

import org.dom4j.Document;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.TransformerUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
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
            Throwable throwable = null;
            done:
            try {
                initializing = true;
                final long current = System.currentTimeMillis();

                if (lastUpdate + RELOAD_DELAY >= current) break done;

                final URL url = URLFactory.createURL(propertiesURI);
                final URLConnection uc = url.openConnection();

                if (propertyStore != null && uc.getLastModified() <= lastUpdate) {
                    lastUpdate = current;
                    break done;
                }

                final InputStream in = uc.getInputStream();
                final Document document = TransformerUtils.readDom4j(in, propertiesURI, false);
                propertyStore = new PropertyStore(document);

                lastUpdate = current;

            } catch (final IOException e) {
                throwable = e;
            } finally {
                initializing = false;
            }
            if (throwable != null) {
                throw new OXFException("Failure to initialize Orbeon Forms properties", throwable);
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
