package org.orbeon.oxf.xml;

import orbeon.apache.xerces.xni.XMLAttributes;
import orbeon.apache.xerces.xni.XNIException;

/**
 * This is our own version of XIncludeHandler that supports a listener to report inclusions.
 */
public class XIncludeHandler extends orbeon.apache.xerces.xinclude.XIncludeHandler {

    private static ThreadLocal threadLocal = new ThreadLocal();

    public static void setXIncludeListener(XIncludeListener xIncludeListener) {
        threadLocal.set(xIncludeListener);
    }

    public interface XIncludeListener {
        public void inclusion(String base, String href);
    }

    protected boolean handleIncludeElement(XMLAttributes xmlAttributes) throws XNIException {
        XIncludeListener xIncludeListener = (XIncludeListener) threadLocal.get();
        if (xIncludeListener != null)
            xIncludeListener.inclusion(getBaseURI(0), xmlAttributes.getValue("href"));
        return super.handleIncludeElement(xmlAttributes);
    }
}
