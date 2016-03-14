/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/BaseXPath.java,v 1.50 2006/05/03 16:07:03 elharo Exp $
 * $Revision: 1.50 $
 * $Date: 2006/05/03 16:07:03 $
 *
 * ====================================================================
 *
 * Copyright 2000-2002 bob mcwhirter & James Strachan.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   * Neither the name of the Jaxen Project nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Jaxen Project and was originally
 * created by bob mcwhirter <bob@werken.com> and
 * James Strachan <jstrachan@apache.org>.  For more information on the
 * Jaxen Project, please see <http://www.jaxen.org/>.
 *
 * $Id: BaseXPath.java,v 1.50 2006/05/03 16:07:03 elharo Exp $
 */


package org.orbeon.jaxen;

import org.orbeon.jaxen.expr.Expr;
import org.orbeon.jaxen.expr.XPathExpr;
import org.orbeon.jaxen.function.BooleanFunction;
import org.orbeon.jaxen.function.NumberFunction;
import org.orbeon.jaxen.function.StringFunction;
import org.orbeon.jaxen.saxpath.SAXPathException;
import org.orbeon.jaxen.saxpath.XPathReader;
import org.orbeon.jaxen.saxpath.helpers.XPathReaderFactory;
import org.orbeon.jaxen.util.SingletonList;

import java.io.Serializable;
import java.util.List;

/** Base functionality for all concrete, implementation-specific XPaths.
 *
 *  <p>
 *  This class provides generic functionality for further-defined
 *  implementation-specific XPaths.
 *  </p>
 *
 *  <p>
 *  If you want to adapt the Jaxen engine so that it can traverse your own
 *  object model, then this is a good base class to derive from.
 *  Typically you only really need to provide your own
 *  {@link org.orbeon.jaxen.Navigator} implementation.
 *  </p>
 *
 *  @see org.orbeon.jaxen.dom4j.Dom4jXPath XPath for dom4j
 *  @see org.orbeon.jaxen.jdom.JDOMXPath   XPath for JDOM
 *  @see org.orbeon.jaxen.dom.DOMXPath     XPath for W3C DOM
 *
 *  @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 *  @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 */
public class BaseXPath implements XPath, Serializable
{

    /**
     *
     */
    private static final long serialVersionUID = -5882742682135366202L;

    /** Original expression text. */
    private String exprText;

    /** the parsed form of the XPath expression */
    private XPathExpr xpath;

    /** the support information and function, namespace and variable contexts */
    private ContextSupport support;

    /** the implementation-specific Navigator for retrieving XML nodes **/
    private Navigator navigator;

    /** Construct given an XPath expression string.
     *
     *  @param xpathExpr the XPath expression
     *
     *  @throws JaxenException if there is a syntax error while
     *          parsing the expression
     */
    protected BaseXPath(String xpathExpr) throws JaxenException
    {
        try
        {
            XPathReader reader = XPathReaderFactory.createReader();
            JaxenHandler handler = new JaxenHandler();
            reader.setXPathHandler( handler );
            reader.parse( xpathExpr );
            this.xpath = handler.getXPathExpr();
        }
        catch (org.orbeon.jaxen.saxpath.XPathSyntaxException e)
        {
            throw new org.orbeon.jaxen.XPathSyntaxException( e );
        }
        catch (SAXPathException e)
        {
            throw new JaxenException( e );
        }

        this.exprText = xpathExpr;
    }

    /** Construct given an XPath expression string.
     *
     *  @param xpathExpr the XPath expression
     *
     *  @param navigator the XML navigator to use
     *
     *  @throws JaxenException if there is a syntax error while
     *          parsing the expression
     */
    public BaseXPath(String xpathExpr, Navigator navigator) throws JaxenException
    {
        this( xpathExpr );
        this.navigator = navigator;
    }

