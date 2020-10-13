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
package org.orbeon.oxf.xforms.model;

import org.orbeon.datatypes.LocationData;
import org.orbeon.dom.Attribute;
import org.orbeon.dom.Element;
import org.orbeon.dom.Node;
import org.orbeon.dom.QName;
import org.orbeon.msv.datatype.xsd.DatatypeFactory;
import org.orbeon.msv.datatype.xsd.XSDatatype;
import org.orbeon.msv.grammar.Expression;
import org.orbeon.msv.grammar.Grammar;
import org.orbeon.msv.grammar.IDContextProvider2;
import org.orbeon.msv.grammar.xmlschema.*;
import org.orbeon.msv.reader.util.GrammarLoader;
import org.orbeon.msv.reader.xmlschema.XMLSchemaReader;
import org.orbeon.msv.relaxng.datatype.Datatype;
import org.orbeon.msv.relaxng.datatype.DatatypeException;
import org.orbeon.msv.util.DatatypeRef;
import org.orbeon.msv.util.StartTagInfo;
import org.orbeon.msv.util.StringRef;
import org.orbeon.msv.verifier.Acceptor;
import org.orbeon.msv.verifier.regexp.ExpressionAcceptor;
import org.orbeon.msv.verifier.regexp.REDocumentDeclaration;
import org.orbeon.msv.verifier.regexp.SimpleAcceptor;
import org.orbeon.msv.verifier.regexp.StringToken;
import org.orbeon.msv.verifier.regexp.xmlschema.XSAcceptor;
import org.orbeon.msv.verifier.regexp.xmlschema.XSREDocDecl;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OrbeonLocationException;
import org.orbeon.oxf.externalcontext.URLRewriter$;
import org.orbeon.oxf.processor.validation.SchemaValidationException;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.msv.IDConstraintChecker;
import org.orbeon.oxf.xforms.schema.MSVGrammarReaderController;
import org.orbeon.oxf.xforms.schema.SchemaDependencies;
import org.orbeon.oxf.xforms.schema.SchemaInfo;
import org.orbeon.oxf.xforms.schema.SchemaKey;
import org.orbeon.oxf.xml.ParserConfiguration;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLParsing;
import org.orbeon.oxf.xml.dom.Extensions;
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData;
import org.orbeon.xforms.XFormsNames;
import org.orbeon.xforms.runtime.ErrorInfo;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;
import scala.Option;

