/**
 *  Copyright (C) 2004 - 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms;

import com.sun.msv.grammar.Grammar;
import com.sun.msv.grammar.IDContextProvider2;
import com.sun.msv.reader.GrammarReaderController;
import com.sun.msv.reader.util.GrammarLoader;
import com.sun.msv.util.DatatypeRef;
import com.sun.msv.util.StartTagInfo;
import com.sun.msv.util.StringRef;
import com.sun.msv.verifier.Acceptor;
import com.sun.msv.verifier.regexp.REDocumentDeclaration;
import com.sun.msv.verifier.regexp.StringToken;
import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.CacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.msv.IDConstraintChecker;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.relaxng.datatype.Datatype;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Provides XML Schema validation services for the XForms model. 
 */
public class XFormsModelSchemaValidator {

    private static final ValidationContext validationContext = new ValidationContext();

    private Element modelElement;
    private String schemaURIs;
    private Grammar schemaGrammar;

    public XFormsModelSchemaValidator(Element modelElement) {
        this.modelElement = modelElement;
        this.schemaURIs = modelElement.attributeValue("schema");
    }

    private static class MSVGrammarReaderController implements GrammarReaderController {

        static private Logger logger = LoggerFactory.createLogger(MSVGrammarReaderController.class);

        private final String base;
        private final SchemaInfo schemaInfo;

        MSVGrammarReaderController(final String bs, final SchemaInfo schmInf) {
            base = bs;
            schemaInfo = schmInf;
        }

        public void warning(final Locator[] locs, final String msg) {
            if (locs == null && locs.length == 0) {
                logger.warn(msg);
            } else {
                final String frst = XMLUtils.toString(locs[0]);
                final StringBuffer sb = new StringBuffer(frst);
                for (int i = 1; i < locs.length; i++) {
                    sb.append(',');
                    final String locMsg = XMLUtils.toString(locs[i]);
                    sb.append(locMsg);
                }
                sb.append(':');
                sb.append(msg);
                final String logMsg = sb.toString();
                logger.warn(logMsg);
            }
        }

        public void error(final Locator[] locs, final String msg, final Exception ex) {
            final LocationData ld = locs.length > 0 ? new LocationData(locs[0]) : null;
            throw new ValidationException(msg, ex, ld);
        }

        public InputSource resolveEntity(final String pid, final String sid)
                throws SAXException, IOException {
            final java.net.URL u = URLFactory.createURL(base, sid);
            schemaInfo.addInclude(u);

            final String surl = u.toString();
            return XMLUtils.ENTITY_RESOLVER.resolveEntity("", surl);
        }

    }

    private static class SchemaKey extends CacheKey {
        final int hash;
        final java.net.URL url;

        SchemaKey(final java.net.URL u) {
            setClazz(SchemaKey.class);
            url = u;
            hash = url.hashCode();
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(final Object rhsObj) {
            final boolean ret;
            if (rhsObj instanceof SchemaKey) {
                final SchemaKey rhs = (SchemaKey) rhsObj;
                ret = url.equals(rhs.url);
            } else {
                ret = false;
            }
            return ret;
        }
    }

    private static class SchemaInfo {

        private final ArrayList includes = new ArrayList(0);
        private final ArrayList modTimes = new ArrayList(0);
        private Grammar grammar;

        void addInclude(final java.net.URL u) throws java.io.IOException {
            // Get the time first.  This way if there's a problem the array lengths will remain
            // the same.
            final Long modTim = NetUtils.getLastModified(u, (Long) null);
            includes.add(u);
            modTimes.add(modTim);
        }

        boolean includesUpToDate() {
            boolean ret = true;
            final int size = includes.size();
            for (int i = 0; ret && i < size; i++) {
                final java.net.URL u = (java.net.URL) includes.get(i);
                try {
                    final Long crntTim = NetUtils.getLastModified(u, (Long) null);
                    final Long lstTim = (Long) modTimes.get(i);
                    ret = crntTim.equals(lstTim);
                } catch (final java.io.IOException e) {
                    // We won't propagate here.  Reason is that while an include may be missing
                    // it may just be the case that it isn't included anymore _and_ it has been
                    // removed.  So, we return false and then on a reparse we will find out the
                    // truth.
                    ret = false;
                }
            }
            return ret;
        }

        void setGrammar(final Grammar g) {
            grammar = g;
        }

        Grammar getGrammar() {
            return grammar;
        }
    }

    private static class ValidationContext implements IDContextProvider2 {
        public void onID(final Datatype dt, final String s) {
        }

        public String resolveNamespacePrefix(final String s) {
            return null;
        }

        public String getBaseUri() {
            return null;
        }

        public boolean isUnparsedEntity(final String s) {
            return false;
        }