    /** Evaluate this XPath against a given context.
     *  The context of evaluation may be any object type
     *  the navigator recognizes as a node.
     *  The return value is either a <code>String</code>,
     *  <code>Double</code>, <code>Boolean</code>, or <code>List</code>
     *  of nodes.
     *
     *  <p>
     *  When using this method, one must be careful to
     *  test the class of the returned object.  If the returned
     *  object is a list, then the items in this
     *  list will be the actual <code>Document</code>,
     *  <code>Element</code>, <code>Attribute</code>, etc. objects
     *  as defined by the concrete XML object-model implementation,
     *  directly from the context document.  This method <strong>does
     *  not return <em>copies</em> of anything</strong>, but merely
     *  returns references to objects within the source document.
     *  </p>
     *
     * @param context the node, node-set or Context object for evaluation.
     *      This value can be null.
     *
     * @return the result of evaluating the XPath expression
     *          against the supplied context
     * @throws JaxenException if an XPath error occurs during expression evaluation
     * @throws ClassCastException if the context is not a node
     */
    public Object evaluate(Object context) throws JaxenException
    {
        List answer = selectNodes(context);

        if ( answer != null
             &&
             answer.size() == 1 )
        {
            Object first = answer.get(0);

            if ( first instanceof String
                 ||
                 first instanceof Number
                 ||
                 first instanceof Boolean )
            {
                return first;
            }
        }
        return answer;
    }

    /** Select all nodes that are selected by this XPath
     *  expression. If multiple nodes match, multiple nodes
     *  will be returned. Nodes will be returned
     *  in document-order, as defined by the XPath
     *  specification. If the expression selects a non-node-set
     *  (i.e. a number, boolean, or string) then a List
     *  containing just that one object is returned.
     *  </p>
     *
     * @param node the node, node-set or Context object for evaluation.
     *     This value can be null.
     *
     * @return the node-set of all items selected
     *          by this XPath expression
     * @throws JaxenException if an XPath error occurs during expression evaluation
     *
     * @see #selectNodesForContext
     */
    public List selectNodes(Object node) throws JaxenException
    {
        Context context = getContext( node );
        return selectNodesForContext( context );
    }

    /** Select only the first node selected by this XPath
     *  expression.  If multiple nodes match, only one node will be
     *  returned. The selected node will be the first
     *  selected node in document-order, as defined by the XPath
     *  specification.
     *  </p>
     *
     * @param node the node, node-set or Context object for evaluation.
     *     This value can be null.
     *
     * @return the node-set of all items selected
     *          by this XPath expression
     * @throws JaxenException if an XPath error occurs during expression evaluation
     *
     * @see #selectNodes
     */
    public Object selectSingleNode(Object node) throws JaxenException
    {
        List results = selectNodes( node );

        if ( results.isEmpty() )
        {
            return null;
        }

        return results.get( 0 );
    }

    /**
     * Returns the XPath string-value of the argument node.
     *
     * @param node the node whose value to take
     * @return the XPath string value of this node
     * @throws JaxenException if an XPath error occurs during expression evaluation
     * @deprecated replaced by {@link #stringValueOf}
     */
    public String valueOf(Object node) throws JaxenException
    {
        return stringValueOf( node );
    }

    /** Retrieves the string-value of the result of
     *  evaluating this XPath expression when evaluated
     *  against the specified context.
     *
     *  <p>
     *  The string-value of the expression is determined per
     *  the <code>string(..)</code> core function defined
     *  in the XPath specification.  This means that an expression
     *  that selects zero nodes will return the empty string,
     *  while an expression that selects one-or-more nodes will
     *  return the string-value of the first node.
     *  </p>
     *
     * @param node the node, node-set or Context object for evaluation. This value can be null.
     *
     * @return the string-value of the result of evaluating this expression with the specified context node
     * @throws JaxenException if an XPath error occurs during expression evaluation
     */
    public String stringValueOf(Object node) throws JaxenException
    {
        Context context = getContext( node );

        Object result = selectSingleNodeForContext( context );

        if ( result == null )
        {
            return "";
        }

        return StringFunction.evaluate( result,
                                        context.getNavigator() );
    }

