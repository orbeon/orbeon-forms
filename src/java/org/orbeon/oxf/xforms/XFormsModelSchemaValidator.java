/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms;

import com.sun.msv.datatype.xsd.DatatypeFactory;
import com.sun.msv.datatype.xsd.XSDatatype;
import com.sun.msv.grammar.Expression;
import com.sun.msv.grammar.Grammar;
import com.sun.msv.grammar.IDContextProvider2;
import com.sun.msv.grammar.xmlschema.*;
import com.sun.msv.reader.GrammarReaderController;
import com.sun.msv.reader.util.GrammarLoader;
import com.sun.msv.reader.xmlschema.XMLSchemaReader;
import com.sun.msv.util.DatatypeRef;
import com.sun.msv.util.StartTagInfo;
import com.sun.msv.util.StringRef;
import com.sun.msv.verifier.Acceptor;
import com.sun.msv.verifier.regexp.ExpressionAcceptor;
import com.sun.msv.verifier.regexp.REDocumentDeclaration;
import com.sun.msv.verifier.regexp.SimpleAcceptor;
import com.sun.msv.verifier.regexp.StringToken;
import com.sun.msv.verifier.regexp.xmlschema.XSAcceptor;
import com.sun.msv.verifier.regexp.xmlschema.XSREDocDecl;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.CacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.msv.IDConstraintChecker;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
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
 *
 * TODO: support multiple schemas
 *
 * TODO: "3.3.1 The model Element [...] The schema list may include URI fragments referring to elements located
 * outside the current model elsewhere in the containing document; e.g. "#myschema"."
 */
public class XFormsModelSchemaValidator {

    private static final ValidationContext validationContext = new ValidationContext();

    private Element modelElement;
    private IndentedLogger indentedLogger;

    private Grammar schemaGrammar;
    private String[] schemaURIs;
    private List<Element> schemaElements;

    // REDocumentDeclaration is not reentrant, but the validator is used by a single thread
    private REDocumentDeclaration documentDeclaration;

    public XFormsModelSchemaValidator(Element modelElement, IndentedLogger indentedLogger) {
        this.modelElement = modelElement;
        this.indentedLogger = indentedLogger;

        // Check for external schemas
        final String schemaAttribute = modelElement.attributeValue(XFormsConstants.SCHEMA_QNAME);
        if (schemaAttribute != null)
            this.schemaURIs = StringUtils.split(NetUtils.encodeHRRI(schemaAttribute, false));

        // Check for inline schemas
        // "3.3.1 The model Element [...] xs:schema elements located inside the current model need not be listed."
        for (final Element schemaElement: Dom4jUtils.elements( modelElement, XMLConstants.XML_SCHEMA_QNAME)) {

            if (schemaElements == null)
                schemaElements = new ArrayList<Element>();

            schemaElements.add(schemaElement);
        }
    }

    public XFormsModelSchemaValidator(String schemaURI) {
        this.schemaURIs = new String[] { schemaURI };
    }

    public boolean hasSchema() {
        return schemaGrammar != null;
    }

    private static class MSVGrammarReaderController implements GrammarReaderController {

        static private Logger logger = LoggerFactory.createLogger(MSVGrammarReaderController.class);

        private final String baseURI;
        private final SchemaInfo schemaInfo;

        MSVGrammarReaderController(final String baseURI, final SchemaInfo schemaInfo) {
            this.baseURI = baseURI;
            this.schemaInfo = schemaInfo;
        }

        public void warning(final Locator[] locators, final String message) {
            if (locators == null || locators.length == 0) {
                logger.warn(message);
            } else {
                final String first = XMLUtils.toString(locators[0]);
                final StringBuffer sb = new StringBuffer(first);
                for (int i = 1; i < locators.length; i++) {
                    sb.append(',');
                    final String locMsg = XMLUtils.toString(locators[i]);
                    sb.append(locMsg);
                }
                sb.append(':');
                sb.append(message);
                final String logMessage = sb.toString();
                logger.warn(logMessage);
            }
        }

        public void error(final Locator[] locators, final String message, final Exception exception) {
            final LocationData locationData = locators.length > 0 ? new LocationData(locators[0]) : null;
            throw new ValidationException(message, exception, locationData);
        }

