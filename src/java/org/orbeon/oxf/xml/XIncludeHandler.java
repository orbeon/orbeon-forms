package org.orbeon.oxf.xml;

import orbeon.apache.xerces.impl.Constants;
import orbeon.apache.xerces.xni.parser.XMLParserConfiguration;
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
        if ( fChildConfig == null ) {
            fChildConfig = new XIncludeParserConfiguration();
            // use the same error reporter, entity resolver, and security manager.
            if (fErrorReporter != null) fChildConfig.setProperty(ERROR_REPORTER, fErrorReporter);
            if (fEntityResolver != null) fChildConfig.setProperty(ENTITY_RESOLVER, fEntityResolver);
            if (fSecurityManager != null) fChildConfig.setProperty(SECURITY_MANAGER, fSecurityManager);

            // use the same namespace context
            fChildConfig.setProperty(
                Constants.XERCES_PROPERTY_PREFIX
                    + Constants.NAMESPACE_CONTEXT_PROPERTY,
                fNamespaceContext);

            XIncludeHandler newHandler =
                (XIncludeHandler)fChildConfig.getProperty(
                    Constants.XERCES_PROPERTY_PREFIX
                        + Constants.XINCLUDE_HANDLER_PROPERTY);
            newHandler.setParent(this);
            newHandler.setDocumentHandler(this.getDocumentHandler());

        }
        XIncludeListener xIncludeListener = (XIncludeListener) threadLocal.get();
        if (xIncludeListener != null)
            xIncludeListener.inclusion(getBaseURI(0), xmlAttributes.getValue("href"));
        return super.handleIncludeElement(xmlAttributes);
    }
}
