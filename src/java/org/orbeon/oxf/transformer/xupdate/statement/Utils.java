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
package org.orbeon.oxf.transformer.xupdate.statement;

import org.dom4j.Attribute;
import org.dom4j.*;
import org.dom4j.XPath;
import org.dom4j.util.UserDataDocumentFactory;
import org.jaxen.*;
import org.jaxen.dom4j.DocumentNavigator;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.transformer.xupdate.Closure;
import org.orbeon.oxf.transformer.xupdate.Statement;
import org.orbeon.oxf.transformer.xupdate.VariableContextImpl;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import java.util.*;

public class Utils {

    /**
     * Evalutate a sequence a statements, and insert the result of the
     * evaluation the given parent node at the given position.
     */
    public static void insert(LocationData locationData, Node parent, int position, Object toInsert) {
        List nodesToInsert = xpathObjectToDOM4JList(locationData, toInsert);
        if (parent instanceof Element)
            Collections.reverse(nodesToInsert);
        for (Iterator j = nodesToInsert.iterator(); j.hasNext();) {
            Object object = j.next();
            Node node = object instanceof String || object instanceof Number
                    ? UserDataDocumentFactory.getInstance().createText(object.toString())
                    : (Node) ((Node) object).clone();
            if (parent instanceof Element) {
                Element element = (Element) parent;
                if (node instanceof Attribute) {
                    element.attributes().add(node);
                } else {
                    element.content().add(position, node);
                }
            } else if (parent instanceof Attribute) {
                Attribute attribute = (Attribute) parent;
                attribute.setValue(attribute.getValue() + node.getText());
            } else if (parent instanceof Document) {
                if (! (node instanceof Element))
                    throw new ValidationException("Only an element can be at the root of a document", locationData);
                if (((Document) parent).getRootElement() != null)
                    throw new ValidationException("Document already has a root element", locationData);
                ((Document) parent).setRootElement((Element) node);
            } else {
                throw new ValidationException("Cannot insert into a node of type '" + parent.getClass() + "'", locationData);
            }
        }
    }

