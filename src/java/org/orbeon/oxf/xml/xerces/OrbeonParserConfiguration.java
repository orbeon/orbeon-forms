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
package org.orbeon.oxf.xml.xerces;

import orbeon.apache.xerces.parsers.XIncludeAwareParserConfiguration;
import org.orbeon.oxf.xml.XMLUtils;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class OrbeonParserConfiguration extends XIncludeAwareParserConfiguration {

    private boolean externalEntities;

    public OrbeonParserConfiguration() {
        this(XMLUtils.ParserConfiguration.PLAIN);
    }

    public OrbeonParserConfiguration(XMLUtils.ParserConfiguration parserConfiguration) {
        super(null, null, null);
        this.externalEntities = parserConfiguration.externalEntities;

        // Set validation feature
        super.setFeature(VALIDATION, parserConfiguration.validating);
        // Set XInclude feature
        if (parserConfiguration.handleXInclude) {
            super.setFeature(XINCLUDE_FEATURE, true);
            fXIncludeHandler = new orbeon.apache.xerces.xinclude.XIncludeHandler();
            setProperty(XINCLUDE_HANDLER, fXIncludeHandler);
            addCommonComponent(fXIncludeHandler);
        } else {
            super.setFeature(XINCLUDE_FEATURE, false);
        }
    }

    private static final String FEATURE_NS_PREFIXES = "http://xml.org/sax/features/namespace-prefixes";

    // Used by our XercesSAXParserFactoryImpl
    public Set<String> getRecognizedFeatures() {
        final TreeSet<String> result = new TreeSet<String>();
        result.addAll(fRecognizedFeatures);
        // Xerces uses PARSER_SETTINGS internally and makes sure that nothing from outside Xerces passes it in. But
        // we are exposing features collection here so need to remove PARSER_SETTING in case the feature set is passed
        // back in to Xerces.
        result.remove(PARSER_SETTINGS);
        // 2/16/2004 d : Xerces special cases  http://xml.org/sax/features/namespaces and
        //               http://xml.org/sax/features/namespace-prefixes and consequently they
        //               won't be available unless we add them manually.
        result.add(FEATURE_NS_PREFIXES);
        result.add("http://xml.org/sax/features/namespaces");
        return result;
    }

    // Used by our XercesSAXParserFactoryImpl
    public Map<String, Boolean> getFeatures() {
        // Xerces uses PARSER_SETTINGS internally and makes sure that nothing from outside Xerces passes it in. But
        // we are exposing features collection here so need to remove PARSER_SETTING in case the feature set is passed
        // back in to Xerces.
        final TreeMap<String, Boolean> result = new TreeMap<String, Boolean>(fFeatures);
        result.remove(PARSER_SETTINGS);
        
        result.put("http://xml.org/sax/features/namespaces", true);
        result.put("http://xml.org/sax/features/namespace-prefixes", false);

        result.put("http://xml.org/sax/features/external-general-entities", externalEntities);
        result.put("http://xml.org/sax/features/external-parameter-entities", externalEntities);

        return result;
    }
}