        public boolean isNotation(final String s) {
            return false;
        }

        public void onID(final Datatype dt, final StringToken st) {
        }
    }

    private void addSchemaError(final Element element, final String errMsg) {
        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(element);
        final String em;
        if (errMsg == null) {
            // Looks like if n is an element and errMsg == null then the problem is missing
            // character data.  No idea why MSV doesn't just give us the error msg itself.
            em = "Missing character data.";
        } else {
            em = errMsg;
        }
        instanceData.addSchemaError(em, element.getStringValue());
    }

    private void addSchemaError(final Attribute attribute, final String errMsg) {
        // Looks like if n is an element and errMsg == null then the problem is missing character
        // data.
        final InstanceData instDat = XFormsUtils.getLocalInstanceData(attribute);
        instDat.addSchemaError(errMsg, attribute.getStringValue());
        // FIXME: The code below does nothing. Why is it here at all?
//        final Element elt = att.getParent();
//        final InstanceData eltInstDat = XFormsUtils.getLocalInstanceData(elt);
    }

    private Acceptor getChildAcceptor
            (final Element elt, final StartTagInfo si, final Acceptor acc, final StringRef sr) {
        Acceptor ret = acc.createChildAcceptor(si, null);
        if (ret == null) {
            ret = acc.createChildAcceptor(si, sr);
            addSchemaError(elt, sr.str);
        }
        return ret;
    }

    private void handleIDErrors(final IDConstraintChecker icc) {
        for (ErrorInfo errInf = icc.clearErrorInfo(); errInf != null; errInf = icc.clearErrorInfo()) {
            addSchemaError(errInf.element, errInf.message);
        }
    }

    private void validateElement(final Element elt, final Acceptor acc, final IDConstraintChecker icc) {
        final String nsURI = elt.getNamespaceURI();
        final String nam = elt.getName();
        final String qnam = elt.getQualifiedName();
        final List attLst = elt.attributes();
        final AttributesImpl atts = new AttributesImpl();
        // Note that we don't strip xxform:* atts here as doing so would cause confustion in
        // validateChildren
        for (final Iterator itr = attLst.iterator(); itr.hasNext();) {
            final Attribute att = (Attribute) itr.next();
            final String auri = att.getNamespaceURI();
            final String anam = att.getName();
            final String aQNam = att.getQualifiedName();
            final String val = att.getValue();
            atts.addAttribute(auri, anam, aQNam, null, val);
        }
        final StartTagInfo si = new StartTagInfo(nsURI, nam, qnam, atts, validationContext);

        final StringRef sr = new StringRef();
        final Acceptor chldAcc = getChildAcceptor(elt, si, acc, sr);
        icc.onNextAcceptorReady(si, chldAcc, elt);
        handleIDErrors(icc);

        final int charCare = chldAcc.getStringCareLevel();
        final DatatypeRef dref = new DatatypeRef();
        validateChildren(elt, chldAcc, si, charCare, icc, dref);
        if (!chldAcc.isAcceptState(null)) {
            chldAcc.isAcceptState(sr);
            addSchemaError(elt, sr.str);
        }
        icc.endElement(elt, dref.types);
        handleIDErrors(icc);
        if (!acc.stepForward(chldAcc, null)) {
            acc.stepForward(chldAcc, sr);
            addSchemaError(elt, sr.str);
        }
    }

    /**
     * Note that all of the attribs of elt should be in si.attributes.  If they are out of synch
     * it break the ability to access the attribs by index.
     */
    private void validateChildren
            (final Element elt, final Acceptor acc, final StartTagInfo si, final int charCare
             , final IDConstraintChecker icc, final DatatypeRef dref) {

        final int end = si.attributes.getLength();
        final StringRef sr = new StringRef();
        final DatatypeRef attDRef = new DatatypeRef();
        for (int i = 0; i < end; i++) {
            final String uri = si.attributes.getURI(i);
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) continue;
            final String nam = si.attributes.getLocalName(i);
            final String qNam = si.attributes.getQName(i);
            final String val = si.attributes.getValue(i);

            if (!acc.onAttribute2(uri, nam, qNam, val, si.context, null, attDRef)) {
                final Attribute att = elt.attribute(i);
                acc.onAttribute2(uri, nam, qNam, val, si.context, sr, (DatatypeRef) null);
                addSchemaError(att, sr.str);
            }
            final Attribute att = elt.attribute(i);
            icc.feedAttribute(acc, att, attDRef.types);
            handleIDErrors(icc);
        }
        if (!acc.onEndAttributes(si, null)) {
            acc.onEndAttributes(si, sr);
            addSchemaError(elt, sr.str);
        }
        for (final Iterator itr = elt.elementIterator(); itr.hasNext();) {
            final Element chld = (Element) itr.next();
            validateElement((Element) chld, acc, icc);
        }
        // If we just iterate over nodes, i.e. use nodeIterator() ) then validation of char data
        // ends up being incorrect.  Specifically elements of type xs:string end up being invalid
        // when they are empty. ( Which is wrong. )
        final String txt = elt.getText();
        switch (charCare) {
            case Acceptor.STRING_IGNORE:
                {
                    if (txt.length() > 0) {
//                        addSchemaError(elt, sr.str);
                        // TODO: Check this! It is not clear whether this should actually be tested
                        // as above. I have noticed that some documents that should pass validation
                        // actually do not with the above, namely with <xsd:element> with no type
                        // but the element actually containing character content. But is removing
                        // the test correct?
                    }
                    dref.types = null;
                    break;
                }
            case Acceptor.STRING_PROHIBITED:
                {
                    final String trmd = txt.trim();
                    if (trmd.length() > 0) {
                        addSchemaError(elt, sr.str);
                    }
                    dref.types = null;
                    break;
                }
            case Acceptor.STRING_STRICT:
                {
                    if (!acc.onText2(txt, si.context, null, dref)) {
                        acc.onText2(txt, si.context, sr, null);
                        addSchemaError(elt, sr.str);
                    }
                    break;
                }
        }
    }

