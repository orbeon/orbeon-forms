package org.orbeon.oxf.xml.xerces;

/*
 * An improvment over orbeon.apache.xerces.parsers.SAXParser.  Every time   
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
 *   which OPS creates SAX parsers this accumlates quickly and consequently we start losing processor time to the
 *   garbage collector.
 */

class XercesSAXParser extends orbeon.apache.xerces.parsers.SAXParser {
    static final String ACCESSIBLE_NOTIFY_BUILTIN_REFS = NOTIFY_BUILTIN_REFS;
    static final String ACCESSIBLE_SYMBOL_TABLE = SYMBOL_TABLE;
    static final String ACCESSIBLE_XMLGRAMMAR_POOL = XMLGRAMMAR_POOL;

    static final String[] RECOGNIZED_FEATURES = {
            NOTIFY_BUILTIN_REFS,
    };

    static final String[] RECOGNIZED_PROPERTIES = {
            SYMBOL_TABLE,
            XMLGRAMMAR_POOL,
    };

    public static OrbeonParserConfiguration makeConfig(boolean validating, boolean handleXInclude) {
        final OrbeonParserConfiguration result = new OrbeonParserConfiguration(validating, handleXInclude);
        result.addRecognizedFeatures(RECOGNIZED_FEATURES);
        result.setFeature(NOTIFY_BUILTIN_REFS, true);

        // Set properties
        result.addRecognizedProperties(RECOGNIZED_PROPERTIES);
        return result;
    }

    public XercesSAXParser(boolean validating, boolean handleXInclude) {
        super(makeConfig(validating, handleXInclude));
    }
}