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
package org.orbeon.oxf.transformer.xupdate;

import org.dom4j.*;
import org.jaxen.NamespaceContext;
import org.jaxen.SimpleNamespaceContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.transformer.xupdate.statement.*;
import org.orbeon.oxf.transformer.xupdate.statement.Attribute;
import org.orbeon.oxf.transformer.xupdate.statement.Error;
import org.orbeon.oxf.transformer.xupdate.statement.Namespace;
import org.orbeon.oxf.transformer.xupdate.statement.Text;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.orbeon.oxf.xml.dom4j.NonLazyUserDataElement;

import javax.xml.transform.sax.TemplatesHandler;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TemplatesHandlerImpl extends LocationSAXContentHandler implements TemplatesHandler  {

    public javax.xml.transform.Templates getTemplates() {
        Document xupdateDocument = getDocument();
        return new TemplatesImpl(parseStatements(xupdateDocument.getRootElement().elements()));
    }

    public void setSystemId(String systemID) {
    }

    public String getSystemId() {
        return null;
    }

    private Statement[] parseStatements(List nodes) {
        List statements = new ArrayList();
        for (Iterator i = nodes.iterator(); i.hasNext();) {
            Node node = (Node) i.next();
            if (node.getNodeType() == Node.TEXT_NODE) {
                if (! "".equals(node.getText().trim()))
                    statements.add(new Text(node.getText().trim()));
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                NamespaceContext namespaceContext = new SimpleNamespaceContext(Dom4jUtils.getNamespaceContext(element));
                if (XUpdateConstants.XUPDATE_NAMESPACE_URI.equals(element.getNamespaceURI())) {
                    if (element.getName().equals("remove")) {
                        statements.add(new Remove((LocationData) element.getData(), element.attributeValue("select"), namespaceContext));
                    } else if (element.getName().equals("update")) {
                        statements.add(new Update((LocationData) element.getData(), element.attributeValue("select"), namespaceContext,
                                parseStatements(element.content())));
                    } else if (element.getName().equals("append")) {
                        statements.add(new Append((LocationData) element.getData(), element.attributeValue("select"), namespaceContext,
                                element.attributeValue("child"),
                                parseStatements(element.content())));
                    } else if (element.getName().equals("insert-before")) {
                        statements.add(new InsertBefore((LocationData) element.getData(), element.attributeValue("select"), namespaceContext,
                                parseStatements(element.content())));
                    } else if (element.getName().equals("insert-after")) {
                        statements.add(new InsertAfter((LocationData) element.getData(), element.attributeValue("select"), namespaceContext,
                                parseStatements(element.content())));
                    } else if (element.getName().equals("for-each")) {
                        statements.add(new ForEach((LocationData) element.getData(), element.attributeValue("select"), namespaceContext,
                                parseStatements(element.content())));
                    } else if (element.getName().equals("while")) {
                        statements.add(new While((LocationData) element.getData(), element.attributeValue("select"), namespaceContext,
                                parseStatements(element.content())));
                    } else if (element.getName().equals("value-of")) {
                        statements.add(new ValueOf((LocationData) element.getData(), element.attributeValue("select"), namespaceContext));
                    } else if (element.getName().equals("copy-of")) {
                        statements.add(new CopyOf((LocationData) element.getData(), element.attributeValue("select"), namespaceContext));
                    } else if (element.getName().equals("node-set")) {
                        statements.add(new NodeSet((LocationData) element.getData(), element.attributeValue("select"), namespaceContext));
                    } else if (element.getName().equals("attribute")) {
                        statements.add(new Attribute((LocationData) element.getData(), parseQName(element),
                                parseStatements(element.content())));
                    } else if (element.getName().equals("namespace")) {
                        statements.add(new Namespace((LocationData) element.getData(), element.attributeValue("name"),
                                element.attributeValue("select"), namespaceContext, parseStatements(element.content())));
                    } else if (element.getName().equals("element")) {
                        statements.add(new DynamicElement((LocationData) element.getData(), parseQName(element),
                                parseStatements(element.content())));
                    } else if (element.getName().equals("if")) {
                        statements.add(new If((LocationData) element.getData(), element.attributeValue("test"), namespaceContext,
                                parseStatements(element.content())));
                    } else if (element.getName().equals("choose")) {
                        List whenTests = new ArrayList();
                        List whenNamespaceContext = new ArrayList();
                        List whenStatements = new ArrayList();
                        for (Iterator j = element.elements("when").iterator(); j.hasNext();) {
                            Element whenElement = (Element) j.next();
                            whenTests.add(whenElement.attributeValue("test"));
                            whenNamespaceContext.add(new SimpleNamespaceContext(Dom4jUtils.getNamespaceContext(whenElement)));
                            whenStatements.add(parseStatements(whenElement.content()));
                        }
                        Element otherwiseElement = element.element("otherwise");
                        statements.add(new Choose((LocationData) element.getData(), (String[]) whenTests.toArray(new String[whenTests.size()]),
                                (NamespaceContext[]) whenNamespaceContext.toArray(new NamespaceContext[whenNamespaceContext.size()]),
                                (Statement[][]) whenStatements.toArray(new Statement[whenStatements.size()][]),
                                otherwiseElement == null ? null : parseStatements(otherwiseElement.content())));
                    } else if (element.getName().equals("variable")) {
                        statements.add(new Variable((LocationData) element.getData(), parseQName(element), element.attributeValue("select"),
                                namespaceContext, parseStatements(element.content())));
                    } else if (element.getName().equals("assign")) {
                        statements.add(new Assign((LocationData) element.getData(), parseQName(element), element.attributeValue("select"),
                                namespaceContext, parseStatements(element.content())));
                    } else if (element.getName().equals("function")) {
                        statements.add(new Function((LocationData) element.getData(), parseQName(element), parseStatements(element.content())));
                    } else if (element.getName().equals("param")) {
                        statements.add(new Param((LocationData) element.getData(), parseQName(element), element.attributeValue("select"),
                                namespaceContext, parseStatements(element.content())));
                    } else if (element.getName().equals("message")) {
                        statements.add(new Message((LocationData) element.getData(), parseStatements(element.content())));
                    } else if (element.getName().equals("error")) {
                        statements.add(new Error((LocationData) element.getData(), parseStatements(element.content())));
                    } else {
                        throw new ValidationException("Unsupported XUpdate element '"
                                + element.getQualifiedName() + "'", (LocationData) element.getData());
                    }
                } else {
                    Element staticElement = new NonLazyUserDataElement(element.getQName());
                    List childNodes = new ArrayList();
                    for (Iterator j = element.attributes().iterator(); j.hasNext();)
                        staticElement.add((org.dom4j.Attribute) ((org.dom4j.Attribute) j.next()).clone());
                    for (Iterator j = element.content().iterator(); j.hasNext();) {
                        Node child = (Node) j.next();
                        if (child instanceof org.dom4j.Namespace) {
                            staticElement.add((Node) child.clone());
                        } else {
                            childNodes.add(child);
                        }
                    }
                    statements.add(new StaticElement((LocationData) element.getData(),
                            staticElement, parseStatements(childNodes)));
                }
            } else if (node.getNodeType() == Node.NAMESPACE_NODE) {
                // Ignore namespace declarations
            } else {
                throw new OXFException("Unsupported node: " + node.getNodeTypeName());
            }
        }
        return (Statement[]) statements.toArray(new Statement[statements.size()]);
    }

    /**
     * Parse a name / namespace attributes of &lt;xu:element> and
     * &lt;xu:attribute>
     */
    private QName parseQName(Element element) {
        String name = element.attributeValue("name");
        String namespace = element.attributeValue("namespace");
        int columnPosition = name.indexOf(':');

        // Check syntax of qname
        if (columnPosition == 0 || columnPosition == name.length() - 1)
            throw new ValidationException("Invalid qname '" + name + "'", (LocationData) element.getData());

        if (columnPosition == -1 && namespace == null) {
            // Simple name
            return new QName(name);
        } else  if (columnPosition != -1 && namespace == null) {
            // Qualified name using namespace declaration in context
            String prefix = name.substring(0, columnPosition);
            String namespaceFromContext = (String) Dom4jUtils.getNamespaceContext(element).get(prefix);
            if (namespaceFromContext == null)
                throw new ValidationException("No namespace declared for prefix '" + prefix + "'",
                        (LocationData) element.getData());
            return new QName(name.substring(columnPosition + 1), new org.dom4j.Namespace(prefix, namespaceFromContext));
        } else if (columnPosition == -1 && namespace != null) {
            // Non-qualified name with namespace declaration
            return new QName(name, new org.dom4j.Namespace("", namespace));
        } else if (columnPosition != -1 && namespace != null) {
            // Qualified name using namespace specified
            return new QName(name.substring(columnPosition + 1),
                    new org.dom4j.Namespace(name.substring(0, columnPosition), namespace));
        } else {
            // This can't happen
            throw new OXFException("Invalid state");
        }
    }
}