    /** Retrieve a boolean-value interpretation of this XPath
     *  expression when evaluated against a given context.
     *
     *  <p>
     *  The boolean-value of the expression is determined per
     *  the <code>boolean(..)</code> function defined
     *  in the XPath specification.  This means that an expression
     *  that selects zero nodes will return <code>false</code>,
     *  while an expression that selects one or more nodes will
     *  return <code>true</code>.
     *  </p>
     *
     * @param node the node, node-set or Context object for evaluation. This value can be null.
     *
     * @return the boolean-value of the result of evaluating this expression with the specified context node
     * @throws JaxenException if an XPath error occurs during expression evaluation
     */
    public boolean booleanValueOf(Object node) throws JaxenException
    {
        Context context = getContext( node );
        List result = selectNodesForContext( context );
        if ( result == null ) return false;
        return BooleanFunction.evaluate( result, context.getNavigator() ).booleanValue();
    }

    /** Retrieve a number-value interpretation of this XPath
     *  expression when evaluated against a given context.
     *
     *  <p>
     *  The number-value of the expression is determined per
     *  the <code>number(..)</code> core function as defined
     *  in the XPath specification. This means that if this
     *  expression selects multiple nodes, the number-value
     *  of the first node is returned.
     *  </p>
     *
     * @param node the node, node-set or Context object for evaluation. This value can be null.
     *
     * @return a <code>Double</code> indicating the numeric value of
     *      evaluating this expression against the specified context
     * @throws JaxenException if an XPath error occurs during expression evaluation
     */
    public Number numberValueOf(Object node) throws JaxenException
    {
        Context context = getContext( node );
        Object result = selectSingleNodeForContext( context );
        return NumberFunction.evaluate( result,
                                        context.getNavigator() );
    }

    // Helpers

    /** Add a namespace prefix-to-URI mapping for this XPath
     *  expression.
     *
     *  <p>
     *  Namespace prefix-to-URI mappings in an XPath are independent
     *  of those used within any document.  Only the mapping explicitly
     *  added to this XPath will be available for resolving the
     *  XPath expression.
     *  </p>
     *
     *  <p>
     *  This is a convenience method for adding mappings to the
     *  default {@link NamespaceContext} in place for this XPath.
     *  If you have installed a custom <code>NamespaceContext</code>
     *  that is not a <code>SimpleNamespaceContext</code>,
     *  then this method will throw a <code>JaxenException</code>.
     *  </p>
     *
     *  @param prefix the namespace prefix
     *  @param uri the namespace URI
     *
     *  @throws JaxenException if the <code>NamespaceContext</code>
     *          used by this XPath is not a <code>SimpleNamespaceContext</code>
     */
    public void addNamespace(String prefix,
                             String uri) throws JaxenException
    {
        NamespaceContext nsContext = getNamespaceContext();
        if ( nsContext instanceof SimpleNamespaceContext )
        {
            ((SimpleNamespaceContext)nsContext).addNamespace( prefix,
                                                              uri );
            return;
        }

        throw new JaxenException("Operation not permitted while using a non-simple namespace context.");
    }


    // ------------------------------------------------------------
    // ------------------------------------------------------------
    //     Properties
    // ------------------------------------------------------------
    // ------------------------------------------------------------


    /** Set a <code>NamespaceContext</code> for use with this
     *  XPath expression.
     *
     *  <p>
     *  A <code>NamespaceContext</code> is responsible for translating
     *  namespace prefixes within the expression into namespace URIs.
     *  </p>
     *
     *  @param namespaceContext the <code>NamespaceContext</code> to
     *         install for this expression
     *
     *  @see NamespaceContext
     *  @see NamespaceContext#translateNamespacePrefixToUri
     */
    public void setNamespaceContext(NamespaceContext namespaceContext)
    {
        getContextSupport().setNamespaceContext(namespaceContext);
    }

