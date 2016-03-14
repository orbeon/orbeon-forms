/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/expr/DefaultXPathFactory.java,v 1.15 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.15 $
 * $Date: 2006/02/05 21:47:40 $
 *
 * ====================================================================
 *
 * Copyright 2000-2002 bob mcwhirter & James Strachan.
 * All rights reserved.
 *
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
 * $Id: DefaultXPathFactory.java,v 1.15 2006/02/05 21:47:40 elharo Exp $
 */
package org.orbeon.jaxen.expr;

import org.orbeon.jaxen.JaxenException;
import org.orbeon.jaxen.expr.iter.*;
import org.orbeon.jaxen.saxpath.Axis;
import org.orbeon.jaxen.saxpath.Operator;

public class DefaultXPathFactory implements XPathFactory
{
    public XPathExpr createXPath( Expr rootExpr ) throws JaxenException
    {
        return new DefaultXPathExpr( rootExpr );
    }

    public PathExpr createPathExpr( FilterExpr filterExpr,
                                    LocationPath locationPath ) throws JaxenException
    {
        return new DefaultPathExpr( filterExpr,
                                    locationPath );
    }

    public LocationPath createRelativeLocationPath() throws JaxenException
    {
        return new DefaultRelativeLocationPath();
    }

    public LocationPath createAbsoluteLocationPath() throws JaxenException
    {
        return new DefaultAbsoluteLocationPath();
    }

    public BinaryExpr createOrExpr( Expr lhs,
                                    Expr rhs ) throws JaxenException
    {
        return new DefaultOrExpr( lhs,
                                  rhs );
    }

    public BinaryExpr createAndExpr( Expr lhs,
                                     Expr rhs ) throws JaxenException
    {
        return new DefaultAndExpr( lhs,
                                   rhs );
    }

    public BinaryExpr createEqualityExpr( Expr lhs,
                                          Expr rhs,
                                          int equalityOperator ) throws JaxenException
    {
        switch( equalityOperator )
        {
            case Operator.EQUALS:
                {
                    return new DefaultEqualsExpr( lhs,
                                                  rhs );
                }
            case Operator.NOT_EQUALS:
                {
                    return new DefaultNotEqualsExpr( lhs,
                                                     rhs );
                }
        }
        throw new JaxenException( "Unhandled operator in createEqualityExpr(): " + equalityOperator );
    }

    public BinaryExpr createRelationalExpr( Expr lhs,
                                            Expr rhs,
                                            int relationalOperator ) throws JaxenException
    {
        switch( relationalOperator )
        {
            case Operator.LESS_THAN:
                {
                    return new DefaultLessThanExpr( lhs,
                                                    rhs );
                }
            case Operator.GREATER_THAN:
                {
                    return new DefaultGreaterThanExpr( lhs,
                                                       rhs );
                }
            case Operator.LESS_THAN_EQUALS:
                {
                    return new DefaultLessThanEqualExpr( lhs,
                                                         rhs );
                }
            case Operator.GREATER_THAN_EQUALS:
                {
                    return new DefaultGreaterThanEqualExpr( lhs,
                                                            rhs );
                }
        }
        throw new JaxenException( "Unhandled operator in createRelationalExpr(): " + relationalOperator );
    }

    public BinaryExpr createAdditiveExpr( Expr lhs,
                                          Expr rhs,
                                          int additiveOperator ) throws JaxenException
    {
        switch( additiveOperator )
        {
            case Operator.ADD:
                {
                    return new DefaultPlusExpr( lhs,
                                                rhs );
                }
            case Operator.SUBTRACT:
                {
                    return new DefaultMinusExpr( lhs,
                                                 rhs );
                }
        }
        throw new JaxenException( "Unhandled operator in createAdditiveExpr(): " + additiveOperator );
    }

    public BinaryExpr createMultiplicativeExpr( Expr lhs,
                                                Expr rhs,
                                                int multiplicativeOperator ) throws JaxenException
    {
        switch( multiplicativeOperator )
        {
            case Operator.MULTIPLY:
                {
                    return new DefaultMultiplyExpr( lhs,
                                                    rhs );
                }
            case Operator.DIV:
                {
                    return new DefaultDivExpr( lhs,
                                               rhs );
                }
            case Operator.MOD:
                {
                    return new DefaultModExpr( lhs,
                                               rhs );
                }
        }
        throw new JaxenException( "Unhandled operator in createMultiplicativeExpr(): " + multiplicativeOperator );
    }

