package org.orbeon.oxf.xml.xerces;

import orbeon.apache.xerces.xni.XMLAttributes;
import orbeon.apache.xerces.xni.XNIException;
import orbeon.apache.xerces.xni.parser.XMLConfigurationException;
import orbeon.apache.xerces.xni.parser.XMLParserConfiguration;
import orbeon.apache.xerces.impl.Constants;

/**
 * This is our own version of XIncludeHandler that supports a listener to report inclusions.
 *
 * NOTE: As of 2007-07-12, we have removed the old code dealing with features, and reverted to modifying the base class
 * in Xerces to call the listener defined here. This has the drawback of having to modify Xerces, but the experience of
 * upgrading to Xerces 2.9 shows that we don't gain much by choosing the alternative of copying over some Xerces code to
 * here as was done before.
 */
public class XIncludeHandler extends orbeon.apache.xerces.xinclude.XIncludeHandler {

    public XIncludeHandler() {
    }
}
