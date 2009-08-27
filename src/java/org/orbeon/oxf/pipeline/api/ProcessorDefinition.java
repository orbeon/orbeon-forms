/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.pipeline.api;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * ProcessorDefinition encapsulate a processor name and its associated inputs. A ProcessorDefinition
 * object can then be used to instantiate and run the given processor.
 */
public class ProcessorDefinition {
    private QName name;
    private Map<String, Object> entries = new HashMap<String, Object>();

    public ProcessorDefinition() {
    }

    /**
     * Set the qualified name of the processor, for example "oxf:xslt".
     *
     * @param name the qualified name of the processor
     */
    public void setName(QName name) {
        this.name = name;
    }

    /**
     * Add an input with the given name and URL.
     *
     * @param name the name of the input, for example "config"
     * @param url  the URL that will be connected to the input, for example "oxf:/my-file.xml"
     */
    public void addInput(String name, String url) {
        entries.put(name, url);
    }

    /**
     * Add an input with the given name and dom4j Element.
     *
     * @param name    the name of the input, for example "config"
     * @param element the dom4j Element containing the XML document connected to the input
     */
    public void addInput(String name, Element element) {
        entries.put(name, element);
    }

    /**
     * Add an input with the given name and TinyTree node.
     *
     * @param name     the name of the input, for example "config"
     * @param nodeInfo the TinyTree node containing the XML document connected to the input
     */
    public void addInput(String name, NodeInfo nodeInfo) {
        entries.put(name, nodeInfo);
    }

    /**
     * Return the configured mappings for the processor inputs. Keys are of type String and refer to
     * input names. Values are of type String (which must be valid URLs) or dom4j Element
     * (containing an XML document).
     *
     * @return Map of name -> String or Element processor inputs mappings
     */
    public Map<String, Object> getEntries() {
        return entries;
    }

    /**
     * Return the qualified name of the processor.
     *
     * @return the qualified name of the processor
     */
    public QName getName() {
        return name;
    }

    public String toString() {
        final StringBuffer sb = new StringBuffer("[");
        sb.append(Dom4jUtils.qNameToExplodedQName(getName()));
        for (final Map.Entry<String, Object> currentEntry: getEntries().entrySet()) {
            final String key = currentEntry.getKey();
            final Object value = currentEntry.getValue();

            sb.append(", ");
            sb.append(key);
            sb.append(" -> ");
            sb.append((value instanceof String) ? value.toString() : "[inline XML document]");
        }
        sb.append(']');
        return sb.toString();
    }
}
