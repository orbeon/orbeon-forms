package org.orbeon.jaxen;

/*
 $Id: XPath.java,v 1.11 2006/02/05 21:47:41 elharo Exp $

 Copyright 2003 The Werken Company. All Rights Reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the name of the Jaxen Project nor the names of its
    contributors may be used to endorse or promote products derived
    from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

import java.util.List;

/** Defines the interface to an object which represents an XPath 1.0 expression which
 *  can be evaluated against a variety of different XML object models.
 *
 *  <p>
 *  Most of the evaluation methods take a context object. This is typically a
 *  node or node-set object (which is typically a <code>List</code>
 *  of node objects) or a Jaxen <code>Context</code> object.
 *  A null context is allowed, meaning that
 *  there are no XML nodes on which to evaluate.
 *  </p>
 *
 *  @see org.orbeon.jaxen.dom4j.Dom4jXPath XPath for dom4j
 *  @see org.orbeon.jaxen.jdom.JDOMXPath  XPath for JDOM
 *  @see org.orbeon.jaxen.dom.DOMXPath   XPath for W3C DOM
 *
 *  @author <a href="mailto:bob@eng.werken.com">bob mcwhirter</a>
 *  @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 */
public interface XPath
{
    // ----------------------------------------------------------------------
    //     Basic Evaluation
    // ----------------------------------------------------------------------

    /** Evaluate this XPath against the given context.
     *
     *  <p>
     *  The context of evaluation my be a <em>document</em>,
     *  an <em>element</em>, or a set of <em>elements</em>.
     *  </p>
     *
     *  <p>
     *  If the expression evaluates to an XPath string, number, or boolean
     *  type, then the equivalent Java object type is returned.
     *  Otherwise, if the result is a node-set, then the returned value is a
     *  <code>List</code>.
     *  </p>
     *
     *  <p>
     *  When using this method, one must be careful to
     *  test the class of the returned objects, and of
     *  each of the composite members if a <code>List</code>
     *  is returned.  If the returned members are XML nodes,
     *  they will be the actual <code>Document</code>,
     *  <code>Element</code> or <code>Attribute</code> objects
     *  as defined by the concrete XML object-model implementation,
     *  directly from the context document.  This method <strong>does not
     *  return <em>copies</em> of anything</strong>. It merely returns
     *  references to nodes within the source document.
     *  </p>
     *
     *  @param context the node, node-set or Context object for evaluation.
     *         This value can be null.
     *
     *  @return the result of evaluating the XPath expression
     *          against the supplied context
     *
     *  @throws JaxenException if an error occurs while attempting
     *          to evaluate the expression
     */
    Object evaluate(Object context) throws JaxenException;

    // ----------------------------------------------------------------------
    //     Advanced Evaluation
    // ----------------------------------------------------------------------

    /** Retrieve a string-value interpretation of this XPath
     *  expression when evaluated against the given context.
     *
     *  <p>
     *  The string-value of the expression is determined per
     *  the <code>string(..)</code> core function as defined
     *  in the XPath specification.  This means that an expression
     *  that selects more than one nodes will return the string value
     *  of the first node in the node set..
     *  </p>
     *
     *  @deprecated use {@link #stringValueOf(Object)} instead
     *
     *  @param context the node, node-set or Context object for evaluation.
     *         This value can be null.
     *
     *  @return the string-value of this expression
     *
     *  @throws JaxenException if an error occurs while attempting
     *          to evaluate the expression
     */
    String valueOf(Object context)
        throws JaxenException;

    /** Retrieve a string-value interpretation of this XPath
     *  expression when evaluated against the given context.
     *
     *  <p>
     *  The string-value of the expression is determined per
     *  the <code>string(..)</code> core function as defined
     *  in the XPath specification.  This means that an expression
     *  that selects more than one nodes will return the string value
     *  of the first node in the node set..
     *  </p>
     *
     *  @param context the node, node-set or Context object for evaluation.
     *     This value can be null
     *
     *  @return the string-value interpretation of this expression
     *
     *  @throws JaxenException if an error occurs while attempting
     *          to evaluate the expression
     */
     String stringValueOf(Object context)
        throws JaxenException;

    /** Retrieve the boolean value of the first node in document order
     *  returned by this XPath expression when evaluated in
     *  the given context.
     *
     *  <p>
     *  The boolean-value of the expression is determined per
     *  the <code>boolean()</code> function defined
     *  in the XPath specification.  This means that an expression
     *  that selects zero nodes will return <code>false</code>,
     *  while an expression that selects one or more nodes will
     *  return <code>true</code>. An expression that returns a string
     *  returns false for empty strings and true for all other strings.
     *  An expression that returns a number
     *  returns false for zero and true for non-zero numbers.
     *  </p>
     *
     *  @param context the node, node-set or Context object for evaluation. This value can be null.
     *
     *  @return the boolean-value of this expression
     *
     *  @throws JaxenException if an error occurs while attempting
     *          to evaluate the expression
     */
    boolean booleanValueOf(Object context)
        throws JaxenException;


