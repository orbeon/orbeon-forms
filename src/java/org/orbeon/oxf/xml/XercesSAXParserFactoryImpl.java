package org.orbeon.oxf.xml;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

/**
 * Boasts a couple of improvents over the 'stock' xerces parser factory.  
 * <ul>
 *   <li>
 *       Doesn't create a new parser every time one calls setFeature or getFeature.  Stock one
 *       has to do this because valid feature set is encapsulated in the parser code.
 *   </li>
 *   <li>
 *      Creates a XercesJAXPSAXParser intead of SaxParserImpl.   See XercesJAXPSAXParser for
 *      why this is an improvement.
 *   </li>
 * </ul> 
 * The improvements cut the time it takes to a SAX parser via JAXP in 
 * half and reduce the amount of garbage created when accessing '/' in
 * the examples app from 9019216 bytes to 8402880 bytes.
 */
public class XercesSAXParserFactoryImpl extends SAXParserFactory {
    
    private static final java.util.Collection recognizedFeatures;

    private static final java.util.Map defaultFeatures;

    static {
        TomcatClasspathFix.applyIfNeedBe();
        final XIncludeParserConfiguration cfg = XercesSAXParser.makeConfig();
        final java.util.Collection ftrs = cfg.getRecognizedFeatures();
        recognizedFeatures = java.util.Collections.unmodifiableCollection( ftrs );
        defaultFeatures = cfg.getFeatures();
        // This was being done in XMLUtils.createSaxParserFactory before.  Maybe want to
        // move it back if we decide to make this class more general purpose.
        defaultFeatures.put( "http://xml.org/sax/features/namespaces", Boolean.TRUE );
        defaultFeatures.put( "http://xml.org/sax/features/namespace-prefixes", Boolean.FALSE );
    }
	
    private final java.util.Hashtable features;
	
    public XercesSAXParserFactoryImpl() {
	features = new java.util.Hashtable( defaultFeatures );
        setNamespaceAware( true ); // this is needed by some tools in addition to the feature
    }
	
    public boolean getFeature( final String key ) throws SAXNotRecognizedException {
        //final long strt = System.currentTimeMillis();
        //try {
            if ( !recognizedFeatures.contains( key ) ) throw new SAXNotRecognizedException( key );
            final boolean ret = features.get( key ) == Boolean.TRUE ? true : false;
            return ret;
        //} finally {
        //    System.out.println( "SAXParserFactory.getFeature : " + ( System.currentTimeMillis() - strt ) );
        //}
    }
    public void setFeature( final String key, final boolean val )  throws SAXNotRecognizedException {
       // final long strt = System.currentTimeMillis();
       // try {
            if ( !recognizedFeatures.contains( key ) ) throw new SAXNotRecognizedException( key );
            features.put( key, val ? Boolean.TRUE : Boolean.FALSE );
       // } finally {
       //     System.out.println( "SAXParserFactory.setFeature : " + ( System.currentTimeMillis() - strt ) );
       // }
    }
	
    public SAXParser newSAXParser() throws ParserConfigurationException {
        final SAXParser ret;
        //final long strt = System.currentTimeMillis();
        try {
            ret = new XercesJAXPSAXParser( this, features );
        } catch ( final SAXException se ) {
            // Translate to ParserConfigurationException
            throw new ParserConfigurationException(se.getMessage());
        //} //finally {
        //    System.out.println( "SAXParserFactory.newParser : " + ( System.currentTimeMillis() - strt ) );
        }
        return ret;
    }
}