    /**
     * Evaluates an XPath expression
     */
    public static Object evaluate(final URIResolver uriResolver, Object context,
                                  final VariableContextImpl variableContext, final LocationData locationData,
                                  String select, NamespaceContext namespaceContext) {
        FunctionContext functionContext = new FunctionContext() {
            public org.jaxen.Function getFunction(final String namespaceURI,
                                        final String prefix,
                                        final String localName) {

                // Override document() and doc()
                if (/*namespaceURI == null &&*/ ("document".equals(localName) || "doc".equals(localName))) {
                    return new org.jaxen.Function() {
                        public Object call(Context jaxenContext, List args) {
                            try {
                                String url = (String) DocumentHelper.createXPath("string(.)").evaluate(args.get(0));
                                Source source = uriResolver.resolve(url, locationData.getSystemID());
                                if (! (source instanceof SAXSource))
                                    throw new ValidationException("Unsupported source type", locationData);
                                XMLReader xmlReader = ((SAXSource) source).getXMLReader();
                                LocationSAXContentHandler contentHandler = new LocationSAXContentHandler();
                                xmlReader.setContentHandler(contentHandler);
                                xmlReader.parse(new InputSource());
                                return contentHandler.getDocument();
                            } catch (Exception e) {
                                throw new ValidationException(e, locationData);
                            }
                        }
                    };
                } else if (/*namespaceURI == null &&*/ "get-namespace-uri-for-prefix".equals(localName)) {
                    return new org.jaxen.Function() {
                        public Object call(Context jaxenContext, List args) {
                            String prefix = (String) DocumentHelper.createXPath("string(.)").evaluate(args.get(0));
                            Element element = null;
                            if (args.get(1) instanceof List) {
                                List list = (List) args.get(1);
                                if (list.size() == 1)
                                    element = (Element) list.get(0);
                            } else if (args.get(1) instanceof Element) {
                                element = (Element) args.get(1);
                            }
                            if (element == null)
                                throw new ValidationException("An element is expected as the second argument " +
                                        "in get-namespace-uri-for-prefix()", locationData);
                            return element.getNamespaceForPrefix(prefix);
                        }
                    };
                } else if (/*namespaceURI == null &&*/ "distinct-values".equals(localName)) {
                    return new org.jaxen.Function() {
                        public Object call(Context jaxenContext, List args) {
                            List originalList = args.get(0) instanceof List ? (List) args.get(0)
                                    : Collections.singletonList(args.get(0));
                            List resultList = new ArrayList();
                            XPath stringXPath = DocumentHelper.createXPath("string(.)");
                            for (Iterator i = originalList.iterator(); i.hasNext();) {
                                Object item = (Object) i.next();
                                String itemString = (String) stringXPath.evaluate(item);
                                if (!resultList.contains(itemString))
                                    resultList.add(itemString);
                            }
                            return resultList;
                        }
                    };
                } else if (/*namespaceURI == null &&*/ "evaluate".equals(localName)) {
                    return new org.jaxen.Function() {
                        public Object call(Context jaxenContext, List args) {
                            try {
                                if (args.size() != 3) {
                                    try {
                                        return XPathFunctionContext.getInstance().getFunction(namespaceURI, prefix, localName);
                                    } catch (UnresolvableException e) {
                                        throw new ValidationException(e, locationData);
                                    }
                                } else {
                                    String xpathString = (String) DocumentHelper.createXPath("string(.)").evaluate(args.get(0));
                                    XPath xpath = DocumentHelper.createXPath(xpathString);
                                    Map namespaceURIs = new HashMap();
                                    List namespaces = (List) args.get(1);
                                    for (Iterator i = namespaces.iterator(); i.hasNext();) {
                                        org.dom4j.Namespace namespace = (org.dom4j.Namespace) i.next();
                                        namespaceURIs.put(namespace.getPrefix(), namespace.getURI());
                                    }
                                    xpath.setNamespaceURIs(namespaceURIs);
                                    return xpath.evaluate(args.get(2));
                                }
                            } catch (InvalidXPathException e) {
                                throw new ValidationException(e, locationData);
                            }
                        }
                    };
                } else if (/*namespaceURI == null &&*/ "tokenize".equals(localName)) {
                    return new org.jaxen.Function() {
                        public Object call(Context jaxenContext, List args) {
                            try {
                                String input = (String) DocumentHelper.createXPath("string(.)").evaluate(args.get(0));
                                String pattern = (String) DocumentHelper.createXPath("string(.)").evaluate(args.get(1));
                                List result = new ArrayList();
                                while (input.length() != 0) {
                                    int position = input.indexOf(pattern);
                                    if (position != -1) {
                                        result.add(input.substring(0, position));
                                        input = input.substring(position + 1);
                                    } else {
                                        result.add(input);
                                        input = "";
                                    }
                                }
                                return result;
                            } catch (InvalidXPathException e) {
                                throw new ValidationException(e, locationData);
                            }
                        }
                    };
                } else if (/*namespaceURI == null &&*/ "string-join".equals(localName)) {
                    return new org.jaxen.Function() {
                        public Object call(Context jaxenContext, List args) {
                            try {
                                List strings = (List) args.get(0);
                                String pattern = (String) DocumentHelper.createXPath("string(.)").evaluate(args.get(1));
                                StringBuffer result = new StringBuffer();
                                boolean isFirst = true;
                                for (Iterator i = strings.iterator(); i.hasNext();) {
                                    if (! isFirst) result.append(pattern); else isFirst = false;
                                    String item = (String) (String) DocumentHelper.createXPath("string(.)").evaluate(i.next());
                                    result.append(item);
                                }
                                return result.toString();
                            } catch (InvalidXPathException e) {
                                throw new ValidationException(e, locationData);
                            }
                        }
                    };
                } else if (/*namespaceURI == null &&*/ "reverse".equals(localName)) {
                    return new org.jaxen.Function() {
                        public Object call(Context jaxenContext, List args) {
                            try {
                                List result = new ArrayList((List) args.get(0));
                                Collections.reverse(result);
                                return result;
                            } catch (InvalidXPathException e) {
                                throw new ValidationException(e, locationData);
                            }
                        }
                    };
                } else {
                    try {
                        // Go through standard XPath functions
                        return XPathFunctionContext.getInstance().getFunction(namespaceURI, prefix, localName);
                    } catch (UnresolvableException e) {
                        // User-defined function
                        try {
                            final Closure closure = findClosure(variableContext.getVariableValue
                                    (namespaceURI, prefix, localName));
                            if (closure == null)
                                throw new ValidationException("'" + qualifiedName(prefix, localName)
                                        + "' is not a function", locationData);
                            return new org.jaxen.Function() {
                                public Object call(Context context, List args) {
                                    return closure.execute(args);
                                }
                            };
                        } catch (UnresolvableException e2) {
                            throw new ValidationException("Cannot invoke function '" +
                                    qualifiedName(prefix, localName) + "', no such function", locationData);
                        }
                    }
                }
            }

            private Closure findClosure(Object xpathObject) {
                if (xpathObject instanceof Closure) {
                    return (Closure) xpathObject;
                } else if (xpathObject instanceof List) {
                    for (Iterator i = ((List) xpathObject).iterator(); i.hasNext();) {
                        Closure closure = findClosure(i.next());
                        if (closure != null)
                            return closure;
                    }
                    return null;
                } else {
                    return null;
                }
            }
        };

        try {
            // Create XPath
            XPath xpath = DocumentHelper.createXPath(select);

            // Set variable, namespace, and function context
            if (context instanceof Context) {
                // Create a new context, as Jaxen may modify the current node in the context (is this a bug?)
                Context currentContext = (Context) context;
                Context newContext = new Context(new ContextSupport
                        (namespaceContext, functionContext, variableContext, DocumentNavigator.getInstance()));
                newContext.setNodeSet(currentContext.getNodeSet());
                newContext.setSize(currentContext.getSize());
                newContext.setPosition(currentContext.getPosition());
                context = newContext;
            } else {
                xpath.setVariableContext(variableContext);
                xpath.setNamespaceContext(namespaceContext);
                xpath.setFunctionContext(functionContext);
            }

            // Execute XPath
            return xpath.evaluate(context);
        } catch (Exception e) {
            throw new ValidationException(e, locationData);
        }
    }