        public InputSource resolveEntity(final String pid, final String sid) throws SAXException, IOException {
            final URL url = URLFactory.createURL(baseURI, sid);
            schemaInfo.addInclude(url);

            final String urlString = url.toString();
            return XMLUtils.ENTITY_RESOLVER.resolveEntity("", urlString);
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

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(final Object other) {
            final boolean ret;
            if (other instanceof SchemaKey) {
                final SchemaKey rhs = (SchemaKey) other;
                ret = url.equals(rhs.url);
            } else {
                ret = false;
            }
            return ret;
        }

        @Override
        public void toXML(ContentHandlerHelper helper, Object validities) {
            helper.element("url", new String[] { "class", getClazz().getName(), "validity", (validities != null) ? validities.toString() : null, "url", url.toExternalForm() });
        }
    }

    private static class SchemaInfo {

        private final ArrayList<URL> includes = new ArrayList<URL>(0);
        private final ArrayList<Long> modTimes = new ArrayList<Long>(0);
        private Grammar grammar;

        void addInclude(final URL url) throws IOException {
            // Get the time first. This way if there's a problem the array lengths will remain the same.
            final Long lastModified = NetUtils.getLastModifiedAsLong(url);
            includes.add(url);
            modTimes.add(lastModified);
        }

        boolean includesUpToDate() {
            boolean ret = true;
            final int size = includes.size();
            for (int i = 0; ret && i < size; i++) {
                final URL url = includes.get(i);
                try {
                    final Long lastModified = NetUtils.getLastModifiedAsLong(url);
                    final Long lastTime = modTimes.get(i);
                    ret = lastModified.equals(lastTime);
                } catch (final IOException e) {
                    // We won't propagate here. Reason is that while an include may be missing it may just be the case
                    // that it isn't included anymore _and_ it has been removed. So, we return false and then on a
                    // reparse we will find out the truth.
                    ret = false;
                }
            }
            return ret;
        }

        void setGrammar(final Grammar grammar) {
            this.grammar = grammar;
        }

        Grammar getGrammar() {
            return grammar;
        }
    }

    private static class ValidationContext implements IDContextProvider2 {

        private Element currentElement;

        public void setCurrentElement(Element currentElement) {
            this.currentElement = currentElement;
        }

        public String resolveNamespacePrefix(final String prefix) {
            return Dom4jUtils.getNamespaceContext(currentElement).get(prefix);
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
        final String newErrorMessage;
        if (errMsg == null) {
            // Looks like if n is an element and errMsg == null then the problem is missing
            // character data.  No idea why MSV doesn't just give us the error msg itself.
            newErrorMessage = "Missing character data.";
        } else {
            newErrorMessage = errMsg;
        }
        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug("schema", "validation error", "error", newErrorMessage);
        InstanceData.addSchemaError(element);
    }

    private void addSchemaError(final Attribute attribute, final String schemaError) {
        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug("schema", "validation error", "error", schemaError);
        InstanceData.addSchemaError(attribute);
    }

    private boolean handleIDErrors(final IDConstraintChecker icc) {
        boolean isValid = true;
        for (ErrorInfo errorInfo = icc.clearErrorInfo(); errorInfo != null; errorInfo = icc.clearErrorInfo()) {
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("schema", "validation error", "error", errorInfo.message);
            addSchemaError(errorInfo.element, errorInfo.message);
            isValid = false;
        }
        return isValid;
    }

    private boolean validateElement(final Element element, final Acceptor acceptor, final IDConstraintChecker icc, final boolean isReportErrors) {

        boolean isElementValid = true;

        // Create StartTagInfo
        final StartTagInfo startTagInfo;
        {
            final String uri = element.getNamespaceURI();
            final String name = element.getName();
            final String qName = element.getQualifiedName();
            final List attributesList = element.attributes();
            final AttributesImpl attributes = new AttributesImpl();

            for (Object anAttributesList: attributesList) {
                final Attribute attribute = (Attribute) anAttributesList;
                final String attributeURI = attribute.getNamespaceURI();
                final String attributeName = attribute.getName();
                final String attributeQName = attribute.getQualifiedName();
                final String attributeValue = attribute.getValue();
                attributes.addAttribute(attributeURI, attributeName, attributeQName, null, attributeValue);
            }
            validationContext.setCurrentElement(element);
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
                    isElementValid = false;
                } else {
                    return false;
                }
            }
            childAcceptor = tempChildAcceptor;
        }

