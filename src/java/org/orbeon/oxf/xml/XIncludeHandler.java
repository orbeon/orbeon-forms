package org.orbeon.oxf.xml;

import orbeon.apache.xerces.impl.Constants;
import orbeon.apache.xerces.util.ParserConfigurationSettings;
import orbeon.apache.xerces.xni.XMLAttributes;
import orbeon.apache.xerces.xni.XNIException;
import orbeon.apache.xerces.xni.parser.XMLComponentManager;
import orbeon.apache.xerces.xni.parser.XMLConfigurationException;
import orbeon.apache.xerces.xni.parser.XMLParserConfiguration;

/**
 * This is our own version of XIncludeHandler that supports a listener to report inclusions.
 * 
 * 4/9/2005 d :
 * Profiling showed that were losing time to the management of feature settings.  Here's the 
 * background ( Keeping in mind Xerce's component pipeline model ) :
 * 
 * o  Components have a manager and they generally get their settings from their manager.
 * 
 * o  A sax parser begins by creating a parse config, in our case XIncludeParserConfiguration, which
 *    then builds a Xerces pipeline.
 * 
 * o  An XmlParserConfiguration puts in its a pipeline an XIncludeHandler, which the config creates
 *    itself.  In this basically treated as just another component in the pipeline.
 * 
 * o  When an XIncludeHandler receives an xinclude element it creates a child 
 *    XIncludeParserConfiguration which is then used to parse the include.
 * 
 * o  With one exception, XIncludeHandlers do not use any of the feature settings themselves.
 *    Instead they just hold onto these settings and pass them on to child 
 *    XIncludeParserConfigurations.
 * 
 * o  What the profiler showed is that when the XIncludeParserConfiguration began parsing time was
 *    being spent while the XIncludeHandler copied settings. 
 *    ( The config is the handler's manager. )  Note this is before any parsing has actually 
 *    happened.
 * 
 * Now since XIncludeHandler doesn't really care about the feature settings at all our handler
 * has been modified so that 
 * a.) It basically ignores feature settings
 * b.) It has a ref to its 'owner' XmlParserConfiguration. ( The one that created it, the one whose
 *     pipeline it is in. )
 * c.) Iff the handler sees gets notified of an xinclude element will it copy feature settings
 *     from its managing XmlParserConfiguration.  Additionally when it copies the settings it
 *     copies it from the managing XmlParserConfiguration to child XmlParserConfigration.
 * 
 * This gets a few ms in the single thread 512MB test and about 30 ms in the 50 thread 512MB test.
 * ( So only about .5% :( )  
 */
public class XIncludeHandler extends orbeon.apache.xerces.xinclude.XIncludeHandler {
    
    private static final ThreadLocal threadLocal = new ThreadLocal();

    public static void setXIncludeListener( final XIncludeListener xIncludeListener ) {
        threadLocal.set( xIncludeListener );
    }

    public interface XIncludeListener {

        public void inclusion(String base, String href);
    
    }

    /**
     * @see XIncludeHandler
     */
    private final XIncludeParserConfiguration owner;
    
    public XIncludeHandler( final XIncludeParserConfiguration o ) {
        owner = o;
    }

    /**
     * @see XIncludeHandler
     */
    private void copyFeatures( final java.util.Enumeration ftrs, final String pfx ) {
        final String[] ftrArr = new String[ 1 ];
        while ( ftrs.hasMoreElements() ) {
            final String id = pfx + ( String )ftrs.nextElement();
            if ( !owner.isRecognizedFeature( id ) ) continue; 
            ftrArr[ 0 ] = id; 
            final boolean val = owner.getFeature( id );
            fChildConfig.addRecognizedFeatures( ftrArr );
            fChildConfig.setFeature( id, val );
        }
    }

    protected boolean handleIncludeElement( final XMLAttributes atts ) throws XNIException {
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
        
        // 4/9/2005 d : See class doc
        final java.util.Enumeration xercFtrs = Constants.getXercesFeatures();
        copyFeatures( xercFtrs, Constants.XERCES_FEATURE_PREFIX );

        final java.util.Enumeration ftrs2 = Constants.getSAXFeatures();
        copyFeatures( ftrs2, Constants.SAX_FEATURE_PREFIX );
        
        
        XIncludeListener xIncludeListener = (XIncludeListener) threadLocal.get();
        if (xIncludeListener != null)
            xIncludeListener.inclusion(getBaseURI(0), atts.getValue("href"));
        return super.handleIncludeElement(atts);
    }
    /**
     * @see XIncludeHandler
     */
    protected void copyFeatures
    ( final XMLComponentManager notUsed1, final ParserConfigurationSettings notUsed2 ) {
    }
    /**
     * @see XIncludeHandler
     */
    protected void copyFeatures
    ( final XMLComponentManager notUsed1, final XMLParserConfiguration notUsed2 ) {
    }
    /**
     * @see XIncludeHandler
     */
    public void setFeature( final String id, final boolean state ) 
    throws XMLConfigurationException {
        if ( ALLOW_UE_AND_NOTATION_EVENTS.equals( id ) ) {
            super.setFeature( id, state );
        }
    }
}
