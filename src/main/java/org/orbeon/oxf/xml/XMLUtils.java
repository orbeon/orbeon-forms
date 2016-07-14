package org.orbeon.oxf.xml;

import org.apache.commons.lang3.StringUtils;

public class XMLUtils {

    public static final String XML_CONTENT_TYPE1 = "text/xml";
    public static final String XML_CONTENT_TYPE2 = "application/xml";
    public static final String XML_CONTENT_TYPE3_SUFFIX = "+xml";
    public static final String TEXT_CONTENT_TYPE_PREFIX = "text/";

    public static String unescapeXMLMinimal(String str) {
        str = StringUtils.replace(str, "&amp;", "&");
        str = StringUtils.replace(str, "&lt;", "<");
        str = StringUtils.replace(str, "&gt;", ">");
        return str;
    }

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

    /**
     * Convert a double into a String without scientific notation.
     *
     * This is useful for XPath 1.0, which does not understand the scientific notation.
     */
    public static String removeScientificNotation(double value) {

        String result = Double.toString(value);
        int eIndex = result.indexOf('E');

        if (eIndex == -1) {
            // No scientific notation, return value as is
            return stripZeros(result);
        } else {
            // Scientific notation, convert value

            // Parse string representation
            String mantissa = result.substring(0, eIndex);
            boolean negative = mantissa.charAt(0) == '-';
            String sign = negative ? "-" : "";
            String mantissa1 = mantissa.substring(negative ? 1 : 0, negative ? 2 : 1);
            String mantissa2 = mantissa.substring(negative ? 3 : 2);
            int exponent = Integer.parseInt(result.substring(eIndex + 1));

            // Calculate result
            if (exponent > 0) {
                // Positive exponent, shift decimal point to the right
                int mantissa2Length = mantissa2.length();
                if (exponent > mantissa2Length) {
                    result = sign + mantissa1 + mantissa2 + nZeros(exponent - mantissa2Length);
                } else if (exponent == mantissa2Length) {
                    result = sign + mantissa1 + mantissa2;
                } else {
                    result = sign + mantissa1 + mantissa2.substring(0, exponent) + '.' + mantissa2.substring(exponent);
                }
            } else if (exponent == 0) {
                // Not sure if this can happen
                result = mantissa;
            } else {
                // Negative exponent, shift decimal point to the left
                result = sign + '0' + '.' + nZeros(-exponent - 1) + mantissa1 + mantissa2;
            }
            return stripZeros(result);
        }
    }

    /**
     * Remove unnecessary zeros after the decimal point, e.g. "12.000" becomes "12".
     */
    private static String stripZeros(String s) {
        int index = s.lastIndexOf('.');
        if (index == -1) return s;
        for (int i = index + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '0')
                return s;
        }
        return s.substring(0, index);
    }

    private static String nZeros(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++)
            sb.append('0');
        return sb.toString();
    }

    public static String escapeXMLMinimal(String str) {
        str = StringUtils.replace(str, "&", "&amp;");
        str = StringUtils.replace(str, "<", "&lt;");
        str = StringUtils.replace(str, ">", "&gt;"); // do this one too because the sequence `]]>` is not allowed
        return str;
    }

    public static String escapeXMLForAttribute(String str) {
        str = escapeXMLMinimal(str);
        str = StringUtils.replace(str, "\"", "&quot;");
        return str;
    }

    /**
     * Return whether the given mediatype is considered as XML.
     *
     * TODO: This does test on the mediatype only, but we need one to check the content type as well for the case
     * "text/html; charset=foobar"
     *
     * @param mediatype mediatype or null
     * @return          true if not null and XML mediatype, false otherwise
     */
    public static boolean isXMLMediatype(String mediatype) {
        return mediatype != null && (mediatype.equals(XML_CONTENT_TYPE1)
                || mediatype.equals(XML_CONTENT_TYPE2)
                || mediatype.endsWith(XML_CONTENT_TYPE3_SUFFIX));
    }

    /**
     * Return whether the given content type is considered as text.
     *
     * @param contentType   content type or null
     * @return              true if not null and a text content type, false otherwise.
     */
    public static boolean isTextContentType(String contentType) {
        return contentType != null && contentType.startsWith(TEXT_CONTENT_TYPE_PREFIX);
    }

    public static boolean isJSONContentType(String contentType) {
        return contentType != null && contentType.startsWith( "application/json");
    }

    /**
     * Return whether the given content type is considered as text or JSON.
     *
     * NOTE: There was debate about whether JSON is text or not and whether it should have a text/* mediatype:
     *
     * http://www.alvestrand.no/pipermail/ietf-types/2006-February/001655.html
     *
     * @param contentType   content type or null
     * @return              true if not null and a text or JSON content type, false otherwise
     */
    public static boolean isTextOrJSONContentType(String contentType) {
        return contentType != null && (isTextContentType(contentType) || isJSONContentType(contentType));
    }
}
