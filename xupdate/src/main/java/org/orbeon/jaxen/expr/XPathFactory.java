/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/expr/XPathFactory.java,v 1.11 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.11 $
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
 * $Id: XPathFactory.java,v 1.11 2006/02/05 21:47:40 elharo Exp $
 */
package org.orbeon.jaxen.expr;

import org.orbeon.jaxen.JaxenException;

public interface XPathFactory
{
    XPathExpr createXPath( Expr rootExpr ) throws JaxenException;

    PathExpr createPathExpr( FilterExpr filterExpr,
                             LocationPath locationPath ) throws JaxenException;

    LocationPath createRelativeLocationPath() throws JaxenException;

    LocationPath createAbsoluteLocationPath() throws JaxenException;

    BinaryExpr createOrExpr( Expr lhs,
                             Expr rhs ) throws JaxenException;

    BinaryExpr createAndExpr( Expr lhs,
                              Expr rhs ) throws JaxenException;

    BinaryExpr createEqualityExpr( Expr lhs,
                                   Expr rhs,
                                   int equalityOperator ) throws JaxenException;

    BinaryExpr createRelationalExpr( Expr lhs,
                                     Expr rhs,
                                     int relationalOperator ) throws JaxenException;

    BinaryExpr createAdditiveExpr( Expr lhs,
                                   Expr rhs,
                                   int additiveOperator ) throws JaxenException;

    BinaryExpr createMultiplicativeExpr( Expr lhs,
                                         Expr rhs,
                                         int multiplicativeOperator ) throws JaxenException;

    Expr createUnaryExpr( Expr expr,
                          int unaryOperator ) throws JaxenException;

    UnionExpr createUnionExpr( Expr lhs,
                               Expr rhs ) throws JaxenException;

    FilterExpr createFilterExpr( Expr expr ) throws JaxenException;

    FunctionCallExpr createFunctionCallExpr( String prefix,
                                             String functionName ) throws JaxenException;

    NumberExpr createNumberExpr( int number ) throws JaxenException;

    NumberExpr createNumberExpr( double number ) throws JaxenException;

    LiteralExpr createLiteralExpr( String literal ) throws JaxenException;

    VariableReferenceExpr createVariableReferenceExpr( String prefix,
                                                       String variableName ) throws JaxenException;

    Step createNameStep( int axis,
                         String prefix,
                         String localName ) throws JaxenException;

    Step createAllNodeStep( int axis ) throws JaxenException;

    Step createCommentNodeStep( int axis ) throws JaxenException;

    Step createTextNodeStep( int axis ) throws JaxenException;

    Step createProcessingInstructionNodeStep( int axis,
                                              String name ) throws JaxenException;

    Predicate createPredicate( Expr predicateExpr ) throws JaxenException;

    PredicateSet createPredicateSet() throws JaxenException;
}
