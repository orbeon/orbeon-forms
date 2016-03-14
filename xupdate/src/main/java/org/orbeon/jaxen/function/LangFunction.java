/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/LangFunction.java,v 1.13 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.13 $
 * $Date: 2006/02/05 21:47:41 $
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
 * $Id: LangFunction.java,v 1.13 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen.function;

import org.orbeon.jaxen.*;

import java.util.Iterator;
import java.util.List;

/**
 * <p>
 * <b>4.3</b> <code><i>boolean</i> lang(<i>string</i>)</code>
 * </p>
 *
 * <blockquote src="http://www.w3.org/TR/xpath#function-lang">
 * <p>
 * The <b>lang</b> function returns true or false depending on whether
 * the language of the context node as specified by
 * <code>xml:lang</code> attributes is the same as or is a sublanguage
 * of the language specified by the argument string. The language of the
 * context node is determined by the value of the <code>xml:lang</code>
 *
 * attribute on the context node, or, if the context node has no
 * <code>xml:lang</code> attribute, by the value of the
 * <code>xml:lang</code> attribute on the nearest ancestor of the
 * context node that has an <code>xml:lang</code> attribute. If there
 * is no such attribute, then <b><a href="#function-lang">lang</a></b>
 * returns false. If there is such an attribute, then <b><a
 * href="#function-lang">lang</a></b> returns true if the attribute
 * value is equal to the argument ignoring case, or if there is some
 * suffix starting with <code>-</code> such that the attribute value
 * is equal to the argument ignoring that suffix of the attribute value
 * and ignoring case. For example, <code>lang("en")</code> would
 * return true if the context node is any of these five elements:
 * </p>
 *
 * <pre>
 *  &lt;para xml:lang=&quot;en&quot;/&gt;
 *  &lt;div xml:lang=&quot;en&quot;&gt;&lt;para/&gt;&lt;/div&gt;
 *  &lt;para xml:lang=&quot;EN&quot;/&gt;
 *  &lt;para xml:lang=&quot;en-us&quot;/&gt;
 * </pre>
 *
 * </blockquote>
 *
 * @author Attila Szegedi (szegedia @ freemail.hu)
 * @see <a href="http://www.w3.org/TR/xpath#function-lang"
 *      target="_top">XPath Specification</a>
 */
public class LangFunction implements Function
{
    private static final String LANG_LOCALNAME = "lang";
    private static final String XMLNS_URI =
        "http://www.w3.org/XML/1998/namespace";


    /**
     * Create a new <code>LangFunction</code> object.
     */
    public LangFunction() {}


    /**
     * <p>
     * Determines whether or not the context node is written in the language specified
     * by the XPath string-value of <code>args.get(0)</code>,
     * as determined by the nearest <code>xml:lang</code> attribute in scope.
     * </p>
     *
     * @param context the context in which to evaluate the <code>lang()</code> function
     * @param args the arguments to the lang function
     * @return a <code>Boolean</code> indicating whether the context node is written in
     *     the specified language
     * @throws FunctionCallException if <code>args</code> does not have length one
     *
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        if (args.size() != 1) {
            throw new FunctionCallException("lang() requires exactly one argument.");
        }

        Object arg = args.get(0);

        try {
            return evaluate(context.getNodeSet(), arg, context.getNavigator() );
        }
        catch(UnsupportedAxisException e) {
            throw new FunctionCallException("Can't evaluate lang()",
                                                 e);
        }

    }

    private static Boolean evaluate(List contextNodes, Object lang, Navigator nav)
      throws UnsupportedAxisException
    {
        return evaluate(contextNodes.get(0),
            StringFunction.evaluate(lang, nav), nav)
            ? Boolean.TRUE : Boolean.FALSE;
    }

    private static boolean evaluate(Object node, String lang, Navigator nav)
      throws UnsupportedAxisException
    {

        Object element = node;
        if (! nav.isElement(element)) {
            element = nav.getParentNode(node);
        }
        while (element != null && nav.isElement(element))
        {
            Iterator attrs = nav.getAttributeAxisIterator(element);
            while(attrs.hasNext())
            {
                Object attr = attrs.next();
                if(LANG_LOCALNAME.equals(nav.getAttributeName(attr)) &&
                   XMLNS_URI.equals(nav.getAttributeNamespaceUri(attr)))
                {
                    return
                        isSublang(nav.getAttributeStringValue(attr), lang);
                }
            }
            element = nav.getParentNode(element);
        }
        return false;
    }

    private static boolean isSublang(String sublang, String lang)
    {
        if(sublang.equalsIgnoreCase(lang))
        {
            return true;
        }
        int ll = lang.length();
        return
            sublang.length() > ll &&
            sublang.charAt(ll) == '-' &&
            sublang.substring(0, ll).equalsIgnoreCase(lang);
    }

}

