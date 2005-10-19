package org.orbeon.oxf.xml;

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
 * Boasts a couple of improvemnts over the 'stock' xerces parser factory.
 * <ul>
 * <li>
 * Doesn't create a new parser every time one calls setFeature or getFeature.  Stock one
 * has to do this because valid feature set is encapsulated in the parser code.
 * </li>
 * <li>
 * Creates a XercesJAXPSAXParser instead of SaxParserImpl. See XercesJAXPSAXParser for
 * why this is an improvement.
 * </li>
 * </ul>
 * The improvements cut the time it takes to a SAX parser via JAXP in
 * half and reduce the amount of garbage created when accessing '/' in
 * the examples app from 9019216 bytes to 8402880 bytes.
 */
public class XercesSAXParserFactoryImpl extends SAXParserFactory {

    private static final Collection recognizedFeaturesXInclude;
    private static final Map defaultFeaturesXInclude;
    private static final Collection recognizedFeaturesNoXInclude;
    private static final Map defaultFeaturesNoXInclude;

    static {
        TomcatClasspathFix.applyIfNeedBe();
        {
            final XIncludeParserConfiguration cfg = XercesSAXParser.makeConfig(true);
            final Collection ftrs = cfg.getRecognizedFeatures();
            recognizedFeaturesXInclude = Collections.unmodifiableCollection(ftrs);
            defaultFeaturesXInclude = cfg.getFeatures();
            // This was being done in XMLUtils.createSaxParserFactory before.  Maybe want to
            // move it back if we decide to make this class more general purpose.
            defaultFeaturesXInclude.put("http://xml.org/sax/features/namespaces", Boolean.TRUE);
            defaultFeaturesXInclude.put("http://xml.org/sax/features/namespace-prefixes", Boolean.FALSE);
        }
        {
            final XIncludeParserConfiguration cfg = XercesSAXParser.makeConfig(false);
            final Collection ftrs = cfg.getRecognizedFeatures();
            recognizedFeaturesNoXInclude = Collections.unmodifiableCollection(ftrs);
            defaultFeaturesNoXInclude = cfg.getFeatures();
            // This was being done in XMLUtils.createSaxParserFactory before.  Maybe want to
            // move it back if we decide to make this class more general purpose.
            defaultFeaturesNoXInclude.put("http://xml.org/sax/features/namespaces", Boolean.TRUE);
            defaultFeaturesNoXInclude.put("http://xml.org/sax/features/namespace-prefixes", Boolean.FALSE);
        }
    }

    private final Hashtable features;
    private final boolean handleXInclude;

    public XercesSAXParserFactoryImpl() {
        this(true);
    }

    public XercesSAXParserFactoryImpl(boolean handleXInclude) {
        this.handleXInclude = handleXInclude;
        features = new Hashtable(handleXInclude ? defaultFeaturesXInclude : defaultFeaturesNoXInclude);
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
            ret = new XercesJAXPSAXParser(this, features, handleXInclude);
        } catch (final SAXException se) {
            // Translate to ParserConfigurationException
            throw new OXFException(se); // so we see a decent stack trace!
//            throw new ParserConfigurationException(se.getMessage());
        }
        return ret;
    }

    private Collection getRecognizedFeatures() {
        return handleXInclude ? recognizedFeaturesXInclude : recognizedFeaturesNoXInclude;
    }
}