    /** Set a <code>FunctionContext</code> for use with this XPath
     *  expression.
     *
     *  <p>
     *  A <code>FunctionContext</code> is responsible for resolving
     *  all function calls used within the expression.
     *  </p>
     *
     *  @param functionContext the <code>FunctionContext</code> to
     *         install for this expression
     *
     *  @see FunctionContext
     *  @see FunctionContext#getFunction
     */
    public void setFunctionContext(FunctionContext functionContext)
    {
        getContextSupport().setFunctionContext(functionContext);
    }

    /** Set a <code>VariableContext</code> for use with this XPath
     *  expression.
     *
     *  <p>
     *  A <code>VariableContext</code> is responsible for resolving
     *  all variables referenced within the expression.
     *  </p>
     *
     *  @param variableContext The <code>VariableContext</code> to
     *         install for this expression
     *
     *  @see VariableContext
     *  @see VariableContext#getVariableValue
     */
    public void setVariableContext(VariableContext variableContext)
    {
        getContextSupport().setVariableContext(variableContext);
    }

    /** Retrieve the <code>NamespaceContext</code> used by this XPath
     *  expression.
     *
     *  <p>
     *  A <code>NamespaceContext</code> is responsible for mapping
     *  prefixes used within the expression to namespace URIs.
     *  </p>
     *
     *  <p>
     *  If this XPath expression has not previously had a <code>NamespaceContext</code>
     *  installed, a new default <code>NamespaceContext</code> will be created,
     *  installed and returned.
     *  </p>
     *
     *  @return the <code>NamespaceContext</code> used by this expression
     *
     *  @see NamespaceContext
     */
    public NamespaceContext getNamespaceContext()
    {
        return getContextSupport().getNamespaceContext();
    }

    /** Retrieve the <code>FunctionContext</code> used by this XPath
     *  expression.
     *
     *  <p>
     *  A <code>FunctionContext</code> is responsible for resolving
     *  all function calls used within the expression.
     *  </p>
     *
     *  <p>
     *  If this XPath expression has not previously had a <code>FunctionContext</code>
     *  installed, a new default <code>FunctionContext</code> will be created,
     *  installed and returned.
     *  </p>
     *
     *  @return the <code>FunctionContext</code> used by this expression
     *
     *  @see FunctionContext
     */
    public FunctionContext getFunctionContext()
    {
        return getContextSupport().getFunctionContext();
    }

    /** Retrieve the <code>VariableContext</code> used by this XPath
     *  expression.
     *
     *  <p>
     *  A <code>VariableContext</code> is responsible for resolving
     *  all variables referenced within the expression.
     *  </p>
     *
     *  <p>
     *  If this XPath expression has not previously had a <code>VariableContext</code>
     *  installed, a new default <code>VariableContext</code> will be created,
     *  installed and returned.
     *  </p>
     *
     *  @return the <code>VariableContext</code> used by this expression
     *
     *  @see VariableContext
     */
    public VariableContext getVariableContext()
    {
        return getContextSupport().getVariableContext();
    }


    /** Retrieve the root expression of the internal
     *  compiled form of this XPath expression.
     *
     *  <p>
     *  Internally, Jaxen maintains a form of Abstract Syntax
     *  Tree (AST) to represent the structure of the XPath expression.
     *  This is normally not required during normal consumer-grade
     *  usage of Jaxen.  This method is provided for hard-core users
     *  who wish to manipulate or inspect a tree-based version of
     *  the expression.
     *  </p>
     *
     *  @return the root of the AST of this expression
     */
    public Expr getRootExpr()
    {
        return xpath.getRootExpr();
    }

    /** Return the original expression text.
     *
     *  @return the normalized XPath expression string
     */
    public String toString()
    {
        return this.exprText;
    }

    /** Returns a string representation of the parse tree.
     *
     *  @return a string representation of the parse tree.
     */
    public String debug()
    {
        return this.xpath.toString();
    }

