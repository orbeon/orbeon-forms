/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/expr/Step.java,v 1.10 2006/02/05 21:47:40 elharo Exp $
 * $Revision: 1.10 $
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
 * $Id: Step.java,v 1.10 2006/02/05 21:47:40 elharo Exp $
 */

package org.orbeon.jaxen.expr;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.ContextSupport;
import org.orbeon.jaxen.JaxenException;
import org.orbeon.jaxen.UnsupportedAxisException;

import java.util.Iterator;
import java.util.List;

/**
 * A Step represents a location step in a LocationPath. The node-set selected by
 * the location step is the node-set that results from generating an initial
 * node-set from the axis and node-test, and then filtering that node-set by
 * each of the predicates in turn.
 *
 * The initial node-set consists of the nodes having the relationship to the
 * context node specified by the axis, and having the node type and expanded-name
 * specified by the node test.
 */
public interface Step extends Predicated, Visitable
{

    /**
     * Performs the node-test part of evaluating the step for the given node
     * (which must be on the axis).
     */
    boolean matches(Object node,
                    ContextSupport contextSupport) throws JaxenException;

    String getText();

    void simplify();

    /**
     * Get an identifier for the current axis.
     * @see org.orbeon.jaxen.saxpath.Axis
     */
    public int getAxis();

    /**
     * Get an Iterator for the current axis starting in the given contextNode.
     */
    Iterator axisIterator(Object contextNode,
                          ContextSupport support) throws UnsupportedAxisException;

    /**
     * For each node in the given context calls matches() for every node on the
     * axis, then filters the result by each of the predicates.
     */
    List evaluate(Context context) throws JaxenException;

}

