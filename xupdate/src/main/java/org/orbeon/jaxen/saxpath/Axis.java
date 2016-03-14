/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/saxpath/Axis.java,v 1.8 2006/03/30 13:56:36 elharo Exp $
 * $Revision: 1.8 $
 * $Date: 2006/03/30 13:56:36 $
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
 * $Id: Axis.java,v 1.8 2006/03/30 13:56:36 elharo Exp $
 */



package org.orbeon.jaxen.saxpath;

import org.orbeon.jaxen.JaxenRuntimeException;



/**
 *
 * Internal SAXPath class that contains constants representing
 * XPath operators to avoid a lot of string comparisons.
 */
public class Axis
{

    private Axis() {}

    // XXX Ultimately these should use the type-safe enum pattern instead
    /** Marker for an invalid axis */
    public final static int INVALID_AXIS       =  0;

    /** The <code>child</code> axis */
    public final static int CHILD              =  1;

    /** The <code>descendant</code> axis */
    public final static int DESCENDANT         =  2;

    /** The <code>parent</code> axis */
    public final static int PARENT             =  3;

    /** The <code>ancestor</code> axis */
    public final static int ANCESTOR           =  4;

    /** The <code>following-sibling</code> axis */
    public final static int FOLLOWING_SIBLING  =  5;

    /** The <code>preceding-sibling</code> axis */
    public final static int PRECEDING_SIBLING  =  6;

    /** The <code>following</code> axis */
    public final static int FOLLOWING          =  7;

    /** The <code>preceding</code> axis */
    public final static int PRECEDING          =  8;

    /** The <code>attribute</code> axis */
    public final static int ATTRIBUTE          =  9;

    /** The <code>namespace</code> axis */
    public final static int NAMESPACE          = 10;

    /** The <code>self</code> axis */
    public final static int SELF               = 11;

    /** The <code>descendant-or-self</code> axis */
    public final static int DESCENDANT_OR_SELF = 12;

    /** The <code>ancestor-or-self</code> axis */
    public final static int ANCESTOR_OR_SELF   = 13;

    /**
     * <p>
     * Returns the name of the axis.
     * </p>
     *
     * @param axisNum the axis code
     * @return the name of the axis such as might be used in an XPath expression
     * @throws JaxenRuntimeException if the number does not represent one of the 13
     *     XPath axes
     */
    public static String lookup(int axisNum)
    {
        switch ( axisNum )
        {
            case CHILD:
                return "child";

            case DESCENDANT:
                return "descendant";

            case PARENT:
                return "parent";

            case ANCESTOR:
                return "ancestor";

            case FOLLOWING_SIBLING:
                return "following-sibling";

            case PRECEDING_SIBLING:
                return "preceding-sibling";

            case FOLLOWING:
                return "following";

            case PRECEDING:
                return "preceding";

            case ATTRIBUTE:
                return "attribute";

            case NAMESPACE:
                return "namespace";

            case SELF:
                return "self";

            case DESCENDANT_OR_SELF:
                return "descendant-or-self";

            case ANCESTOR_OR_SELF:
                return "ancestor-or-self";
        }

        throw new JaxenRuntimeException("Illegal Axis Number");
    }

    /**
     * <p>
     * Returns the code for an axis given its name.
     * </p>
     *
     * @param axisName the name of the axis: child, parent, descendant, descendant-or-self, etc.
     * @return the axis code
     */
    public static int lookup(String axisName)
    {

        // XXX All these equals calls are a small HotSpot;
        // Need to replace this with a static HashMap
        if ( "child".equals( axisName ) )
        {
            return CHILD;
        }

        if ( "descendant".equals( axisName ) )
        {
            return DESCENDANT;
        }

        if ( "parent".equals( axisName ) )
        {
            return PARENT;
        }

        if ( "ancestor".equals( axisName ) )
        {
            return ANCESTOR;
        }

        if ( "following-sibling".equals( axisName ) )
        {
            return FOLLOWING_SIBLING;
        }

        if ( "preceding-sibling".equals( axisName ) )
        {
            return PRECEDING_SIBLING;
        }

        if ( "following".equals( axisName ) )
        {
            return FOLLOWING;
        }

        if ( "preceding".equals( axisName ) )
        {
            return PRECEDING;
        }

        if ( "attribute".equals( axisName ) )
        {
            return ATTRIBUTE;
        }

        if ( "namespace".equals( axisName ) )
        {
            return NAMESPACE;
        }

        if ( "self".equals( axisName ) )
        {
            return SELF;
        }

        if ( "descendant-or-self".equals( axisName ) )
        {
            return DESCENDANT_OR_SELF;
        }

        if ( "ancestor-or-self".equals( axisName ) )
        {
            return ANCESTOR_OR_SELF;
        }

        return INVALID_AXIS;
    }
}