        // Handle id errors
        if (icc != null && isReportErrors) {
            icc.onNextAcceptorReady(startTagInfo, childAcceptor, element);
            isElementValid &= handleIDErrors(icc);
        }

        // Validate children
        final DatatypeRef datatypeRef = new DatatypeRef();
        final boolean childrenValid = validateChildren(element, childAcceptor, startTagInfo, icc, datatypeRef, isReportErrors);
        if (!childrenValid) {
            if (isReportErrors)
                isElementValid = false;
            else
                return false;
        }

        // TODO: MSV doesn't allow getting the type if validity check fails. However, we would like to obtain datatype validity in XForms.
        if (!childAcceptor.isAcceptState(null)) {
            if (isReportErrors) {
                childAcceptor.isAcceptState(stringRef);
                addSchemaError(element, stringRef.str);
                isElementValid = false;
            } else {
                return false;
            }
        } else if (datatypeRef.types != null && datatypeRef.types.length > 0) {
            // This element is valid and has at least one assigned datatype

            // Attempt to set datatype name
            final Datatype datatype = datatypeRef.types[0];
            if (datatype instanceof XSDatatype) {
                final XSDatatype xsDatatype = (XSDatatype) datatype;
                final String dataTypeURI = xsDatatype.getNamespaceUri();
                final String dataTypeName = xsDatatype.getName();

                if (dataTypeName != null && !dataTypeName.equals(""))
                    InstanceData.setSchemaType(element, QName.get(dataTypeName, "", dataTypeURI));
            }
        }

        // Handle id errors
        if (icc != null && isReportErrors) {
            icc.endElement(element, datatypeRef.types);
            isElementValid &= handleIDErrors(icc);
        }

        // Get back to parent acceptor
        if (!acceptor.stepForward(childAcceptor, null)) {
            if (isReportErrors) {
                acceptor.stepForward(childAcceptor, stringRef);
                addSchemaError(element, stringRef.str);
                isElementValid = false;
            } else {
                return false;
            }
        }

        if (isReportErrors) {
            // Element may be invalid or not
            return isElementValid;
        } else {
            // This element is valid
            return true;
        }
    }

    /**
     * Validate an element following the XML Schema "lax" mode.
     *
     * @param element   element to validate
     */
    private boolean validateElementLax(final Element element) {

        final String elementURI;
        final String elementName;

        // NOTE: We do some special processing for xsi:type to find if there is a type declared for it. If not, we do
        // lax processing. However, it is not clear whether we should apply lax processing in this case or not. Maybe if
        // an xsi:type is specified and not found, the element should just be invalid.
        // TODO: should pass true?
        final QName xsiType = Dom4jUtils.extractAttributeValueQName(element, XMLConstants.XSI_TYPE_QNAME, false);
        if (xsiType != null) {
            // Honor xsi:type
            elementURI = xsiType.getNamespaceURI();
            elementName = xsiType.getName();
        } else {
            // Use element name
            elementURI = element.getNamespaceURI();
            elementName = element.getName();
        }

        boolean isValid = true;
        {
            // Find expression for element type
            final Expression expression;
            {
                // Find schema for type namespace
                final XMLSchemaSchema schema = ((XMLSchemaGrammar) schemaGrammar).getByNamespace(elementURI);
                if (schema != null) {
                    // Try to find the expression in the schema
                    final ElementDeclExp elementDeclExp = schema.elementDecls.get(elementName);
                    if (elementDeclExp != null) {
                        // Found element type
                        expression = elementDeclExp;
                    } else if (xsiType != null) {
                        // Try also complex type
                        expression = schema.complexTypes.get(elementName);
                    } else {
                        // No type found
                        expression = null;
                    }
                } else {
                    // No schema so no expression
                    expression = null;
                }
            }

            if (expression != null) {
                // Found type for element, so validate element
                final Acceptor acceptor = documentDeclaration.createAcceptor();
                isValid &= validateElement(element, acceptor, null, true);
            } else {
                // Element does not have type, so try to validate attributes and children elements

                // Attributes
                if (false) {
                    // TODO: find out way of validating an attribute only
                    // TODO: should we also look at schema.attributeGroups?
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
                    isValid &= validateElementLax(childElement);
                }
            }
        }
        return isValid;
    }

