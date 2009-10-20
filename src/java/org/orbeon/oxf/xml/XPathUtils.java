/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xml;

import org.dom4j.InvalidXPathException;
import org.jaxen.*;
import org.jaxen.dom.DOMXPath;
import org.orbeon.oxf.common.OXFException;
import org.w3c.dom.Node;

import java.util.*;

/**
 * NOTE: This class is obsolete as it relies on Jaxen. All new XPath code uses Saxon and XPath 2.0.
 *
 * TODO: Migrate these methods and/or callers to use XPathCache instead.
 */
public class XPathUtils {

    /**
     * Apply the given XPath expression to the given node.
     * @return            Iterator over org.w3c.dom.Node objects.
     */
    public static Iterator selectIterator(org.dom4j.Node node, String expr) {
        return selectIterator(node, expr, Collections.EMPTY_MAP);
    }

    /**
     * Apply the given XPath expression to the given node.
     *
     * @param   prefixes  mapping of prefixes to namespace URIs for prefixes used in expr
     * @return            Iterator over org.w3c.dom.Node objects, never null
     */
    public static Iterator selectIterator(org.dom4j.Node node, String expr, Map prefixes) {
        try {
            org.dom4j.XPath path = node.createXPath(expr);
            path.setNamespaceContext(new SimpleNamespaceContext(prefixes));

            return new IteratorFilter(path.selectNodes(node).iterator(), org.dom4j.Namespace.class);
        } catch (InvalidXPathException e) {
            throw new OXFException(e);
        }
    }

