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
package org.orbeon.oxf.resources;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The priority resource manager delegates to two or more resource managers the
 * loading of documents. For example, if a flat file resource manager doesn't
 * contain a document, a second classloader resource manager might load it.
 *
 * This is an important feature that allows an application developper to bundle a
 * resource, and still allow the user to override it easily.
 */
public class PriorityResourceManagerImpl implements ResourceManager {

    List resourceManagers = new ArrayList();

    public PriorityResourceManagerImpl(Map props) {

        // Populate resource manager factories list with nulls
        int resourceCount = 0;
        for (Iterator i = props.keySet().iterator(); i.hasNext();) {
            String priorityKey = (String) i.next();
            if (priorityKey.startsWith(PriorityResourceManagerFactory.PRIORITY_PROPERTY))
                resourceManagers.add(null);
        }

        // Create resource managers
        for (Iterator i = props.keySet().iterator(); i.hasNext();) {
            String priorityKey = (String) i.next();
            if (priorityKey.startsWith(PriorityResourceManagerFactory.PRIORITY_PROPERTY)) {
                try {
                    // Get position
                    String positionString = priorityKey.substring
                            (PriorityResourceManagerFactory.PRIORITY_PROPERTY.length());
                    int position = Integer.parseInt(positionString);

                    // Create instance
                    Class clazz = Class.forName((String) props.get(priorityKey));
                    Constructor constructor = clazz.getConstructor(new Class[]{Map.class});
                    ResourceManagerFactoryFunctor factory =
                            (ResourceManagerFactoryFunctor) constructor.newInstance(new Object[]{props});
                    ResourceManager instance = factory.makeInstance();

                    resourceManagers.set(position - 1, instance);
                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        }
    }

    public Node getContentAsDOM(final String key) {
        return (Node) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getContentAsDOM(key);
            }
        });
    }

    public Document getContentAsDOM4J(final String key) {
        return (Document) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getContentAsDOM4J(key);
            }
        });
    }

    public void getContentAsSAX(final String key, final ContentHandler handler) {
        delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                resourceManager.getContentAsSAX(key, handler);
                return null;
            }
        });
    }

    public void getContentAsSAX(final String key, final ContentHandler handler, final boolean validating, final boolean handleXInclude) {
        delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                resourceManager.getContentAsSAX(key, handler, validating, handleXInclude);
                return null;
            }
        });
    }

    /**
     * Returns a binary input stream for the specified key. The key could point
     * to any document type (text or binary).
     * @param key A Resource Manager key
     * @return a input stream
     */
    public InputStream getContentAsStream(final String key) {
        return (InputStream) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getContentAsStream(key);
            }
        });
    }

    /**
     * Gets the last modified timestamp for the specofoed resource
     * @param key A Resource Manager key
     * @return a timestamp
     */
    public long lastModified(final String key) {
        return ((Long) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return new Long(resourceManager.lastModified(key));
            }
        })).longValue();
    }

    /**
     * Returns the length of the file denoted by this abstract pathname.
     * @return The length, in bytes, of the file denoted by this abstract pathname, or 0L if the file does not exist
     */
    public int length(final String key) {
        return ((Integer) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return new Integer(resourceManager.length(key));
            }
        })).intValue();
    }

    /**
     * Indicates if the resource manager implementation suports write operations
     * @return true if write operations are allowed
     */
    public boolean canWrite(final String key) {
        final boolean[] result = new boolean[1];
        delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                // Logical "or"
                result[0] |= resourceManager.canWrite(key);
                return null;
            }
        });

        return result[0];
    }

    /**
     * Allows writing to the resource
     * @param key A Resource Manager key
     * @return an output stream
     */
    public OutputStream getOutputStream(final String key) {
        return (OutputStream) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                if (!resourceManager.canWrite(key))
                    throw new ResourceNotFoundException("Try next resource manager.");
                return resourceManager.getOutputStream(key);
            }
        });
    }

    /**
     * Allow writing to the resource
     * @param key A Resource Manager key
     * @return  a writer
     */
    public Writer getWriter(final String key) {
        return (Writer) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                if (!resourceManager.canWrite(key))
                    throw new ResourceNotFoundException("Try next resource manager.");
                return resourceManager.getWriter(key);
            }
        });
    }

    public String getRealPath(final String key) {
        return (String) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getRealPath(key);
            }
        });
    }


    public ContentHandler getWriteContentHandler(final String key) {
        return (ContentHandler) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                if (!resourceManager.canWrite(key))
                    throw new ResourceNotFoundException("Try next resource manager.");
                return resourceManager.getWriteContentHandler(key);
            }
        });
    }

    public XMLReader getXMLReader() {
        return (XMLReader) delegate(new Operation() {
            public Object run(ResourceManager resourceManager) {
                return resourceManager.getXMLReader();
            }
        });
    }

//    public void writeDOM(final String key, final Node node) {
//        delegate(new Operation() {
//            public Object run(ResourceManager resourceManager) {
//                resourceManager.writeDOM(key, node);
//                return null;
//            }
//        });
//    }
//
//    public void writeDOM4J(final String key, final Document document) {
//        delegate(new Operation() {
//            public Object run(ResourceManager resourceManager) {
//                resourceManager.writeDOM4J(key, document);
//                return null;
//            }
//        });
//    }

    private static interface Operation {
        public Object run(ResourceManager resourceManager);
    }

    private Object delegate(Operation operation) {
        Exception firstException = null;
        for (Iterator i = resourceManagers.iterator(); i.hasNext();) {
            try {
                ResourceManager resourceManager = (ResourceManager) i.next();
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
}
