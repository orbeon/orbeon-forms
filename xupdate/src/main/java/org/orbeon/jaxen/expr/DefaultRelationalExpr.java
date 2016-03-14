/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/expr/DefaultRelationalExpr.java,v 1.14 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.14 $
 * $Date: 2006/02/05 21:47:40 $
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
 * $Id: DefaultRelationalExpr.java,v 1.14 2006/02/05 21:47:40 elharo Exp $
 */



package org.orbeon.jaxen.expr;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.JaxenException;
import org.orbeon.jaxen.Navigator;
import org.orbeon.jaxen.function.NumberFunction;

import java.util.Iterator;
import java.util.List;

abstract class DefaultRelationalExpr extends DefaultTruthExpr implements RelationalExpr
  {
  DefaultRelationalExpr( Expr lhs, Expr rhs )
    {
    super( lhs, rhs );
    }

  public String toString()
    {
    return "[(DefaultRelationalExpr): " + getLHS() + ", " + getRHS() + "]";
    }

  public Object evaluate( Context context ) throws JaxenException
    {
    Object lhsValue = getLHS().evaluate( context );
    Object rhsValue = getRHS().evaluate( context );
    Navigator nav = context.getNavigator();

    if( bothAreSets( lhsValue, rhsValue ) )
      {
      return evaluateSetSet( (List) lhsValue, (List) rhsValue, nav );
      }

    if( eitherIsSet( lhsValue, rhsValue ) )
      {
      if( isSet( lhsValue ) )
        {
        return evaluateSetSet( (List) lhsValue, convertToList( rhsValue ), nav );
        }
      else
        {
        return evaluateSetSet( convertToList( lhsValue ), (List) rhsValue, nav );
        }
      }

    return evaluateObjectObject( lhsValue, rhsValue, nav ) ? Boolean.TRUE : Boolean.FALSE;
    }

  private Object evaluateSetSet( List lhsSet, List rhsSet, Navigator nav )
    {
    if( setIsEmpty( lhsSet ) || setIsEmpty( rhsSet ) ) // return false if either is null or empty
      {
      return Boolean.FALSE;
      }

    for( Iterator lhsIterator = lhsSet.iterator(); lhsIterator.hasNext(); )
      {
      Object lhs = lhsIterator.next();

      for( Iterator rhsIterator = rhsSet.iterator(); rhsIterator.hasNext(); )
        {
        Object rhs = rhsIterator.next();

        if( evaluateObjectObject( lhs, rhs, nav ) )
          {
          return Boolean.TRUE;
          }
        }
      }

    return Boolean.FALSE;
    }

  private boolean evaluateObjectObject( Object lhs, Object rhs, Navigator nav )
    {
    if( lhs == null || rhs == null )
      {
      return false;
      }

    Double lhsNum = NumberFunction.evaluate( lhs, nav );
    Double rhsNum = NumberFunction.evaluate( rhs, nav );

    if( NumberFunction.isNaN( lhsNum ) || NumberFunction.isNaN( rhsNum ) )
      {
      return false;
      }

    return evaluateDoubleDouble( lhsNum, rhsNum );
    }

  protected abstract boolean evaluateDoubleDouble( Double lhs, Double rhs );
  }