    public static Iterator selectIterator(org.dom4j.Node node, String expr, Map prefixes, VariableContext variableContext, FunctionContext functionContext) {
        try {
            org.dom4j.XPath path = node.createXPath(expr);
            hookupPath(path, prefixes, variableContext, functionContext);
            return new IteratorFilter(path.selectNodes(node).iterator(), org.dom4j.Namespace.class);
        } catch (InvalidXPathException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Apply the given XPath expression to the given node.
     */
    public static org.dom4j.Node selectSingleNode(org.dom4j.Node node, String expr) {
        return selectSingleNode(node, expr, Collections.EMPTY_MAP);
    }

    /**
     * Apply the given XPath expression to the given node.
     *
     * @param prefixes  mapping of prefixes to namespace URIs for prefixes used in expr
     */
    public static Node selectSingleNode(Node node, String expr, Map prefixes) {
        try {
            XPath path = new DOMXPath(expr);
            path.setNamespaceContext(new SimpleNamespaceContext(prefixes));
            return (Node) path.selectSingleNode(node);
        } catch (JaxenException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Apply the given XPath expression to the given node.
     *
     * @param prefixes  mapping of prefixes to namespace URIs for prefixes used in expr
     */
    public static org.dom4j.Node selectSingleNode(org.dom4j.Node node, String expr, Map prefixes) {
        try {
            org.dom4j.XPath path = node.createXPath(expr);
            path.setNamespaceContext(new SimpleNamespaceContext(prefixes));
            return path.selectSingleNode(node);
        } catch (InvalidXPathException e) {
            throw new OXFException(e);
        }
    }

    public static String selectStringValue(org.dom4j.Node node, String expr) {
        return selectStringValue(node, expr, Collections.EMPTY_MAP, null, null);
    }

    public static String selectStringValueNormalize(Node node, String expr) {
        String result = selectStringValue(node, expr, Collections.EMPTY_MAP);
        if (result == null)
            return null;
        result = result.trim();
        if ("".equals(result))
            return null;
        return result;
    }

    public static String selectStringValueNormalize(org.dom4j.Node node, String expr) {
        String result = selectStringValue(node, expr, Collections.EMPTY_MAP, null, null);
        if (result == null)
            return null;
        result = result.trim();
        if ("".equals(result))
            return null;
        return result;
    }

    public static Object selectObjectValue(org.dom4j.Node node, String expr, Map prefixes, VariableContext variableContext, FunctionContext functionContext) {
        try {
            org.dom4j.XPath path = node.createXPath(expr);
            hookupPath(path, prefixes, variableContext, functionContext);
            return path.evaluate(node);
        } catch (InvalidXPathException e) {
            throw new OXFException(e);
        }
    }

    public static Object selectObjectValue(org.dom4j.Node node, String expr) {
        return selectObjectValue(node, expr, Collections.EMPTY_MAP, null, null);
    }

    /**
     * Select a value as a String.
     *
     * If the XPath expressions select an empty node set, return null.
     */
    public static String selectStringValue(Node node, String expr, Map prefixes) {
        try {
            XPath path = new DOMXPath(expr);
            path.setNamespaceContext(new SimpleNamespaceContext(prefixes));
            Object result = path.selectSingleNode(node);
            return (result == null || result instanceof String) ? (String) result :
                    new DOMXPath(".").stringValueOf(result);
        } catch (JaxenException e) {
            throw new OXFException(e);
        }
    }

    public static String selectStringValue(org.dom4j.Node node, String expr, Map prefixes, VariableContext variableContext, FunctionContext functionContext) {
        try {
            org.dom4j.XPath path = node.createXPath(expr);
            hookupPath(path, prefixes, variableContext, functionContext);
            Object result = path.evaluate(node);
            // Test for empty node-set
            if (result == null || (result instanceof List && ((List) result).size() == 0))
                return null;
            // Otherwise return a String
            return (result instanceof String) ? (String) result : node.createXPath(".").valueOf(result);
        } catch (InvalidXPathException e) {
            throw new OXFException(e);
        }
    }

    private static void hookupPath(org.dom4j.XPath path, Map prefixes, VariableContext variableContext, FunctionContext functionContext) {
        if (prefixes != null)
            path.setNamespaceContext(new SimpleNamespaceContext(prefixes));
        if (variableContext != null)
            path.setVariableContext(variableContext);
        if (functionContext != null) {
            final FunctionContext _functionContext = functionContext;
            path.setFunctionContext(new FunctionContext() {
                public Function getFunction(String namespaceURI,
                                            String prefix,
                                            String localName) throws UnresolvableException {

                    Function f = _functionContext.getFunction(namespaceURI, prefix, localName);
                    if (f != null)
                        return f;
                    else
                        return XPathFunctionContext.getInstance().getFunction(namespaceURI, prefix, localName);
                }
            });
        }
    }

    /**
     * Select a value as an Integer.
     *
     * If the XPath expressions select an empty node set, return null.
     */
    public static Integer selectIntegerValue(org.dom4j.Node node, String expr) {
        return selectIntegerValue(node, expr, null, null, null);
    }

    public static Integer selectIntegerValue(org.dom4j.Node node, String expr, Map prefixes, VariableContext variableContext, FunctionContext functionContext) {
        String text = selectStringValue(node, expr, prefixes, variableContext, functionContext);
        return text == null ? null : new Integer(text);
    }

    /**
     * Boolean selectors.
     */
    public static Boolean selectBooleanValue(org.dom4j.Node node, String expr) {
        return selectBooleanValue(node, expr, null, null, null, false);
    }

    public static Boolean selectBooleanValue(org.dom4j.Node node, String expr, Map prefixes, VariableContext variableContext, FunctionContext functionContext) {
        return selectBooleanValue(node, expr, prefixes, variableContext, functionContext, false);
    }

    public static Boolean selectBooleanValue(org.dom4j.Node node, String expr, Map prefixes, VariableContext variableContext, FunctionContext functionContext, boolean allowNull) {
        try {
            org.dom4j.XPath path = node.createXPath(expr);
            hookupPath(path, prefixes, variableContext, functionContext);
            Object result = path.evaluate(node);
            if (allowNull && (result == null || (result instanceof List && ((List) result).size() == 0)))
                return null;
            else
                return new Boolean(node.createXPath("boolean(.)").valueOf(result));
        } catch (InvalidXPathException e) {
            throw new OXFException(e);
        }
    }

    /**
     * Wraps an Iterator and filters out objects of a particular class. Because the Iterator interface
     * is particularly minimal, this means reading the next object from the iterator in advance.
     */
    private static class IteratorFilter implements Iterator {
    	/** The class of objects to filter out. */
    	private final Class filterClass;

    	/** The underlying iterator. */
    	private final Iterator iterator;

    	/** The next filtered object which has been read ahead, or null if there are no
    	 * remaining objects in the iterator which meet the filter criteria.*/
    	private Object nextFiltered;

    	/**
    	 * @param iterator the iterator to filter
    	 * @param filterClass the type of object to filter out, including sub classes & implementations
    	 */
    	public IteratorFilter(final Iterator iterator, final Class filterClass) {
    		this.filterClass = filterClass;
    		this.iterator = iterator;

    		// Pre-load the first object
    		nextFiltered = readNext();
    	}

		/** @see java.util.Iterator#remove() */
		public void remove() {
			throw new UnsupportedOperationException();
		}

		/** @see java.util.Iterator#hasNext() */
		public boolean hasNext() {
			return nextFiltered != null;
		}

		/** @see java.util.Iterator#next() */
		public Object next() {
			if(nextFiltered == null) {
				throw new NoSuchElementException();
			}

			// Return the previously read object, but not before
			// pre-loading the next one.

			final Object toReturn = nextFiltered;
			nextFiltered = readNext();
			return toReturn;
		}

		/**
		 * @return the next object in the iterator which meets the filter criteria, or null if
		 * 	there are none remaining after the filter is applied.
		 */
		private Object readNext() {

			// Loop until we find a matching object, or
			// until we run out of objects.
			while(iterator.hasNext()) {
				final Object next = iterator.next();
				if(!filterClass.isAssignableFrom(next.getClass())) {
					return next;
				}
			}
			return null;
		}
    }
}