    /**
     * Note that all of the attributes of element should be in startTagInfo.attributes. If they are out of sync it break
     * the ability to access the attributes by index.
     */
    private boolean validateChildren(final Element element, final Acceptor acceptor, final StartTagInfo startTagInfo,
                                     final IDConstraintChecker icc, final DatatypeRef datatypeRef, final boolean isReportErrors) {

        boolean isElementChildrenValid = true;

        // Validate attributes
        final StringRef stringRef = new StringRef();
        {
            final DatatypeRef attributeDatatypeRef = new DatatypeRef();
            final int end = startTagInfo.attributes.getLength();
            for (int i = 0; i < end; i++) {
                final String uri = startTagInfo.attributes.getURI(i);

                final String name = startTagInfo.attributes.getLocalName(i);
                final String qName = startTagInfo.attributes.getQName(i);
                final String value = startTagInfo.attributes.getValue(i);

                if (!acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, null, attributeDatatypeRef)) {
                    if (isReportErrors) {
                        final Attribute attribute = element.attribute(i);
                        acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, stringRef, null);
                        addSchemaError(attribute, stringRef.str);
                        isElementChildrenValid = false;
                    } else {
                        return false;
                    }
                }
                final Attribute attribute = element.attribute(i);
                if (icc != null && isReportErrors) {
                    icc.feedAttribute(acceptor, attribute, attributeDatatypeRef.types);
                    isElementChildrenValid &= handleIDErrors(icc);
                }
            }

            if (!acceptor.onEndAttributes(startTagInfo, null)) {
                if (isReportErrors) {
                    acceptor.onEndAttributes(startTagInfo, stringRef);
                    addSchemaError(element, stringRef.str);
                    isElementChildrenValid = false;
                } else {
                    return false;
                }
            }
        }

        // Get string care level here like in MSV Verifier.java
        final int stringCareLevel = acceptor.getStringCareLevel();

        // Validate children elements
        for (final Iterator iterator = element.elementIterator(); iterator.hasNext();) {
            final Element childElement = (Element) iterator.next();
            final boolean isChildElementValid = validateElement(childElement, acceptor, icc, isReportErrors);
            if (!isChildElementValid) {
                if (isReportErrors) {
                    isElementChildrenValid = false;
                } else {
                    return false;
                }
            }
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
                            isElementChildrenValid = false;
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
                            isElementChildrenValid = false;
                        } else {
                            return false;
                        }
                    }
                    break;
                }
        }

        if (isReportErrors) {
            // Element children may be invalid or not
            return isElementChildrenValid;
        } else {
            // The element children are valid
            return true;
        }
    }

    /**
     * Load XForms model schemas.
     *
     * @param containingDocument    current document
     */
    public void loadSchemas(XFormsContainingDocument containingDocument) {

        // Check for external schema
        if (schemaURIs != null && schemaURIs.length > 0) {
            // Resolve URL
            // NOTE: We do not support "optimized" access here, we always use an URL, because loadGrammar() wants a URL
            final String resolvedURLString = XFormsUtils.resolveServiceURL(containingDocument, modelElement, schemaURIs[0],
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE);

            // Load associated grammar
            schemaGrammar = loadCacheGrammar(resolvedURLString);
        }

        // Check for inline schema
        if (schemaElements != null && schemaElements.size() > 0) {
            schemaGrammar = loadInlineGrammar(null, schemaElements.get(0)); // TODO: specify baseURI
        }
    }

    /**
     * Load and cache a Grammar for a given schema URI.
     */
    private Grammar loadCacheGrammar(final String schemaURI) {
        try {
            final URL url = URLFactory.createURL(schemaURI);
            final Long modificationTime = NetUtils.getLastModifiedAsLong(url);

            final Cache cache = ObjectCache.instance();
            final SchemaKey schemaKey = new SchemaKey(url);

            final SchemaInfo schemaInfo;
            {
                final Object cached = cache.findValid(schemaKey, modificationTime);
                schemaInfo = cached == null ? null : (SchemaInfo) cached;
            }

            // Grammar is thread safe while REDocumentDeclaration is not so cache grammar
            // instead of REDocumentDeclaration
            final Grammar grammar;
            if (schemaInfo == null || !schemaInfo.includesUpToDate()) {
                final SchemaInfo newSchemaInfo = new SchemaInfo();

                final InputSource is = XMLUtils.ENTITY_RESOLVER.resolveEntity("", schemaURI);
                final MSVGrammarReaderController controller = new MSVGrammarReaderController(schemaURI, newSchemaInfo);
                final SAXParserFactory factory = XMLUtils.getSAXParserFactory(XMLUtils.ParserConfiguration.XINCLUDE_ONLY);

                grammar = GrammarLoader.loadSchema(is, controller, factory);
                newSchemaInfo.setGrammar(grammar);
                cache.add(schemaKey, modificationTime, newSchemaInfo);
            } else {
                grammar = schemaInfo.getGrammar();
            }
            return grammar;
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(schemaURI, -1, -1, "loading schema from URI"));
        }
    }

    /**
     * Load an inline schema.
     *
     * @param baseURI               URI to resolve external dependencies
     * @param schemaElement         root element of the schema
     * @return
     */
