/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xml;

import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.trans.IndependentContext;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.net.URL;

public class XPathCacheStandaloneContext extends IndependentContext {

    private static final URIResolver URI_RESOLVER = new XPathCacheURIResolver();

    public XPathCacheStandaloneContext() {
        super();
        getConfiguration().setURIResolver(URI_RESOLVER);
    }

    public XPathCacheStandaloneContext(Configuration configuration) {
        super(configuration);
        getConfiguration().setURIResolver(URI_RESOLVER);
    }

    private static class XPathCacheURIResolver implements URIResolver {
        public Source resolve(String href, String base) throws TransformerException {
            try {
                // Saxon Document.makeDoc() changes the base to "" if it is null
                if ("".equals(base))
                    base = null;
                URL url = URLFactory.createURL(base, href);
                return new SAXSource(XMLUtils.newSAXParser(false, false).getXMLReader(), new InputSource(url.openStream()));
            } catch (SAXException e) {
                throw new TransformerException(e);
            } catch (IOException e) {
                throw new TransformerException(e);
            }
        }
    }
}