    /** Retrieve the number-value of the first node in document order
     *  returned by this XPath expression when evaluated in
     *  the given context.
     *
     *  <p>
     *  The number-value of the expression is determined per
     *  the <code>number(..)</code> core function as defined
     *  in the XPath specification. This means that if this
     *  expression selects multiple nodes, the number-value
     *  of the first node is returned.
     *  </p>
     *
     *  @param context the node, node-set or Context object for evaluation. This value can be null.
     *
     *  @return the number-value interpretation of this expression
     *
     *  @throws JaxenException if an error occurs while attempting
     *          to evaluate the expression
     */
    Number numberValueOf(Object context)
        throws JaxenException;

    // ----------------------------------------------------------------------
    //     Selection
    // ----------------------------------------------------------------------

    /** Select all nodes that are selectable by this XPath
     *  expression. If multiple nodes match, multiple nodes
     *  will be returned.
     *
     *  <p>
     *  <b>NOTE:</b> In most cases, nodes will be returned
     *  in document-order, as defined by the XML Canonicalization
     *  specification.  The exception occurs when using XPath
     *  expressions involving the <code>union</code> operator
     *  (denoted with the pipe '|' character).
     *  </p>
     *
     *  @see #selectSingleNode
     *
     *  @param context the node, node-set or Context object for evaluation.
     *     This value can be null.
     *
     *  @return the node-set of all items selected
     *          by this XPath expression.
     *
     *  @throws JaxenException if an error occurs while attempting
     *          to evaluate the expression
     */
    List selectNodes(Object context)
        throws JaxenException;

    /**
     *  <p>
     *  Return the first node in document order that is selected by this
     *  XPath expression.
     *  </p>
     *
     *  @see #selectNodes
     *
     *  @param context the node, node-set or Context object for evaluation.
     *     This value can be null.
     *
     *  @return the first node in document order selected by this XPath expression
     *
     *  @throws JaxenException if an error occurs while attempting
     *          to evaluate the expression
     */
    Object selectSingleNode(Object context)
        throws JaxenException;

    // ----------------------------------------------------------------------
    //     Helpers
    // ----------------------------------------------------------------------

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
     *  If you have installed a specific custom <code>NamespaceContext</code>,
     *  then this method will throw a <code>JaxenException</code>.
     *  </p>
     *
     *  @param prefix the namespace prefix
     *  @param uri the namespace URI
     *
     *  @throws JaxenException if a <code>NamespaceContext</code>
     *          used by this XPath has been explicitly installed
     */
    void addNamespace(String prefix,
                      String uri)
        throws JaxenException;

    // ----------------------------------------------------------------------
    //     Properties
    // ----------------------------------------------------------------------

    /** Set a <code>NamespaceContext</code> for  this
     *  XPath expression.
     *
     *  <p>
     *  A <code>NamespaceContext</code> is responsible for translating
     *  namespace prefixes within the expression into namespace URIs.
     *  </p>
     *
     *  @see NamespaceContext
     *  @see NamespaceContext#translateNamespacePrefixToUri
     *
     *  @param namespaceContext the <code>NamespaceContext</code> to
     *         install for this expression
     */
    void setNamespaceContext(NamespaceContext namespaceContext);

    /** Set a <code>FunctionContext</code> for  this XPath
     *  expression.
     *
     *  <p>
     *  A <code>FunctionContext</code> is responsible for resolving
     *  all function calls used within the expression.
     *  </p>
     *
     *  @see FunctionContext
     *  @see FunctionContext#getFunction
     *
     *  @param functionContext the <code>FunctionContext</code> to
     *         install for this expression
     */
    void setFunctionContext(FunctionContext functionContext);

    /** Set a <code>VariableContext</code> for this XPath
     *  expression.
     *
     *  <p>
     *  A <code>VariableContext</code> is responsible for resolving
     *  all variables referenced within the expression.
     *  </p>
     *
     *  @see VariableContext
     *  @see VariableContext#getVariableValue
     *
     *  @param variableContext the <code>VariableContext</code> to
     *         install for this expression.
     */
    void setVariableContext(VariableContext variableContext);

    /** Retrieve the <code>NamespaceContext</code> used by this XPath
     *  expression.
     *
     *  <p>
     *  A <code>FunctionContext</code> is responsible for resolving
     *  all function calls used within the expression.
     *  </p>
     *
     *  <p>
     *  If this XPath expression has not previously had a <code>NamespaceContext</code>
     *  installed, a new default <code>NamespaceContext</code> will be created,
     *  installed and returned.
     *  </p>
     *
     *  @see NamespaceContext
     *
     *  @return the <code>NamespaceContext</code> used by this expression
     */
    NamespaceContext getNamespaceContext();

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
     *  @see FunctionContext
     *
     *  @return the <code>FunctionContext</code> used by this expression
     */
    FunctionContext getFunctionContext();

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
     *  @see VariableContext
     *
     *  @return the <code>VariableContext</code> used by this expression
     */
    VariableContext getVariableContext();


    /** Retrieve the XML object-model-specific {@link Navigator}
     *  used to evaluate this XPath expression.
     *
     *  @return the implementation-specific <code>Navigator</code>
     */
    Navigator getNavigator();
}
