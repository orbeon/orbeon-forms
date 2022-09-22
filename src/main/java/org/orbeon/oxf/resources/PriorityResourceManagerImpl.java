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
package org.orbeon.oxf.resources;

import org.orbeon.dom.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.ParserConfiguration;
import org.orbeon.oxf.xml.XMLReceiver;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * The priority resource manager delegates to two or more resource managers the loading of documents. For example, if a
 * flat file resource manager doesn't contain a document, a second classloader resource manager might load it.
 *
 * This is an important feature that allows an application developer to bundle a resource, and still allow the user to
 * override it easily.
 */
public class PriorityResourceManagerImpl implements ResourceManager {

    private final List<ResourceManager> resourceManagers = new ArrayList<ResourceManager>();

    public PriorityResourceManagerImpl(Map<String, String> props) {

        // Map an order number to a Map of local properties
        final Map<Integer, Map<String, String>> orderToPropertyNames = new TreeMap<Integer, Map<String, String>>();

        for (final String key: props.keySet()) {
            if (key.startsWith(PriorityResourceManagerFactory.PRIORITY_PROPERTY)) {
                final String substring = key.substring(PriorityResourceManagerFactory.PRIORITY_PROPERTY.length());
                final int dotIndex = substring.indexOf('.');
                final int position;
                final String localPropertyName;
                if (dotIndex == -1) {
                    position = Integer.valueOf(substring);
                    localPropertyName = null;
                } else {
                    position = Integer.valueOf(substring.substring(0, dotIndex));
                    localPropertyName = substring.substring(dotIndex + 1);
                }

                if (orderToPropertyNames.get(position) == null)
                    orderToPropertyNames.put(position, new HashMap<String, String>());

                final Map<String, String> localProperties = orderToPropertyNames.get(position);
                localProperties.put(localPropertyName, props.get(key));
            }
        }

        // Create resource managers in order
        for (final Map.Entry<Integer, Map<String, String>> entry: orderToPropertyNames.entrySet()) {
            final int position = entry.getKey();
            final Map<String, String> localProperties = entry.getValue();
            try {
                // Create instance
                final Class<ResourceManagerFactory> clazz =
                    (Class<ResourceManagerFactory>) Class.forName((String) props.get(PriorityResourceManagerFactory.PRIORITY_PROPERTY + position));
                final Constructor<ResourceManagerFactory> constructor = clazz.getConstructor(Map.class);
                final Map<String, String> allProps = new HashMap<String, String>(props);
                allProps.putAll(localProperties);
                final ResourceManagerFactory factory = constructor.newInstance(allProps);
                final ResourceManager instance = factory.makeInstance();

                resourceManagers.add(instance);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }
    }

    public Document getContentAsOrbeonDom(final String key) {
        return (Document) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getContentAsOrbeonDom(key);
            }
        });
    }

    public Document getContentAsOrbeonDom(final String key, final ParserConfiguration parserConfiguration, final boolean handleLexical) {
        return (Document) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getContentAsOrbeonDom(key, parserConfiguration, handleLexical);
            }
        });
    }

    public void getContentAsSAX(final String key, final XMLReceiver xmlReceiver) {
        delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                resourceManager.getContentAsSAX(key, xmlReceiver);
                return null;
            }
        });
    }

    public void getContentAsSAX(final String key, final XMLReceiver xmlReceiver, final ParserConfiguration parserConfiguration, final boolean handleLexical) {
        delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                resourceManager.getContentAsSAX(key, xmlReceiver, parserConfiguration, handleLexical);
                return null;
            }
        });
    }

    public InputStream getContentAsStream(final String key) {
        return (InputStream) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getContentAsStream(key);
            }
        });
    }

    public long lastModified(final String key, boolean doNotThrowResourceNotFound) {
        for (ResourceManager resourceManager: resourceManagers) {
            long lastModified = resourceManager.lastModified(key, true);
            if (lastModified != -1)
                return lastModified;
        }
        throw new ResourceNotFoundException(key);
    }

    public int length(final String key) {
        return (Integer) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.length(key);
            }
        });
    }

    public String getRealPath(final String key) {
        return (String) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                final String realPath = resourceManager.getRealPath(key);
                // Here consider null as not found
                // The semantic is a bit different from the underlying managers, which have:
                //
                // - null: unsupported
                // - ResourceNotFoundException: supported by not found
                if (realPath == null)
                   throw new ResourceNotFoundException(key);
                return realPath;
            }
        });
    }

    private interface Operation {
        Object run(ResourceManager resourceManager);
    }

    private Object delegate(Operation operation) {
        Exception firstException = null;
        for (ResourceManager resourceManager: resourceManagers) {
            try {
                return operation.run(resourceManager);
            } catch (Exception e) {
                if(!(e instanceof ResourceNotFoundException)) {
                    // this is serious, not just a file not found
                    // we should abort and throw the exception right away
                    if(e instanceof OXFException)
                        throw (OXFException)e;
                    else
                        throw new OXFException(e);
                }
                if (firstException == null)
                    firstException = e;
            }
        }
        if (firstException instanceof ResourceNotFoundException)
            throw ((ResourceNotFoundException) firstException);
        else
            throw new OXFException(firstException);
    }

    public boolean exists(String key) {
        for (ResourceManager resourceManager: resourceManagers) {
            if (resourceManager.exists(key))
                return true;
        }
        return false;
    }
}