    /**
     * Load XForms model schemas.
     */
    public void loadSchemas(final PipelineContext pipelineContext, XFormsContainingDocument containingDocument) {
        if (!isSkipInstanceSchemaValidation()) {
            final String schemaURI = schemaURIs;// TODO: check for multiple schemas

            // External instance
            final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

            // Resolve URL
            // TODO: We do not support "optimized" access here, we always use an URL, because loadGrammar() wants a URL
            final String resolvedURLString = XFormsUtils.resolveURL(containingDocument, pipelineContext, modelElement, false, schemaURI);
            final URL resolvedURL = XFormsSubmissionUtils.createURL(resolvedURLString, null, externalContext);

            // Load associated grammar
            schemaGrammar = loadGrammar(pipelineContext, resolvedURL.toExternalForm());
        }
    }

    /**
     * Load and cache a Grammar for a given schema URI.
     */
    private Grammar loadGrammar(final PipelineContext pipelineContext, final String schemaURI) {
        try {
            final URL url = URLFactory.createURL(schemaURI);
            final Long modTim = NetUtils.getLastModified(url, (Long) null);

            final Cache cache = ObjectCache.instance();
            final SchemaKey schmKey = new SchemaKey(url);

            final SchemaInfo schmInf;
            {
                final Object cached = cache.findValid(pipelineContext, schmKey, modTim);
                schmInf = cached == null ? null : (SchemaInfo) cached;
            }

            // Grammar is thread safe while REDocumentDeclaration is not so cache grammar
            // instead of REDocumentDeclaration
            final Grammar grmr;
            if (schmInf == null || !schmInf.includesUpToDate()) {
                final SchemaInfo newSchmInf = new SchemaInfo();

                final InputSource is = XMLUtils.ENTITY_RESOLVER.resolveEntity("", schemaURI);
                final MSVGrammarReaderController cntrlr = new MSVGrammarReaderController(schemaURI, newSchmInf);
                final SAXParserFactory fctry = XMLUtils.createSAXParserFactory(false, true);

                grmr = GrammarLoader.loadSchema(is, cntrlr, fctry);
                newSchmInf.setGrammar(grmr);
                cache.add(pipelineContext, schmKey, modTim, newSchmInf);
            } else {
                grmr = schmInf.getGrammar();
            }
            return grmr;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Apply schema validation only.
     */
    public void applySchema(XFormsInstance instance) {
        if (!isSkipInstanceSchemaValidation() && schemaGrammar != null) {
            final REDocumentDeclaration rdd = new REDocumentDeclaration(schemaGrammar);
            final Acceptor acc = rdd.createAcceptor();
            final Element relt = instance.getInstanceDocument().getRootElement();
            final IDConstraintChecker icc = new IDConstraintChecker();

            validateElement(relt, acc, icc);
            icc.endDocument();
            handleIDErrors(icc);
        }
    }

    /**
     * Return whether XForms instance validation should be skipped based on configuration
     * properties.
     */
    private boolean isSkipInstanceSchemaValidation() {
        OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();
        Boolean schmVldatdObj =  propertySet.getBoolean(XFormsConstants.XFORMS_VALIDATION_PROPERTY, true);
        return !schmVldatdObj.booleanValue();
    }

    /**
     * Return the value of the @schema attribute on the model.
     */
    public String getSchemaURIs() {
        return schemaURIs;
    }
}
