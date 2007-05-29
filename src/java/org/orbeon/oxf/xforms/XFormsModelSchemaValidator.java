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
import com.sun.msv.grammar.Expression;
import com.sun.msv.grammar.xmlschema.*;
import com.sun.msv.reader.GrammarReaderController;
import com.sun.msv.reader.util.GrammarLoader;
import com.sun.msv.util.DatatypeRef;
import com.sun.msv.util.StartTagInfo;
import com.sun.msv.util.StringRef;
import com.sun.msv.verifier.Acceptor;
import com.sun.msv.verifier.regexp.REDocumentDeclaration;
import com.sun.msv.verifier.regexp.StringToken;
import com.sun.msv.verifier.regexp.ExpressionAcceptor;
import com.sun.msv.verifier.regexp.SimpleAcceptor;
import com.sun.msv.verifier.regexp.xmlschema.XSAcceptor;
import com.sun.msv.datatype.xsd.DatatypeFactory;
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
import org.orbeon.saxon.om.NodeInfo;
import org.relaxng.datatype.Datatype;
import org.relaxng.datatype.DatatypeException;
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
        instanceData.addSchemaError(em, element.getStringValue(), null);
    }

    private void addSchemaError(final Attribute attribute, final String errMsg) {
        // Looks like if n is an element and errMsg == null then the problem is missing character
        // data.
        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(attribute);
        instanceData.addSchemaError(errMsg, attribute.getStringValue(), null);
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

    private void validateElement(final Element element, final Acceptor acceptor, final IDConstraintChecker icc) {
        final StartTagInfo startTagInfo;
        {
            final String nsURI = element.getNamespaceURI();
            final String nam = element.getName();
            final String qnam = element.getQualifiedName();
            final List attLst = element.attributes();
            final AttributesImpl atts = new AttributesImpl();

            for (final Iterator itr = attLst.iterator(); itr.hasNext();) {
                final Attribute att = (Attribute) itr.next();
                final String auri = att.getNamespaceURI();
                final String anam = att.getName();
                final String aQNam = att.getQualifiedName();
                final String val = att.getValue();
                atts.addAttribute(auri, anam, aQNam, null, val);
            }
            startTagInfo = new StartTagInfo(nsURI, nam, qnam, atts, validationContext);
        }

        final StringRef stringRef = new StringRef();
        final Acceptor childAcceptor = getChildAcceptor(element, startTagInfo, acceptor, stringRef);
        if (icc != null) {
            icc.onNextAcceptorReady(startTagInfo, childAcceptor, element);
            handleIDErrors(icc);
        }

        final int stringCareLevel = childAcceptor.getStringCareLevel();
        final DatatypeRef datatypeRef = new DatatypeRef();
        validateChildren(element, childAcceptor, startTagInfo, stringCareLevel, icc, datatypeRef);
        if (!childAcceptor.isAcceptState(null)) {
            childAcceptor.isAcceptState(stringRef);
            addSchemaError(element, stringRef.str);
        }
        if (icc != null) {
            icc.endElement(element, datatypeRef.types);
            handleIDErrors(icc);
        }
        if (!acceptor.stepForward(childAcceptor, null)) {
            acceptor.stepForward(childAcceptor, stringRef);
            addSchemaError(element, stringRef.str);
        }
    }

    /**
     * Note that all of the attribs of elt should be in si.attributes.  If they are out of synch
     * it break the ability to access the attribs by index.
     */
    private void validateChildren(final Element element, final Acceptor acceptor, final StartTagInfo startTagInfo, final int stringCareLevel,
                                  final IDConstraintChecker icc, final DatatypeRef datatypeRef) {

        final int end = startTagInfo.attributes.getLength();
        final StringRef sr = new StringRef();
        final DatatypeRef attDRef = new DatatypeRef();
        for (int i = 0; i < end; i++) {
            final String uri = startTagInfo.attributes.getURI(i);
            if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) continue;
            final String nam = startTagInfo.attributes.getLocalName(i);
            final String qNam = startTagInfo.attributes.getQName(i);
            final String val = startTagInfo.attributes.getValue(i);

            if (!acceptor.onAttribute2(uri, nam, qNam, val, startTagInfo.context, null, attDRef)) {
                final Attribute att = element.attribute(i);
                acceptor.onAttribute2(uri, nam, qNam, val, startTagInfo.context, sr, (DatatypeRef) null);
                addSchemaError(att, sr.str);
            }
            final Attribute att = element.attribute(i);
            if (icc != null) {
                icc.feedAttribute(acceptor, att, attDRef.types);
                handleIDErrors(icc);
            }
        }
        if (!acceptor.onEndAttributes(startTagInfo, null)) {
            acceptor.onEndAttributes(startTagInfo, sr);
            addSchemaError(element, sr.str);
        }
        for (final Iterator itr = element.elementIterator(); itr.hasNext();) {
            final Element chld = (Element) itr.next();
            validateElement((Element) chld, acceptor, icc);
        }
        // If we just iterate over nodes, i.e. use nodeIterator() ) then validation of char data
        // ends up being incorrect.  Specifically elements of type xs:string end up being invalid
        // when they are empty. ( Which is wrong. )
        final String txt = element.getText();
        switch (stringCareLevel) {
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
                    datatypeRef.types = null;
                    break;
                }
            case Acceptor.STRING_PROHIBITED:
                {
                    final String trmd = txt.trim();
                    if (trmd.length() > 0) {
                        addSchemaError(element, sr.str);
                    }
                    datatypeRef.types = null;
                    break;
                }
            case Acceptor.STRING_STRICT:
                {
                    if (!acceptor.onText2(txt, startTagInfo.context, null, datatypeRef)) {
                        acceptor.onText2(txt, startTagInfo.context, sr, null);
                        addSchemaError(element, sr.str);
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
            final String resolvedURLString = XFormsUtils.resolveResourceURL(pipelineContext, modelElement, schemaURI);
            final URL resolvedURL = XFormsSubmissionUtils.createAbsoluteURL(resolvedURLString, null, externalContext);

            // Load associated grammar
            schemaGrammar = loadCacheGrammar(pipelineContext, resolvedURL.toExternalForm());
        }
    }

    /**
     * Load and cache a Grammar for a given schema URI.
     */
    private Grammar loadCacheGrammar(final PipelineContext pipelineContext, final String schemaURI) {
        try {
            final URL url = URLFactory.createURL(schemaURI);
            final Long modificationTime = NetUtils.getLastModified(url, (Long) null);

            final Cache cache = ObjectCache.instance();
            final SchemaKey schemaKey = new SchemaKey(url);

            final SchemaInfo schemaInfo;
            {
                final Object cached = cache.findValid(pipelineContext, schemaKey, modificationTime);
                schemaInfo = cached == null ? null : (SchemaInfo) cached;
            }

            // Grammar is thread safe while REDocumentDeclaration is not so cache grammar
            // instead of REDocumentDeclaration
            final Grammar grammar;
            if (schemaInfo == null || !schemaInfo.includesUpToDate()) {
                final SchemaInfo newSchemaInfo = new SchemaInfo();

                final InputSource is = XMLUtils.ENTITY_RESOLVER.resolveEntity("", schemaURI);
                final MSVGrammarReaderController controller = new MSVGrammarReaderController(schemaURI, newSchemaInfo);
                final SAXParserFactory factory = XMLUtils.createSAXParserFactory(false, true);

                grammar = GrammarLoader.loadSchema(is, controller, factory);
                newSchemaInfo.setGrammar(grammar);
                cache.add(pipelineContext, schemaKey, modificationTime, newSchemaInfo);
            } else {
                grammar = schemaInfo.getGrammar();
            }
            return grammar;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Apply schema validation.
     *
     * @param instance      instance to validate
     */
    public void applySchema(XFormsInstance instance) {
        if (!isSkipInstanceSchemaValidation() && schemaGrammar != null) {
            final REDocumentDeclaration documentDeclaration = new REDocumentDeclaration(schemaGrammar);
            final Acceptor acceptor = documentDeclaration.createAcceptor();
            final Element instanceRootElement = instance.getDocument().getRootElement();
            final IDConstraintChecker idConstraintChecker = new IDConstraintChecker();

            validateElement(instanceRootElement, acceptor, idConstraintChecker);
            idConstraintChecker.endDocument();
            handleIDErrors(idConstraintChecker);
        }
    }

    /**
     * Check whether a node's value satisfies a simple schema type definition given by namespace URI and local name.
     *
     * @param containingNodeInfo    node containing the value (to update validation MIPs)
     * @param value                 value to validate
     * @param typeNamespaceURI      namespace URI of the type ("" if no namespace)
     * @param typeLocalname         local name of the type
     * @param typeQName             QName of type type (for error handling)
     * @return                      validation error message, null if no error
     */
    public String validateDatatype(NodeInfo containingNodeInfo, String value, String typeNamespaceURI, String typeLocalname, String typeQName, LocationData locationData, String modelBindId) {

        if (typeNamespaceURI == null)
            typeNamespaceURI = "";

        // REDocumentDeclaration is not reentrant
        final REDocumentDeclaration documentDeclaration = new REDocumentDeclaration(schemaGrammar);

        // Find expression to use to validate
        final Expression contentModelExpression;
        {
            if( typeNamespaceURI.equals(XSAcceptor.XMLSchemaNamespace) ) {
                // Handle built-in schema type
                try {
                    contentModelExpression = schemaGrammar.getPool().createData(DatatypeFactory.getTypeByName(typeLocalname) );
                } catch (DatatypeException e) {
                    throw new ValidationException("Built-in schema type not found: " + typeLocalname, locationData);
                }
            } else {
                // Find schema for type namespace
                final XMLSchemaSchema schema = ((XMLSchemaGrammar) schemaGrammar).getByNamespace(typeNamespaceURI);
                if (schema == null)
                    throw new ValidationException("No schema found for namespace: " + typeNamespaceURI, locationData);

                final ComplexTypeExp complexTypeExpression = schema.complexTypes.get(typeLocalname);
                if (complexTypeExpression != null) {
                    // XForms mandates simple types
                    throw new ValidationException("Simple type required for type: " + typeQName, locationData);
                } else {
                    // Find simple type in schema
                    final SimpleTypeExp simpleTypeExpression = schema.simpleTypes.get(typeLocalname);
                    if (simpleTypeExpression == null)
                        throw new ValidationException("Simple type not found: " + typeQName, locationData);

                    contentModelExpression = simpleTypeExpression;
                }
            }
        }

        // Create a simple acceptor
        final ExpressionAcceptor expressionAcceptor = new SimpleAcceptor(documentDeclaration, contentModelExpression, null, null);

        // Send text to acceptor
        final StringRef errorStringRef = new StringRef();
        final DatatypeRef datatypeRef = new DatatypeRef();
        expressionAcceptor.onText2(value, validationContext, errorStringRef, datatypeRef);

        // Return validation error if any
        return errorStringRef.str;
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