    public Expr createUnaryExpr( Expr expr,
                                 int unaryOperator ) throws JaxenException
    {
        switch( unaryOperator )
        {
            case Operator.NEGATIVE:
                {
                    return new DefaultUnaryExpr( expr );
                }
        }
        return expr;
    }

    public UnionExpr createUnionExpr( Expr lhs,
                                      Expr rhs ) throws JaxenException
    {
        return new DefaultUnionExpr( lhs,
                                     rhs );
    }

    public FilterExpr createFilterExpr( Expr expr ) throws JaxenException
    {
        return new DefaultFilterExpr( expr, createPredicateSet() );
    }

    public FunctionCallExpr createFunctionCallExpr( String prefix,
                                                    String functionName ) throws JaxenException
    {
        return new DefaultFunctionCallExpr( prefix,
                                            functionName );
    }

    public NumberExpr createNumberExpr( int number ) throws JaxenException
    {
        return new DefaultNumberExpr( new Double( number ) );
    }

    public NumberExpr createNumberExpr( double number ) throws JaxenException
    {
        return new DefaultNumberExpr( new Double( number ) );
    }

    public LiteralExpr createLiteralExpr( String literal ) throws JaxenException
    {
        return new DefaultLiteralExpr( literal );
    }

    public VariableReferenceExpr createVariableReferenceExpr( String prefix,
                                                              String variable ) throws JaxenException
    {
        return new DefaultVariableReferenceExpr( prefix,
                                                 variable );
    }

    public Step createNameStep( int axis,
                                String prefix,
                                String localName ) throws JaxenException
    {
        IterableAxis iter = getIterableAxis( axis );
        return new DefaultNameStep( iter,
                                    prefix,
                                    localName,
                                    createPredicateSet() );
    }

    public Step createTextNodeStep( int axis ) throws JaxenException
    {
        IterableAxis iter = getIterableAxis( axis );
        return new DefaultTextNodeStep( iter, createPredicateSet() );
    }

    public Step createCommentNodeStep( int axis ) throws JaxenException
    {
        IterableAxis iter = getIterableAxis( axis );
        return new DefaultCommentNodeStep( iter, createPredicateSet() );
    }

    public Step createAllNodeStep( int axis ) throws JaxenException
    {
        IterableAxis iter = getIterableAxis( axis );
        return new DefaultAllNodeStep( iter, createPredicateSet() );
    }

    public Step createProcessingInstructionNodeStep( int axis,
                                                     String piName ) throws JaxenException
    {
        IterableAxis iter = getIterableAxis( axis );
        return new DefaultProcessingInstructionNodeStep( iter,
                                                         piName,
                                                         createPredicateSet() );
    }

    public Predicate createPredicate( Expr predicateExpr ) throws JaxenException
    {
        return new DefaultPredicate( predicateExpr );
    }

    protected IterableAxis getIterableAxis( int axis )
    {
        IterableAxis iter = null;
        switch( axis )
        {
            case Axis.CHILD:
                {
                    iter = new IterableChildAxis( axis );
                    break;
                }
            case Axis.DESCENDANT:
                {
                    iter = new IterableDescendantAxis( axis );
                    break;
                }
            case Axis.PARENT:
                {
                    iter = new IterableParentAxis( axis );
                    break;
                }
            case Axis.FOLLOWING_SIBLING:
                {
                    iter = new IterableFollowingSiblingAxis( axis );
                    break;
                }
            case Axis.PRECEDING_SIBLING:
                {
                    iter = new IterablePrecedingSiblingAxis( axis );
                    break;
                }
            case Axis.FOLLOWING:
                {
                    iter = new IterableFollowingAxis( axis );
                    break;
                }
            case Axis.PRECEDING:
                {
                    iter = new IterablePrecedingAxis( axis );
                    break;
                }
            case Axis.ATTRIBUTE:
                {
                    iter = new IterableAttributeAxis( axis );
                    break;
                }
            case Axis.NAMESPACE:
                {
                    iter = new IterableNamespaceAxis( axis );
                    break;
                }
            case Axis.SELF:
                {
                    iter = new IterableSelfAxis( axis );
                    break;
                }
            case Axis.DESCENDANT_OR_SELF:
                {
                    iter = new IterableDescendantOrSelfAxis( axis );
                    break;
                }
            case Axis.ANCESTOR_OR_SELF:
                {
                    iter = new IterableAncestorOrSelfAxis( axis );
                    break;
                }
            case Axis.ANCESTOR:
                {
                    iter = new IterableAncestorAxis( axis );
                    break;
                }
        }
        return iter;
    }

    public PredicateSet createPredicateSet() throws JaxenException
    {
        return new PredicateSet();
    }
}