import javax.xml.parsers.SAXParserFactory;
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
    public static final org.slf4j.Logger logger = LoggerFactory.createLoggerJava(XFormsModelSchemaValidator.class);

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
        final String schemaAttribute = modelElement.attributeValue(XFormsNames.SCHEMA_QNAME());
        if (schemaAttribute != null)
            this.schemaURIs = org.apache.commons.lang3.StringUtils.split(MarkupUtils.encodeHRRI(schemaAttribute, false));

        // Check for inline schemas
        // "3.3.1 The model Element [...] xs:schema elements located inside the current model need not be listed."
        for (final Element schemaElement: modelElement.jElements(XMLConstants.XML_SCHEMA_QNAME())) {

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

    private static class ValidationContext implements IDContextProvider2 {

        private Element currentElement;

        public void setCurrentElement(Element currentElement) {
            this.currentElement = currentElement;
        }

        public String resolveNamespacePrefix(final String prefix) {
            return currentElement.allInScopeNamespacesAsNodes().apply(prefix).prefix();
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
        if (indentedLogger.debugEnabled())
            indentedLogger.logDebug("schema", "validation error", "error", newErrorMessage);
        InstanceData.addSchemaError(element);
    }

    private void addSchemaError(final Attribute attribute, final String schemaError) {
        if (indentedLogger.debugEnabled())
            indentedLogger.logDebug("schema", "validation error", "error", schemaError);
        InstanceData.addSchemaError(attribute);
    }

    private boolean handleIDErrors(final IDConstraintChecker icc) {
        boolean isValid = true;
        for (ErrorInfo errorInfo = icc.clearErrorInfo(); errorInfo != null; errorInfo = icc.clearErrorInfo()) {
            if (indentedLogger.debugEnabled())
                indentedLogger.logDebug("schema", "validation error", "error", errorInfo.message());
            addSchemaError(errorInfo.element(), errorInfo.message());
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
            final List attributesList = element.jAttributes();
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
        } else {
            // Attempt to set datatype name
            setDataType(datatypeRef, element);
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

    private void setDataType(DatatypeRef datatypeRef, Node node) {
        if (datatypeRef.types != null && datatypeRef.types.length > 0) {
            // This element is valid and has at least one assigned datatype

            // Attempt to set datatype name
            final Datatype datatype = datatypeRef.types[0];
            if (datatype instanceof XSDatatype) {
                final XSDatatype xsDatatype = (XSDatatype) datatype;
                final String dataTypeURI = xsDatatype.getNamespaceUri();
                final String dataTypeName = xsDatatype.getName();

                if (dataTypeName != null && !dataTypeName.equals(""))
                    InstanceData.setSchemaType(node, QName.apply(dataTypeName, "", dataTypeURI));
            }
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
        final QName xsiType = Extensions.resolveAttValueQNameJava(element, XMLConstants.XSI_TYPE_QNAME(), false);
        if (xsiType != null) {
            // Honor xsi:type
            elementURI = xsiType.namespace().uri();
            elementName = xsiType.localName();
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
                    final List attributesList = element.jAttributes();
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
                for (final Iterator iterator = element.jElementIterator(); iterator.hasNext();) {
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

                final Attribute attribute = element.attribute(i);

                if (!acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, null, attributeDatatypeRef)) {
                    if (isReportErrors) {
                        acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, stringRef, null);
                        addSchemaError(attribute, stringRef.str);
                        isElementChildrenValid = false;
                    } else {
                        return false;
                    }
                }

                // Attempt to set datatype name
                setDataType(attributeDatatypeRef, attribute);

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
        for (final Iterator iterator = element.jElementIterator(); iterator.hasNext();) {
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
                    final String trimmed = StringUtils.trimAllToEmpty(text);
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
            final String resolvedURLString = org.orbeon.oxf.xforms.XFormsUtils.resolveServiceURL(containingDocument, modelElement, schemaURIs[0],
                    URLRewriter$.MODULE$.REWRITE_MODE_ABSOLUTE());

            // Load associated grammar
            schemaGrammar = loadCacheGrammar(containingDocument, resolvedURLString);
        }

        // Check for inline schema
        if (schemaElements != null && schemaElements.size() > 0) {
            schemaGrammar = loadInlineGrammar(containingDocument, schemaElements.get(0)); // TODO: specify baseURI
        }
    }

    /**
     * Load and cache a Grammar for a given schema URI.
     */
    private Grammar loadCacheGrammar(final XFormsContainingDocument containingDocument, final String absoluteSchemaURL) {
        try {
            final URL url = URLFactory.createURL(absoluteSchemaURL);
            final long modificationTime = NetUtils.getLastModified(url); // can be 0 if unknown

            final Cache cache = ObjectCache.instance();
            final SchemaKey schemaKey = new SchemaKey(absoluteSchemaURL);

            final SchemaInfo schemaInfo;
            {
                final Object cached = cache.findValid(schemaKey, modificationTime);
                schemaInfo = cached == null ? null : (SchemaInfo) cached;
            }

            // Grammar is thread safe while REDocumentDeclaration is not so cache grammar
            // instead of REDocumentDeclaration
            final Grammar grammar;
            if (schemaInfo == null || ! schemaInfo.dependencies().areIncludesUnchanged()) {
                final InputSource is                        = XMLParsing.EntityResolver().resolveEntity("", absoluteSchemaURL);
                final SchemaDependencies dependencies       = new SchemaDependencies();
                final MSVGrammarReaderController controller = new MSVGrammarReaderController(containingDocument, dependencies, Option.apply(absoluteSchemaURL));
                final SAXParserFactory factory              = XMLParsing.getSAXParserFactory(ParserConfiguration.XIncludeOnly());

                grammar = GrammarLoader.loadSchema(is, controller, factory);

                cache.add(schemaKey, modificationTime, new SchemaInfo(grammar, dependencies));
            } else {
                grammar = schemaInfo.grammar();
            }
            return grammar;
        } catch (Exception e) {
            throw OrbeonLocationException.wrapException(e, XmlExtendedLocationData.apply(absoluteSchemaURL, -1, -1, "loading schema from URI"));
        }
    }

    /**
     * Load an inline schema.
     */
    private Grammar loadInlineGrammar(final XFormsContainingDocument containingDocument, final Element schemaElement) {
        final SchemaDependencies dependencies = new SchemaDependencies();
        // TODO: Use SchemaDependencies to cache dependencies if any
        final MSVGrammarReaderController controller = new MSVGrammarReaderController(containingDocument, dependencies, Option.<String>apply(null));
        final SAXParserFactory saxParserFactory = XMLParsing.getSAXParserFactory(ParserConfiguration.Plain());
        final XMLSchemaReader reader = new XMLSchemaReader(controller, saxParserFactory);

//        TransformerUtils.writeTinyTree(schemaElementInfo, reader);
        // TODO: We create an entirely new dom4j document here because otherwise the transformation picks the whole document
        TransformerUtils.writeDom4j(Extensions.createDocumentCopyParentNamespacesJava(schemaElement, false), reader);

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
            if (instance.instance().isLaxValidation()) {
                // Lax validation
                final Element instanceRootElement = instance.underlyingDocumentOpt().get().getRootElement();
                isValid &= validateElementLax(instanceRootElement);
            } else if (instance.instance().isStrictValidation()) {
                // Strict validation
                final Acceptor acceptor = documentDeclaration.createAcceptor();
                final Element instanceRootElement = instance.underlyingDocumentOpt().get().getRootElement();
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
                    throw new SchemaValidationException("Built-in schema type not found: " + typeLocalname, locationData);
                }
            } else {
                // Find schema for type namespace
                final XMLSchemaSchema schema = ((XMLSchemaGrammar) schemaGrammar).getByNamespace(typeNamespaceURI);
                if (schema == null)
                    throw new SchemaValidationException("No schema found for namespace: " + typeNamespaceURI, locationData);

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
                            throw new SchemaValidationException("Simple type or complex type with simple content required for type: " + typeQName, locationData);
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
                                throw new SchemaValidationException("Simple type or complex type with simple content required for type: " + typeQName, locationData);
                            }
                        } else {
                            // XForms mandates simple types or complex types with simple content
                            throw new SchemaValidationException("Simple type or complex type with simple content required for type: " + typeQName, locationData);
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