    public static List evaluateToList(URIResolver uriResolver, Object context,
                                      VariableContextImpl variableContext, LocationData locationData,
                                      String select, NamespaceContext namespaceContext) {
        Object selected = Utils.evaluate(uriResolver, context, variableContext, locationData, select, namespaceContext);
        return xpathObjectToDOM4JList(locationData, selected);
    }

    public static List xpathObjectToDOM4JList(LocationData locationData, Object selected) {
        if (selected == null) {
            return Collections.EMPTY_LIST;
        } else if (selected instanceof String || selected instanceof Number) {
            org.dom4j.Text textNode = DocumentHelper.createText(selected.toString());
            return Arrays.asList(new org.dom4j.Text[] {textNode});
        } else if (selected instanceof Node) {
            return Arrays.asList(new Node[]{(Node) selected});
        } else if (selected instanceof List) {
            return (List) selected;
        } else if (selected instanceof Closure) {
            return Arrays.asList(new Closure[] {(Closure) selected});
        } else {
            throw new ValidationException("Unsupported type: " + selected.getClass().getName(), locationData);
        }
    }

    public static Object execute(URIResolver uriResolver, Object context,
                                 VariableContextImpl variableContext, Statement[] statements) {
        List result = new ArrayList();
        VariableContextImpl currentVariableContext = variableContext;
        for (int i = 0; i < statements.length; i++) {
            if (statements[i] instanceof Variable) {
                Variable variable = (Variable) statements[i];
                currentVariableContext = new VariableContextImpl(currentVariableContext,
                        variable.getName(), variable.execute(uriResolver, context, currentVariableContext));
            } else if (statements[i] instanceof Function) {
                Function function = (Function) statements[i];
                Closure closure = function.getClosure(uriResolver, context);
                currentVariableContext = new VariableContextImpl(currentVariableContext, function.getName(), closure);
                closure.setVariableContext(currentVariableContext);
            } else {
                Object statementResult = statements[i].execute(uriResolver, context, currentVariableContext);
                if (statementResult instanceof List) {
                    result.addAll((List) statementResult);
                } else {
                    result.add(statementResult);
                }
            }
        }
        return result.size() == 1 ? result.get(0) : result;
    }

    public static String xpathObjectToString(Object xpathObject) {
        List list = xpathObject instanceof List ? (List) xpathObject
                : Collections.singletonList(xpathObject);
        StringBuffer buffer = new StringBuffer();
        for (Iterator i = list.iterator(); i.hasNext();) {
            Object object = i.next();
            buffer.append(object instanceof Node ? Dom4jUtils.domToString((Node) object) : object.toString());
        }
        return buffer.toString();
    }

    public static String qualifiedName(String prefix, String localName) {
        return ("".equals(prefix) ? "" : prefix + ":") + localName;
    }


    public static Element getInsertPivot(LocationData locationData, String select,
                                         NamespaceContext namespaceContext,
                                         URIResolver uriResolver, Object context,
                                         VariableContextImpl variableContext) {
        Object insertPivotObject = Utils.evaluate(uriResolver, context, variableContext,
                locationData, select, namespaceContext);
        if (!(insertPivotObject instanceof Element))
            throw new ValidationException("Select expression must return an element", locationData);
        return (Element) insertPivotObject;
    }
}