    // ------------------------------------------------------------
    // ------------------------------------------------------------
    //     Implementation methods
    // ------------------------------------------------------------
    // ------------------------------------------------------------


    /** Create a {@link Context} wrapper for the provided
     *  implementation-specific object.
     *
     *  @param node the implementation-specific object
     *         to be used as the context
     *
     *  @return a <code>Context</code> wrapper around the object
     */
    protected Context getContext(Object node)
    {
        if ( node instanceof Context )
        {
            return (Context) node;
        }

        Context fullContext = new Context( getContextSupport() );

        if ( node instanceof List )
        {
            fullContext.setNodeSet( (List) node );
        }
        else
        {
            List list = new SingletonList(node);
            fullContext.setNodeSet( list );
        }

        return fullContext;
    }

    /** Retrieve the {@link ContextSupport} aggregation of
     *  <code>NamespaceContext</code>, <code>FunctionContext</code>,
     *  <code>VariableContext</code>, and {@link Navigator}.
     *
     *  @return aggregate <code>ContextSupport</code> for this
     *          XPath expression
     */
    protected ContextSupport getContextSupport()
    {
        if ( support == null )
        {
            support = new ContextSupport(
                createNamespaceContext(),
                createFunctionContext(),
                createVariableContext(),
                getNavigator()
            );
        }

        return support;
    }

    /** Retrieve the XML object-model-specific {@link Navigator}
     *  for us in evaluating this XPath expression.
     *
     *  @return the implementation-specific <code>Navigator</code>
     */
    public Navigator getNavigator()
    {
        return navigator;
    }



    // ------------------------------------------------------------
    // ------------------------------------------------------------
    //     Factory methods for default contexts
    // ------------------------------------------------------------
    // ------------------------------------------------------------

    /** Create a default <code>FunctionContext</code>.
     *
     *  @return a default <code>FunctionContext</code>
     */
    protected FunctionContext createFunctionContext()
    {
        return XPathFunctionContext.getInstance();
    }

    /** Create a default <code>NamespaceContext</code>.
     *
     *  @return a default <code>NamespaceContext</code> instance
     */
    protected NamespaceContext createNamespaceContext()
    {
        return new SimpleNamespaceContext();
    }

    /** Create a default <code>VariableContext</code>.
     *
     *  @return a default <code>VariableContext</code> instance
     */
    protected VariableContext createVariableContext()
    {
        return new SimpleVariableContext();
    }

    /** Select all nodes that match this XPath
     *  expression on the given Context object.
     *  If multiple nodes match, multiple nodes
     *  will be returned in document-order, as defined by the XPath
     *  specification. If the expression selects a non-node-set
     *  (i.e. a number, boolean, or string) then a List
     *  containing just that one object is returned.
     *  </p>
     *
     * @param context the Context which gets evaluated
     *
     * @return the node-set of all items selected
     *          by this XPath expression
     * @throws JaxenException if an XPath error occurs during expression evaluation
     *
     */
    protected List selectNodesForContext(Context context) throws JaxenException
    {
        List list = this.xpath.asList( context );
        return list;

    }


    /** Return only the first node that is selected by this XPath
     *  expression.  If multiple nodes match, only one node will be
     *  returned. The selected node will be the first
     *  selected node in document-order, as defined by the XPath
     *  specification. If the XPath expression selects a double,
     *  String, or boolean, then that object is returned.
     *  </p>
     *
     * @param context the Context against which this expression is evaluated
     *
     * @return the first node in document order of all nodes selected
     *          by this XPath expression
     * @throws JaxenException if an XPath error occurs during expression evaluation
     *
     *  @see #selectNodesForContext
     */
    protected Object selectSingleNodeForContext(Context context) throws JaxenException
    {
        List results = selectNodesForContext( context );

        if ( results.isEmpty() )
        {
            return null;
        }

        return results.get( 0 );
    }

}
