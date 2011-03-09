package org.orbeon.oxf.xml.xerces;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Boasts a couple of improvements over the 'stock' xerces parser factory.
 *
 * o Doesn't create a new parser every time one calls setFeature or getFeature.  Stock one
 *   has to do this because valid feature set is encapsulated in the parser code.
 *
 * o Creates a XercesJAXPSAXParser instead of SaxParserImpl. See XercesJAXPSAXParser for
 *   why this is an improvement.
 *
 * o The improvements cut the time it takes to a SAX parser via JAXP in
 *   half and reduce the amount of garbage created when accessing '/' in
 *   the examples app from 9019216 bytes to 8402880 bytes.
 */
public class XercesSAXParserFactoryImpl extends SAXParserFactory {

    private final Map<String, Boolean> features;
    private final Set<String> recognizedFeatures;
    private final XMLUtils.ParserConfiguration parserConfiguration;

    public XercesSAXParserFactoryImpl() {
        this(XMLUtils.ParserConfiguration.PLAIN);
    }

    public XercesSAXParserFactoryImpl(XMLUtils.ParserConfiguration parserConfiguration) {
        this.parserConfiguration = parserConfiguration;

        // NOTE: Creating a configuration can be expensive, so callers should create factories sparingly
        final OrbeonParserConfiguration configuration = XercesSAXParser.makeConfig(parserConfiguration);

        this.recognizedFeatures = Collections.unmodifiableSet(configuration.getRecognizedFeatures());
        this.features = configuration.getFeatures();

        setNamespaceAware(true); // this is needed by some tools in addition to the feature
        setValidating(parserConfiguration.validating);
    }

    public boolean getFeature(final String key) throws SAXNotRecognizedException {
        if (!recognizedFeatures.contains(key)) throw new SAXNotRecognizedException(key);
        return features.get(key) == Boolean.TRUE;
    }

    public void setFeature(final String key, final boolean val) throws SAXNotRecognizedException {
        if (!recognizedFeatures.contains(key)) throw new SAXNotRecognizedException(key);
        features.put(key, val ? Boolean.TRUE : Boolean.FALSE);
    }

    public SAXParser newSAXParser() {
        try {
            return new XercesJAXPSAXParser(this, features, parserConfiguration);
        } catch (final SAXException se) {
            // Translate to ParserConfigurationException
            throw new OXFException(se); // so we see a decent stack trace!
        }
    }
}