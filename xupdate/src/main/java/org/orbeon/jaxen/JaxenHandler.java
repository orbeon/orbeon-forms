/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/JaxenHandler.java,v 1.15 2006/03/30 13:53:11 elharo Exp $
 * $Revision: 1.15 $
 * $Date: 2006/03/30 13:53:11 $
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
 * $Id: JaxenHandler.java,v 1.15 2006/03/30 13:53:11 elharo Exp $
 */



package org.orbeon.jaxen;

import org.orbeon.jaxen.expr.*;
import org.orbeon.jaxen.saxpath.Operator;
import org.orbeon.jaxen.saxpath.XPathHandler;

import java.util.Iterator;
import java.util.LinkedList;

/** SAXPath <code>XPathHandler</code> implementation capable
 *  of building Jaxen expression trees which can walk various
 *  different object models.
 *
 *  @author bob mcwhirter (bob@werken.com)
 */
public class JaxenHandler implements XPathHandler
{
    private XPathFactory xpathFactory;
    private XPathExpr    xpath;

    /**
     * ????
     */
    protected boolean simplified;

    /**
     * This may be changed to an ArrayList in the future (i.e. version &gt;= 1.2).
     * You really shouldn't be accessing this field directly, but
     * if you are please try to use it as a generic List. Don't use the
     * methods that are only available in LinkedList.
     */
    protected LinkedList stack;

    /** Constructor
     */
    public JaxenHandler()
    {
        this.stack        = new LinkedList();
        this.xpathFactory = new DefaultXPathFactory();
    }

    /** Set the Jaxen <code>XPathFactory</code> that constructs
     *  the XPath expression tree during the parse.
     *
     *  @param xpathFactory the factory to use during the parse
     */
    public void setXPathFactory(XPathFactory xpathFactory)
    {
        this.xpathFactory = xpathFactory;
    }

    /** Retrieve the Jaxen <code>XPathFactory</code> used
     *  during the parse to construct the XPath expression tree.
     *
     *  @return the <code>XPathFactory</code> used during the parse.
     */
    public XPathFactory getXPathFactory()
    {
        return this.xpathFactory;
    }

    /** Retrieve the simplified Jaxen XPath expression tree.
     *
     *  <p>
     *  This method is only valid once <code>XPathReader.parse(...)</code>
     *  successfully returned.
     *  </p>
     *
     *  @return the XPath expression tree
     */
    public XPathExpr getXPathExpr()
    {
        return getXPathExpr( true );
    }

    /** Retrieve the Jaxen XPath expression tree, optionally
     *  simplified.
     *
     *  <p>
     *  This method is only valid once <code>XPathReader.parse(...)</code>
     *  successfully returned.
     *  </p>
     *
     *  @param shouldSimplify ????
     *
     *  @return the XPath expression tree
     */
    public XPathExpr getXPathExpr(boolean shouldSimplify)
    {
        if ( shouldSimplify && ! this.simplified )
        {
            this.xpath.simplify();
            this.simplified = true;
        }

        return this.xpath;
    }

    public void startXPath()
    {
        this.simplified = false;
        pushFrame();
    }

    public void endXPath() throws JaxenException
    {
        this.xpath = getXPathFactory().createXPath( (Expr) pop() );
        popFrame();
    }

    public void startPathExpr()
    {
        pushFrame();
    }

    public void endPathExpr() throws JaxenException
    {

        // PathExpr ::=   LocationPath
        //              | FilterExpr
        //              | FilterExpr / RelativeLocationPath
        //              | FilterExpr // RelativeLocationPath
        //
        // If the current stack-frame has two items, it's a
        // FilterExpr and a LocationPath (of some flavor).
        //
        // If the current stack-frame has one item, it's simply
        // a FilterExpr, and more than likely boils down to a
        // primary expr of some flavor.  But that's for another
        // method...

        FilterExpr   filterExpr;
        LocationPath locationPath;

        Object       popped;

        if ( stackSize() == 2 )
        {
            locationPath = (LocationPath) pop();
            filterExpr   = (FilterExpr) pop();
        }
        else
        {
            popped = pop();

            if ( popped instanceof LocationPath )
            {
                locationPath = (LocationPath) popped;
                filterExpr   = null;
            }
            else
            {
                locationPath = null;
                filterExpr   = (FilterExpr) popped;
            }
        }
        popFrame();

        push( getXPathFactory().createPathExpr( filterExpr,
                                               locationPath ) );
    }

