package org.orbeon.oxf.xml;

public class XMLUtils {

    public static String prefixFromQName(String qName) {
        final int colonIndex = qName.indexOf(':');
        return (colonIndex == -1) ? "" : qName.substring(0, colonIndex);
    }

    public static String localNameFromQName(String qName) {
        final int colonIndex = qName.indexOf(':');
        return (colonIndex == -1) ? qName : qName.substring(colonIndex + 1);
    }

    public static String buildQName(String prefix, String localname) {
        return (prefix == null || prefix.equals("")) ? localname : prefix + ":" + localname;
    }

    /**
     * Encode a URI and local name to an exploded QName (also known as a "Clark name") String.
     */
    public static String buildExplodedQName(String uri, String localname) {
        if ("".equals(uri))
            return localname;
        else {
            return "{" + uri + '}' + localname;
        }
    }

    // http://www.w3.org/TR/xpath-30/#doc-xpath30-URIQualifiedName
    public static String buildURIQualifiedName(String uri, String localname) {
        if ("".equals(uri))
            return localname;
        else {
            return "Q{" + uri + '}' + localname;
        }
    }
}
