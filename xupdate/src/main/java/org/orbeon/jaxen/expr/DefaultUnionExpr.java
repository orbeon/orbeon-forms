/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/expr/DefaultUnionExpr.java,v 1.16 2006/05/03 16:07:02 elharo Exp $
 * $Revision: 1.16 $
 * $Date: 2006/05/03 16:07:02 $
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
 * $Id: DefaultUnionExpr.java,v 1.16 2006/05/03 16:07:02 elharo Exp $
 */



package org.orbeon.jaxen.expr;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.JaxenException;
import org.orbeon.jaxen.XPathSyntaxException;

import java.util.*;

/**
 * @deprecated this class will become non-public in the future;
 *     use the interface instead
 */
public class DefaultUnionExpr extends DefaultBinaryExpr implements UnionExpr
{
    /**
     *
     */
    private static final long serialVersionUID = 7629142718276852707L;

    public DefaultUnionExpr(Expr lhs,
                            Expr rhs)
    {
        super( lhs,
               rhs );
    }

    public String getOperator()
    {
        return "|";
    }

    public String toString()
    {
        return "[(DefaultUnionExpr): " + getLHS() + ", " + getRHS() + "]";
    }

    public Object evaluate(Context context) throws JaxenException
    {
        List results = new ArrayList();

        try {
            List lhsResults = (List) getLHS().evaluate( context );
            List rhsResults = (List) getRHS().evaluate( context );

            Set unique = new HashSet();

            results.addAll( lhsResults );
            unique.addAll( lhsResults );

            Iterator rhsIter = rhsResults.iterator();

            while ( rhsIter.hasNext() )
            {
                Object each = rhsIter.next();

                if ( ! unique.contains( each ) )
                {
                    results.add( each );
                    unique.add( each );
                }
            }

            Collections.sort(results, new NodeComparator(context.getNavigator()));

            return results;
        }
        catch (ClassCastException e) {
            throw new XPathSyntaxException(this.getText(), context.getPosition(), "Unions are only allowed over node-sets");
        }
    }

    public void accept(Visitor visitor)
    {
        visitor.visit(this);
    }

}