    public void startAbsoluteLocationPath() throws JaxenException
    {
        pushFrame();

        push( getXPathFactory().createAbsoluteLocationPath() );
    }

    public void endAbsoluteLocationPath() throws JaxenException
    {
        endLocationPath();
    }

    public void startRelativeLocationPath() throws JaxenException
    {
        pushFrame();

        push( getXPathFactory().createRelativeLocationPath() );
    }

    public void endRelativeLocationPath() throws JaxenException
    {
        endLocationPath();
    }

    protected void endLocationPath() throws JaxenException
    {
        LocationPath path = (LocationPath) peekFrame().removeFirst();

        addSteps( path,
                  popFrame().iterator() );

        push( path );
    }

    protected void addSteps(LocationPath locationPath,
                          Iterator stepIter)
    {
        while ( stepIter.hasNext() )
        {
            locationPath.addStep( (Step) stepIter.next() );
        }
    }

    public void startNameStep(int axis,
                              String prefix,
                              String localName) throws JaxenException
    {
        pushFrame();

        push( getXPathFactory().createNameStep( axis,
                                               prefix,
                                               localName ) );
    }

    public void endNameStep()
    {
        endStep();
    }

    public void startTextNodeStep(int axis) throws JaxenException
    {
        //System.err.println("startTextNodeStep()");
        pushFrame();

        push( getXPathFactory().createTextNodeStep( axis ) );
    }

    public void endTextNodeStep()
    {
        endStep();
    }

    public void startCommentNodeStep(int axis) throws JaxenException
    {
        pushFrame();

        push( getXPathFactory().createCommentNodeStep( axis ) );
    }

    public void endCommentNodeStep()
    {
        endStep();
    }

    public void startAllNodeStep(int axis) throws JaxenException
    {
        pushFrame();

        push( getXPathFactory().createAllNodeStep( axis ) );
    }

    public void endAllNodeStep()
    {
        endStep();
    }

    public void startProcessingInstructionNodeStep(int axis,
                                                   String name) throws JaxenException
    {
        pushFrame();

        push( getXPathFactory().createProcessingInstructionNodeStep( axis,
                                                                    name ) );
    }

    public void endProcessingInstructionNodeStep()
    {
        endStep();
    }

    protected void endStep()
    {
        Step step = (Step) peekFrame().removeFirst();

        addPredicates( step,
                       popFrame().iterator() );

        push( step );
    }

    public void startPredicate()
    {
        pushFrame();
    }

    public void endPredicate() throws JaxenException
    {
        Predicate predicate = getXPathFactory().createPredicate( (Expr) pop() );

        popFrame();

        push( predicate );
    }

    public void startFilterExpr()
    {
        pushFrame();
    }

    public void endFilterExpr() throws JaxenException
    {
        Expr expr = (Expr) peekFrame().removeFirst();

        FilterExpr filter = getXPathFactory().createFilterExpr( expr );

        Iterator predIter = popFrame().iterator();

        addPredicates( filter,
                       predIter );

        push( filter );
    }

    protected void addPredicates(Predicated obj,
                               Iterator predIter)
    {
        while ( predIter.hasNext() )
        {
            obj.addPredicate( (Predicate) predIter.next() );
        }
    }

    protected void returnExpr()
    {
        Expr expr = (Expr) pop();
        popFrame();
        push( expr );
    }

    public void startOrExpr()
    {
    }

    public void endOrExpr(boolean create) throws JaxenException
    {

        if ( create )
        {
            Expr rhs = (Expr) pop();
            Expr lhs = (Expr) pop();

            push( getXPathFactory().createOrExpr( lhs,
                                                 rhs ) );
        }
    }

    public void startAndExpr()
    {
    }

