package org.orbeon.oxf.xml.xerces;

import org.orbeon.oxf.xml.XMLUtils;

/*
* An improvement over orbeon.apache.xerces.parsers.SAXParser.  Every time
* orbeon.apache.xerces.parsers.SAXParser is constructed it looks in
* META-INF/services/orbeon.apache.xerces.xni.parser.XMLParserConfiguration to figure out what
* config to use.  Pbms with this are
*
* o We only want our config to be used. While we have changed the above file, any work done
*   to read the file is really just a waste.
*
* o The contents of the file do not change at runtime so there is little point in repeatedly
*   reading it.
*
* o About 16.4K of garbage gets create with each read of the above file. At the frequency with
*   which OPS creates SAX parsers this accumulates quickly and consequently we start losing processor time to the
*   garbage collector.
*/
class XercesSAXParser extends orbeon.apache.xerces.parsers.SAXParser {

    static final String[] RECOGNIZED_FEATURES = { NOTIFY_BUILTIN_REFS };
    static final String[] RECOGNIZED_PROPERTIES = { SYMBOL_TABLE, XMLGRAMMAR_POOL };

    public static OrbeonParserConfiguration makeConfig(XMLUtils.ParserConfiguration parserConfiguration) {
        final OrbeonParserConfiguration result = new OrbeonParserConfiguration(parserConfiguration);

        result.addRecognizedFeatures(RECOGNIZED_FEATURES);
        result.setFeature(NOTIFY_BUILTIN_REFS, true);
        result.addRecognizedProperties(RECOGNIZED_PROPERTIES);

        return result;
    }

    public XercesSAXParser(XMLUtils.ParserConfiguration parserConfiguration) {
        super(makeConfig(parserConfiguration));
    }
}