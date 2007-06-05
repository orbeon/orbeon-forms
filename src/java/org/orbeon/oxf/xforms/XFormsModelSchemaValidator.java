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
import com.sun.msv.reader.xmlschema.XMLSchemaReader;
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
import org.orbeon.oxf.xml.TransformerUtils;
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

    // REDocumentDeclaration is not reentrant, but the validator is used by a single thread
    private REDocumentDeclaration documentDeclaration;

    public XFormsModelSchemaValidator(Element modelElement) {
        this.modelElement = modelElement;
        this.schemaURIs = modelElement.attributeValue("schema");
    }

    private static class MSVGrammarReaderController implements GrammarReaderController {

        static private Logger logger = LoggerFactory.createLogger(MSVGrammarReaderController.class);

        private final String baseURI;
        private final SchemaInfo schemaInfo;

        MSVGrammarReaderController(final String baseURI, final SchemaInfo schemaInfo) {
            this.baseURI = baseURI;
            this.schemaInfo = schemaInfo;
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
            final java.net.URL u = URLFactory.createURL(baseURI, sid);
            schemaInfo.addInclude(u);

            final String surl = u.toString();
            return XMLUtils.ENTITY_RESOLVER.resolveEntity("", surl);
        }
    }

    private static class SchemaKey extends CacheKey {
        final int hash;
        final URL url;

        SchemaKey(final URL u) {
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

        void addInclude(final URL u) throws java.io.IOException {
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
                final URL u = (URL) includes.get(i);
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
        final String newErrorMessage;
        if (errMsg == null) {
            // Looks like if n is an element and errMsg == null then the problem is missing
            // character data.  No idea why MSV doesn't just give us the error msg itself.
            newErrorMessage = "Missing character data.";
        } else {
            newErrorMessage = errMsg;
        }
        instanceData.addSchemaError(newErrorMessage, element.getStringValue(), null);
    }

    private void addSchemaError(final Attribute attribute, final String errMsg) {
        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(attribute);
        instanceData.addSchemaError(errMsg, attribute.getStringValue(), null);
    }

    private void handleIDErrors(final IDConstraintChecker icc) {
        for (ErrorInfo errorInfo = icc.clearErrorInfo(); errorInfo != null; errorInfo = icc.clearErrorInfo()) {
            addSchemaError(errorInfo.element, errorInfo.message);
        }
    }

    private boolean validateElement(final Element element, final Acceptor acceptor, final IDConstraintChecker icc, final boolean isReportErrors) {

        // Create StartTagInfo
        final StartTagInfo startTagInfo;
        {
            final String uri = element.getNamespaceURI();
            final String name = element.getName();
            final String qName = element.getQualifiedName();
            final List attributesList = element.attributes();
            final AttributesImpl attributes = new AttributesImpl();

            for (final Iterator iterator = attributesList.iterator(); iterator.hasNext();) {
                final Attribute attribute = (Attribute) iterator.next();
                final String attributeURI = attribute.getNamespaceURI();
                final String attributeName = attribute.getName();
                final String attributeQName = attribute.getQualifiedName();
                final String attributeValue = attribute.getValue();
                attributes.addAttribute(attributeURI, attributeName, attributeQName, null, attributeValue);
            }
            startTagInfo = new StartTagInfo(uri, name, qName, attributes, validationContext);
        }

        final StringRef stringRef = new StringRef();

        // Get child acceptor
        final Acceptor childAcceptor;
        {
            Acceptor tempChildAcceptor = acceptor.createChildAcceptor(startTagInfo, null);
            if (tempChildAcceptor == null) {
                if (isReportErrors) {
                    tempChildAcceptor = acceptor.createChildAcceptor(startTagInfo, stringRef);
                    addSchemaError(element, stringRef.str);
                } else {
                    return false;
                }
            }
            childAcceptor = tempChildAcceptor;
        }

        // Handle id errors
        if (icc != null && isReportErrors) {
            icc.onNextAcceptorReady(startTagInfo, childAcceptor, element);
            handleIDErrors(icc);
        }

        // Validate children
        final int stringCareLevel = childAcceptor.getStringCareLevel();
        final DatatypeRef datatypeRef = new DatatypeRef();
        final boolean childrenValid = validateChildren(element, childAcceptor, startTagInfo, stringCareLevel, icc, datatypeRef, isReportErrors);
        if (!childrenValid && !isReportErrors)
            return false;

        if (!childAcceptor.isAcceptState(null)) {
            if (isReportErrors) {
                childAcceptor.isAcceptState(stringRef);
                addSchemaError(element, stringRef.str);
            } else {
                return false;
            }
        }

        // Handle id errors
        if (icc != null && isReportErrors) {
            icc.endElement(element, datatypeRef.types);
            handleIDErrors(icc);
        }

        // Get back to parent acceptor
        if (!acceptor.stepForward(childAcceptor, null)) {
            if (isReportErrors) {
                acceptor.stepForward(childAcceptor, stringRef);
                addSchemaError(element, stringRef.str);
            } else {
                return false;
            }
        }

        // This element is valid
        return true;
    }

    /**
     * Validate an element following the XML Schema "lax" mode.
     *
     * @param element   element to validate
     */
    private void validateElementLax(final Element element) {

        final String elementURI = element.getNamespaceURI();
        final String elementName = element.getName();
//        final String elementQName = element.getQualifiedName();
        {
            // Find expression for element type
            final Expression expression;
            {
                // Find schema for type namespace
                final XMLSchemaSchema schema = ((XMLSchemaGrammar) schemaGrammar).getByNamespace(elementURI);
                if (schema != null) {
                    // Try to find the expression in the schema
                    expression = schema.elementDecls.get(elementName);
                } else {
                    // No schema so no expression
                    expression = null;
                }
            }

            if (expression != null) {
                // Found type for element, so validate element
                final Acceptor acceptor = documentDeclaration.createAcceptor();
                validateElement(element, acceptor, null, true);
            } else {
                // Element does not have type, so try to validate attributes and children elements

                // Attributes
                {
                    final List attributesList = element.attributes();
                    for (final Iterator iterator = attributesList.iterator(); iterator.hasNext();)   {
                        final Attribute attribute = (Attribute) iterator.next();
                        final String attributeURI = attribute.getNamespaceURI();
                        final String attributeName = attribute.getName();
//                        final String attributeQName = attribute.getQualifiedName();
//                        final String attributeValue = attribute.getValue();

                        // Find expression for element type
                        final Expression attributeExpression;
                        {
                            // Find schema for type namespace
                            final XMLSchemaSchema schema = ((XMLSchemaGrammar) schemaGrammar).getByNamespace(attributeURI);
                            if (schema != null) {
                                attributeExpression = schema.attributeDecls.get(attributeName);
                            } else {
                                attributeExpression = null;
                            }
                        }
                        if (attributeExpression != null) {
                            // TODO: find out way of validating an attribute only

//                            final ExpressionAcceptor expressionAcceptor = new SimpleAcceptor(documentDeclaration, attributeExpression, null, null);
//                            // Validate attribute value
//                            final StringRef errorStringRef = new StringRef();
//                            final DatatypeRef datatypeRef = new DatatypeRef();
//
//                            if (!expressionAcceptor.onAttribute2(attributeURI, attributeName, attributeQName, attributeValue, validationContext, errorStringRef, datatypeRef)) {
//                                if (errorStringRef.str == null) // not sure if this can happen
//                                    errorStringRef.str = "Error validating attribute";
//                                addSchemaError(attribute, errorStringRef.str);
//                            }

//                            if (!expressionAcceptor.onText2(attributeValue, validationContext, errorStringRef, datatypeRef)) {
//                                if (errorStringRef.str == null) // not sure if this can happen
//                                    errorStringRef.str = "Error validating attribute";
//                                addSchemaError(attribute, errorStringRef.str);
//                            }
//
//                            // Check final acceptor state
//                            if (!expressionAcceptor.isAcceptState(errorStringRef)) {
//                                if (errorStringRef.str == null) // not sure if this can happen
//                                    errorStringRef.str = "Error validating attribute";
//                                addSchemaError(attribute, errorStringRef.str);
//                            }
                        }
                    }
                }

                // Validate children elements
                for (final Iterator iterator = element.elementIterator(); iterator.hasNext();) {
                    final Element childElement = (Element) iterator.next();
                    validateElementLax(childElement);
                }
            }
        }
    }

    /**
     * Note that all of the attribs of element should be in startTagInfo.attributes. If they are out of synch it break
     * the ability to access the attribs by index.
     */
    private boolean validateChildren(final Element element, final Acceptor acceptor, final StartTagInfo startTagInfo, final int stringCareLevel,
                                     final IDConstraintChecker icc, final DatatypeRef datatypeRef, final boolean isReportErrors) {


        // Validate attributes
        final StringRef stringRef = new StringRef();
        {
            final DatatypeRef attributeDatatypeRef = new DatatypeRef();
            final int end = startTagInfo.attributes.getLength();
            for (int i = 0; i < end; i++) {
                final String uri = startTagInfo.attributes.getURI(i);

                // TODO: Is this legacy check still useful?
                if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri))
                    continue;

                final String name = startTagInfo.attributes.getLocalName(i);
                final String qName = startTagInfo.attributes.getQName(i);
                final String value = startTagInfo.attributes.getValue(i);

                if (!acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, null, attributeDatatypeRef)) {
                    if (isReportErrors) {
                        final Attribute attribute = element.attribute(i);
                        acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, stringRef, (DatatypeRef) null);
                        addSchemaError(attribute, stringRef.str);
                    } else {
                        return false;
                    }
                }
                final Attribute attribute = element.attribute(i);
                if (icc != null && isReportErrors) {
                    icc.feedAttribute(acceptor, attribute, attributeDatatypeRef.types);
                    handleIDErrors(icc);
                }
            }

            if (!acceptor.onEndAttributes(startTagInfo, null)) {
                if (isReportErrors) {
                    acceptor.onEndAttributes(startTagInfo, stringRef);
                    addSchemaError(element, stringRef.str);
                } else {
                    return false;
                }
            }
        }

        // Validate children elements
        for (final Iterator iterator = element.elementIterator(); iterator.hasNext();) {
            final Element childElement = (Element) iterator.next();
            final boolean isChildElementValid = validateElement((Element) childElement, acceptor, icc, isReportErrors);
            if (!isReportErrors && !isChildElementValid)
                return false;
        }

        // If we just iterate over nodes, i.e. use nodeIterator() ) then validation of char data ends up being
        // incorrect. Specifically elements of type xs:string end up being invalid when they are empty. (Which is
        // wrong.)

        // TODO: this is very likely wrong as we get the whole text value of the element!!!
        final String text = element.getText();
        switch (stringCareLevel) {
            case Acceptor.STRING_IGNORE:
                {
                    if (text.length() > 0) {
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
                    final String trimmed = text.trim();
                    if (trimmed.length() > 0) {
                        if (isReportErrors) {
                            addSchemaError(element, stringRef.str);
                        } else {
                            return false;
                        }
                    }
                    datatypeRef.types = null;
                    break;
                }
            case Acceptor.STRING_STRICT:
                {
                    if (!acceptor.onText2(text, startTagInfo.context, null, datatypeRef)) {
                        if (isReportErrors) {
                            acceptor.onText2(text, startTagInfo.context, stringRef, null);
                            addSchemaError(element, stringRef.str);
                        } else {
                            return false;
                        }
                    }
                    break;
                }
        }

        // The element children are valid
        return true;
    }

    /**
     * Load XForms model schemas.
     *
     * @param pipelineContext       current PipelineContext
     */
    public void loadSchemas(final PipelineContext pipelineContext) {
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
                final SAXParserFactory factory = XMLUtils.createSAXParserFactory(false, true);// could we use getSAXParserFactory() instead?

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

    private Grammar loadGrammar(final String baseURI, final NodeInfo schemaElementInfo) {

        final SchemaInfo newSchemaInfo = new SchemaInfo();
        final MSVGrammarReaderController controller = new MSVGrammarReaderController(baseURI, newSchemaInfo);
        final SAXParserFactory saxParserFactory = XMLUtils.getSAXParserFactory(false, false);
        final XMLSchemaReader reader = new XMLSchemaReader(controller, saxParserFactory);

        TransformerUtils.writeTinyTree(schemaElementInfo, reader);

        return reader.getResult();
    }

    /**
     * Apply schema validation to an instance. The instance may content a hint specifying whether to perform "lax",
     * "strict", or "skip" validation.
     *
     * @param instance          instance to validate
     */
    public void validateInstance(XFormsInstance instance) {
        if (!isSkipInstanceSchemaValidation() && schemaGrammar != null) {

            // Create REDocumentDeclaration if needed
            if (documentDeclaration == null) {
                documentDeclaration = new REDocumentDeclaration(schemaGrammar);
            }

            // Get validation mode ("lax" is the default)
            final String validation = (instance.getValidation() == null) ? "lax" : instance.getValidation();
            if ("lax".equals(validation)) {
                // Lax validation
                final Element instanceRootElement = instance.getDocument().getRootElement();
                validateElementLax(instanceRootElement);
            } else if ("strict".equals(instance.getValidation())) {
                // Strict validation
                final Acceptor acceptor = documentDeclaration.createAcceptor();
                final Element instanceRootElement = instance.getDocument().getRootElement();
                final IDConstraintChecker idConstraintChecker = new IDConstraintChecker();

                validateElement(instanceRootElement, acceptor, idConstraintChecker, true);
                idConstraintChecker.endDocument();
                handleIDErrors(idConstraintChecker);
            } else {
                // Skip validation
            }
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
     * @param locationData          LocationData to use in case of error
     * @param modelBindId           id of model bind to use in case of error
     * @return                      validation error message, null if no error
     */
    public String validateDatatype(NodeInfo containingNodeInfo, String value, String typeNamespaceURI, String typeLocalname, String typeQName, LocationData locationData, String modelBindId) {

        if (typeNamespaceURI == null)
            typeNamespaceURI = "";

        // Create REDocumentDeclaration if needed
        if (documentDeclaration == null) {
            documentDeclaration = new REDocumentDeclaration(schemaGrammar);
        }

        // Find expression to use to validate
        final Expression contentModelExpression;
        {
            if (typeNamespaceURI.equals(XSAcceptor.XMLSchemaNamespace) ) {
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

                // Find simple type in schema
                final SimpleTypeExp simpleTypeExpression = schema.simpleTypes.get(typeLocalname);
                if (simpleTypeExpression != null) {
                    // There is a simple type definition
                    contentModelExpression = simpleTypeExpression;
                } else {
                    // Find complex type in schema
                    final ComplexTypeExp complexTypeExpression = schema.complexTypes.get(typeLocalname);
                    if (complexTypeExpression != null) {
                        // There is a complex type definition
                        if (complexTypeExpression != null && complexTypeExpression.simpleBaseType != null) {
                            // Complex type with simple content
                            // Here, we only validate the datatype part
                            // NOTE: Here we are guessing a little bit from MSV by looking at simpleBaseType. Is this 100% correct?
                            contentModelExpression = complexTypeExpression;
                        } else {
                            // XForms mandates simple types or complex types with simple content
                            throw new ValidationException("Simple type or complex type with simple content required for type: " + typeQName, locationData);
                        }
                    } else {
                        // Find element declaration in schema
                        final ElementDeclExp elementDeclExp = schema.elementDecls.get(typeLocalname);
                        if (elementDeclExp != null) {
                            // There is an element type definition
                            final ElementDeclExp.XSElementExp xsElementExp = elementDeclExp.getElementExp();
                            final Expression contentModel = xsElementExp.contentModel;
                            if (contentModel instanceof ComplexTypeExp && ((ComplexTypeExp) contentModel).simpleBaseType != null) {
                                // Element complex type with simple content
                                // Here, we only validate the datatype part
                                // NOTE: Here again, we do some guesswork from MSV. Is this 100% correct?
                                contentModelExpression = contentModel;
                            } else {
                                throw new ValidationException("Simple type or complex type with simple content required for type: " + typeQName, locationData);
                            }
                        } else {
                            // XForms mandates simple types or complex types with simple content
                            throw new ValidationException("Simple type or complex type with simple content required for type: " + typeQName, locationData);
                        }
                    }
                    // TODO: Must also look at schema.attributeDecls?
                }
            }
        }

        // Create a simple acceptor
        final ExpressionAcceptor expressionAcceptor = new SimpleAcceptor(documentDeclaration, contentModelExpression, null, null);

        // Validate text
        final StringRef errorStringRef = new StringRef();
        final DatatypeRef datatypeRef = new DatatypeRef();
        if (!expressionAcceptor.onText2(value, validationContext, errorStringRef, datatypeRef)) {
            if (errorStringRef.str == null) // not sure if this can happen
                errorStringRef.str = "Error validating simple type";
            return errorStringRef.str;
        }

        // Check final acceptor state
        if (!expressionAcceptor.isAcceptState(errorStringRef)) {
            if (errorStringRef.str == null) // not sure if this can happen
                errorStringRef.str = "Error validating simple type";
            return errorStringRef.str;
        }

        // Value is valid
        return null;
    }

    /**
     * Return whether XForms instance validation should be skipped based on configuration
     * properties.
     *
     * @return  whether validation should be skipped
     */
    private boolean isSkipInstanceSchemaValidation() {
        final OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();
        final Boolean schmVldatdObj =  propertySet.getBoolean(XFormsConstants.XFORMS_VALIDATION_PROPERTY, true);
        return !schmVldatdObj.booleanValue();
    }

    /**
     * Return the value of the @schema attribute on the model.
     *
     * @return  value of the @schema attribute on the model
     */
    public String getSchemaURIs() {
        return schemaURIs;
    }
}