    public void endAndExpr(boolean create) throws JaxenException
    {

        if ( create )
        {

            Expr rhs = (Expr) pop();
            Expr lhs = (Expr) pop();

            push( getXPathFactory().createAndExpr( lhs,
                                                  rhs ) );
        }
    }

    public void startEqualityExpr()
    {
    }

    public void endEqualityExpr(int operator) throws JaxenException
    {

        if ( operator != Operator.NO_OP )
        {

            Expr rhs = (Expr) pop();
            Expr lhs = (Expr) pop();

            push( getXPathFactory().createEqualityExpr( lhs,
                                                        rhs,
                                                        operator ) );
        }
    }

    public void startRelationalExpr()
    {
    }

    public void endRelationalExpr(int operator) throws JaxenException
    {

        if ( operator != Operator.NO_OP )
        {

            Expr rhs = (Expr) pop();
            Expr lhs = (Expr) pop();

            push( getXPathFactory().createRelationalExpr( lhs,
                                                         rhs,
                                                         operator ) );
        }
    }

    public void startAdditiveExpr()
    {
    }

    public void endAdditiveExpr(int operator) throws JaxenException
    {

        if ( operator != Operator.NO_OP )
        {

            Expr rhs = (Expr) pop();
            Expr lhs = (Expr) pop();

            push( getXPathFactory().createAdditiveExpr( lhs,
                                                        rhs,
                                                        operator ) );
        }
    }

    public void startMultiplicativeExpr()
    {
    }

    public void endMultiplicativeExpr(int operator) throws JaxenException
    {

        if ( operator != Operator.NO_OP )
        {

            Expr rhs = (Expr) pop();
            Expr lhs = (Expr) pop();

            push( getXPathFactory().createMultiplicativeExpr( lhs,
                                                             rhs,
                                                             operator ) );
        }
    }

    public void startUnaryExpr()
    {
     }

    public void endUnaryExpr(int operator) throws JaxenException
    {

        if ( operator != Operator.NO_OP )
        {
            push( getXPathFactory().createUnaryExpr( (Expr) pop(),
                                                    operator ) );
        }
    }

    public void startUnionExpr()
    {
    }

    public void endUnionExpr(boolean create) throws JaxenException
    {

        if ( create )
        {

            Expr rhs = (Expr) pop();
            Expr lhs = (Expr) pop();

            push( getXPathFactory().createUnionExpr( lhs,
                                                    rhs ) );
        }
    }

    public void number(int number) throws JaxenException
    {
        push( getXPathFactory().createNumberExpr( number ) );
    }

    public void number(double number) throws JaxenException
    {
        push( getXPathFactory().createNumberExpr( number ) );
    }

    public void literal(String literal) throws JaxenException
    {
        push( getXPathFactory().createLiteralExpr( literal ) );
    }

    public void variableReference(String prefix,
                                  String variableName) throws JaxenException
    {
        push( getXPathFactory().createVariableReferenceExpr( prefix,
                                                             variableName ) );
    }

    public void startFunction(String prefix,
                              String functionName) throws JaxenException
    {
        pushFrame();
        push( getXPathFactory().createFunctionCallExpr( prefix,
                                                        functionName ) );
    }

    public void endFunction()
    {
        FunctionCallExpr function = (FunctionCallExpr) peekFrame().removeFirst();

        addParameters( function,
                       popFrame().iterator() );

        push( function );
    }

    protected void addParameters(FunctionCallExpr function,
                               Iterator paramIter)
    {
        while ( paramIter.hasNext() )
        {
            function.addParameter( (Expr) paramIter.next() );
        }
    }

    protected int stackSize()
    {
        return peekFrame().size();
    }

    protected void push(Object obj)
    {
        peekFrame().addLast( obj );
    }

    protected Object pop()
    {
        return peekFrame().removeLast();
    }

    protected boolean canPop()
    {
        return ( peekFrame().size() > 0 );
    }

    protected void pushFrame()
    {
        this.stack.addLast( new LinkedList() );
    }

    protected LinkedList popFrame()
    {
        return (LinkedList) this.stack.removeLast();
    }

    protected LinkedList peekFrame()
    {
        return (LinkedList) this.stack.getLast();
    }
}