//    private Grammar loadInlineGrammar(final String baseURI, final NodeInfo schemaElementInfo) {
    private Grammar loadInlineGrammar(final String baseURI, final Element schemaElement) {
        final SchemaInfo newSchemaInfo = new SchemaInfo(); // used for resolving external dependencies
        // TODO: Use SchemaInfo to cache dependencies if any
        final MSVGrammarReaderController controller = new MSVGrammarReaderController(baseURI, newSchemaInfo);
        final SAXParserFactory saxParserFactory = XMLUtils.getSAXParserFactory(XMLUtils.ParserConfiguration.PLAIN);
        final XMLSchemaReader reader = new XMLSchemaReader(controller, saxParserFactory);

//        TransformerUtils.writeTinyTree(schemaElementInfo, reader);
        // TODO: We create an entirely new dom4j document here because otherwise the transformation picks the whole document
        TransformerUtils.writeDom4j(Dom4jUtils.createDocumentCopyParentNamespaces(schemaElement), reader);

        return reader.getResult();
    }

    /**
     * Apply schema validation to an instance. The instance may content a hint specifying whether to perform "lax",
     * "strict", or "skip" validation.
     *
     * @param instance          instance to validate
     */
    public boolean validateInstance(XFormsInstance instance) {
        if (schemaGrammar != null) {

            // Create REDocumentDeclaration if needed
            if (documentDeclaration == null) {
                documentDeclaration = createDocumentDeclaration(schemaGrammar);
            }

            // Get validation mode ("lax" is the default)
            boolean isValid = true;
            if (instance.isLaxValidation()) {
                // Lax validation
                final Element instanceRootElement = instance.getDocument().getRootElement();
                isValid &= validateElementLax(instanceRootElement);
            } else if (instance.isStrictValidation()) {
                // Strict validation
                final Acceptor acceptor = documentDeclaration.createAcceptor();
                final Element instanceRootElement = instance.getDocument().getRootElement();
                final IDConstraintChecker idConstraintChecker = new IDConstraintChecker();

                isValid &= validateElement(instanceRootElement, acceptor, idConstraintChecker, true);
                idConstraintChecker.endDocument();
                isValid &= handleIDErrors(idConstraintChecker);
            } else {
                // Skip validation
            }
            return isValid;
        } else {
            return true;
        }
    }

    /**
     * Check whether a node's value satisfies a simple schema type definition given by namespace URI and local name.
     *
     * @param value                 value to validate
     * @param typeNamespaceURI      namespace URI of the type ("" if no namespace)
     * @param typeLocalname         local name of the type
     * @param typeQName             QName of type type (for error handling)
     * @param locationData          LocationData to use in case of error
     * @return                      validation error message, null if no error
     */
    public String validateDatatype(String value, String typeNamespaceURI, String typeLocalname, String typeQName, LocationData locationData) {

        if (typeNamespaceURI == null)
            typeNamespaceURI = "";

        // Create REDocumentDeclaration if needed
        if (documentDeclaration == null) {
            documentDeclaration = createDocumentDeclaration(schemaGrammar);
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
                        if (complexTypeExpression.simpleBaseType != null) {
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
     * Create an REDocumentDeclaration.
     *
     * @param grammar   Grammar to use
     * @return          REDocumentDeclaration for that Grammar
     */
    private REDocumentDeclaration createDocumentDeclaration(Grammar grammar) {
        if (grammar instanceof XMLSchemaGrammar)
            return new XSREDocDecl((XMLSchemaGrammar) grammar);
        else
            return new REDocumentDeclaration(grammar);
    }

    /**
     * Return the schema URIs specified on the model.
     *
     * @return  array of schema URIs specified on the model, or null if none
     */
    public String[] getSchemaURIs() {
        return schemaURIs;
    }
}
