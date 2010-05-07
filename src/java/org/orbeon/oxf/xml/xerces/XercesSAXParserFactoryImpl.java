package org.orbeon.oxf.xml.xerces;

import org.orbeon.oxf.common.OXFException;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;

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

    private static final Collection recognizedFeaturesNonValidatingXInclude;
    private static final Map defaultFeaturesNonValidatingXInclude;

    private static final Collection recognizedFeaturesNonValidatingNoXInclude;
    private static final Map defaultFeaturesNonValidatingNoXInclude;

    private static final Collection recognizedFeaturesValidatingXInclude;
    private static final Map defaultFeaturesValidatingXInclude;

    private static final Collection recognizedFeaturesValidatingNoXInclude;
    private static final Map defaultFeaturesValidatingNoXInclude;

    static {
        {
            final OrbeonParserConfiguration configuration = XercesSAXParser.makeConfig(false, true);
            final Collection features = configuration.getRecognizedFeatures();
            recognizedFeaturesNonValidatingXInclude = Collections.unmodifiableCollection(features);
            defaultFeaturesNonValidatingXInclude = configuration.getFeatures();
            // This was being done in XMLUtils.createSaxParserFactory before.  Maybe want to
            // move it back if we decide to make this class more general purpose.
            defaultFeaturesNonValidatingXInclude.put("http://xml.org/sax/features/namespaces", Boolean.TRUE);
            defaultFeaturesNonValidatingXInclude.put("http://xml.org/sax/features/namespace-prefixes", Boolean.FALSE);
        }
        {
            final OrbeonParserConfiguration configuration = XercesSAXParser.makeConfig(false, false);
            final Collection features = configuration.getRecognizedFeatures();
            recognizedFeaturesNonValidatingNoXInclude = Collections.unmodifiableCollection(features);
            defaultFeaturesNonValidatingNoXInclude = configuration.getFeatures();
            // This was being done in XMLUtils.createSaxParserFactory before.  Maybe want to
            // move it back if we decide to make this class more general purpose.
            defaultFeaturesNonValidatingNoXInclude.put("http://xml.org/sax/features/namespaces", Boolean.TRUE);
            defaultFeaturesNonValidatingNoXInclude.put("http://xml.org/sax/features/namespace-prefixes", Boolean.FALSE);
        }

        {
            final OrbeonParserConfiguration configuration = XercesSAXParser.makeConfig(true, true);
            final Collection features = configuration.getRecognizedFeatures();
            recognizedFeaturesValidatingXInclude = Collections.unmodifiableCollection(features);
            defaultFeaturesValidatingXInclude = configuration.getFeatures();
            // This was being done in XMLUtils.createSaxParserFactory before.  Maybe want to
            // move it back if we decide to make this class more general purpose.
            defaultFeaturesValidatingXInclude.put("http://xml.org/sax/features/namespaces", Boolean.TRUE);
            defaultFeaturesValidatingXInclude.put("http://xml.org/sax/features/namespace-prefixes", Boolean.FALSE);
        }
        {
            final OrbeonParserConfiguration configuration = XercesSAXParser.makeConfig(true, false);
            final Collection features = configuration.getRecognizedFeatures();
            recognizedFeaturesValidatingNoXInclude = Collections.unmodifiableCollection(features);
            defaultFeaturesValidatingNoXInclude = configuration.getFeatures();
            // This was being done in XMLUtils.createSaxParserFactory before.  Maybe want to
            // move it back if we decide to make this class more general purpose.
            defaultFeaturesValidatingNoXInclude.put("http://xml.org/sax/features/namespaces", Boolean.TRUE);
            defaultFeaturesValidatingNoXInclude.put("http://xml.org/sax/features/namespace-prefixes", Boolean.FALSE);
        }
    }

    private final Hashtable features;
    private final boolean validating;
    private final boolean handleXInclude;

    public XercesSAXParserFactoryImpl() {
        this(false, false);
    }

    public XercesSAXParserFactoryImpl(boolean validating, boolean handleXInclude) {
        this.validating = validating;
        this.handleXInclude = handleXInclude;
        if (!validating) {
            features = new Hashtable(handleXInclude ? defaultFeaturesNonValidatingXInclude : defaultFeaturesNonValidatingNoXInclude);
        } else {
            features = new Hashtable(handleXInclude ? defaultFeaturesValidatingXInclude : defaultFeaturesValidatingNoXInclude);
        }
        setNamespaceAware(true); // this is needed by some tools in addition to the feature
    }

    public boolean getFeature(final String key) throws SAXNotRecognizedException {
        if (!getRecognizedFeatures().contains(key)) throw new SAXNotRecognizedException(key);
        return features.get(key) == Boolean.TRUE;
    }

    public void setFeature(final String key, final boolean val) throws SAXNotRecognizedException {
        if (!getRecognizedFeatures().contains(key)) throw new SAXNotRecognizedException(key);
        features.put(key, val ? Boolean.TRUE : Boolean.FALSE);
    }

    public SAXParser newSAXParser() throws ParserConfigurationException {
        final SAXParser ret;
        try {
            ret = new XercesJAXPSAXParser(this, features, validating, handleXInclude);
        } catch (final SAXException se) {
            // Translate to ParserConfigurationException
            throw new OXFException(se); // so we see a decent stack trace!
//            throw new ParserConfigurationException(se.getMessage());
        }
        return ret;
    }

    private Collection getRecognizedFeatures() {
        if (!validating) {
            return handleXInclude ? recognizedFeaturesNonValidatingXInclude : recognizedFeaturesNonValidatingNoXInclude;
        } else {
            return handleXInclude ? recognizedFeaturesValidatingXInclude : recognizedFeaturesValidatingNoXInclude;
        }
    }
}