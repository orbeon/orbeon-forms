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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.TransformerXMLReceiver;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.resources.handler.OXFHandler;
import org.orbeon.oxf.xml.ForwardingXMLReceiver;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLReaderToReceiver;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.w3c.dom.Node;
import org.xml.sax.Locator;
import org.xml.sax.XMLReader;

import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Base class for most resource manager implementations.
 */
public abstract class ResourceManagerBase implements ResourceManager {

    private static final String MIN_RELOAD_INTERVAL_KEY = "oxf.resources.common.min-reload-interval";
    private static final long DEFAULT_MIN_RELOAD_INTERVAL = 2 * 1000;

    private ExpirationMap lastModifiedMap;

    /**
     * Initialisation. Should be called only by sub-classes
     */
    protected ResourceManagerBase(Map props) {
        // Override default reload interval if property is specified
        final String minReloadIntervalString = (String) props.get(MIN_RELOAD_INTERVAL_KEY);
        long minReloadInterval = DEFAULT_MIN_RELOAD_INTERVAL;
        if (minReloadIntervalString != null) {
            final long longValue = Long.parseLong(minReloadIntervalString);
            if (longValue < 0)
                throw new OXFException("Value for property '" + MIN_RELOAD_INTERVAL_KEY + "' must be a non-negative integer.");
            minReloadInterval = longValue;
        }
        lastModifiedMap = new ExpirationMap(minReloadInterval);
    }

    public Node getContentAsDOM(String key) {
        try {
            final TransformerXMLReceiver identity = TransformerUtils.getIdentityTransformerHandler();
            final DOMResult domResult = new DOMResult(XMLUtils.createDocument());
            identity.setResult(domResult);
            getContentAsSAX(key, identity);
            return domResult.getNode();
        } catch (IllegalArgumentException e) {
            throw new OXFException(e);
        }
    }

    public org.dom4j.Document getContentAsDOM4J(String key) {
        final LocationSAXContentHandler lch = new LocationSAXContentHandler();
        getContentAsSAX(key, lch);
        return lch.getDocument();
    }
    
    public org.dom4j.Document getContentAsDOM4J(String key, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical) {
        final LocationSAXContentHandler lch = new LocationSAXContentHandler();
        getContentAsSAX(key, lch, parserConfiguration, handleLexical);
        return lch.getDocument();
    }

    public void getContentAsSAX(final String key, XMLReceiver handler) {
        getContentAsSAX(key, handler, XMLUtils.ParserConfiguration.XINCLUDE_ONLY, true);
    }

    public void getContentAsSAX(String key, XMLReceiver xmlReceiver, XMLUtils.ParserConfiguration parserConfiguration, boolean handleLexical) {
        InputStream inputStream = null;
        final Locator[] locator = new Locator[1];
        try {
            inputStream = getContentAsStream(key);
            XMLUtils.inputStreamToSAX(inputStream, OXFHandler.PROTOCOL + ":" + key, new ForwardingXMLReceiver(xmlReceiver) {
                public void setDocumentLocator(Locator loc) {
                    locator[0] = loc;
                    super.setDocumentLocator(loc);
                }
            }, parserConfiguration, handleLexical);
        } catch (ValidationException ve) {
            throw ve;
        } catch (ResourceNotFoundException rnfe) {
            throw rnfe;
        } catch (Exception e) {
            if(locator[0] != null)
                throw new ValidationException("Can't retrieve or parse document for key " + key, e, new LocationData(locator[0]));
            else
                throw new OXFException("Can't retrieve or parse document for key " + key, e);
        } finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException e) {
                throw new OXFException(e);
            }
        }
    }

    public XMLReader getXMLReader() {
        return new XMLReaderToReceiver() {
            @Override
            public void parse(String systemId) {
                getContentAsSAX(systemId, createXMLReceiver());
            }
        };
    }

    public XMLReceiver getWriteContentHandler(String key) {
        final OutputStream os = getOutputStream(key);
        final TransformerXMLReceiver transformer = TransformerUtils.getIdentityTransformerHandler();
        transformer.setResult(new StreamResult(os));
        return transformer;
    }

    final synchronized public long lastModified(String key, boolean doNotThrowResourceNotFound) {
        // Do only 1 call to currentTimeMillis()
        final long currentTime = System.currentTimeMillis();
        Object value = lastModifiedMap.get(currentTime, key);
        if (value == null) {
            // We don't have the information or or it has expired
            try {
                long lastModified = lastModifiedImpl(key, doNotThrowResourceNotFound);
                lastModifiedMap.put(currentTime, key, lastModified);
                return lastModified;
            } catch (ResourceNotFoundException e) {
                lastModifiedMap.put(currentTime, key, e);
                throw e;
            }
        } else {
            if (value instanceof ResourceNotFoundException) {
                throw (ResourceNotFoundException) value;
            } else {
                return (Long) value;
            }
        }
    }

    public boolean exists(String key) {
        try {
            final InputStream is = getContentAsStream(key);
            try {
                is.close();
            } catch (IOException e) {
                // ignore
            }
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    abstract protected long lastModifiedImpl(String key, boolean doNotThrowResourceNotFound);
}
