/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/ext/UpperFunction.java,v 1.11 2006/02/05 21:47:42 elharo Exp $
 * $Revision: 1.11 $
 * $Date: 2006/02/05 21:47:42 $
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
 * $Id: UpperFunction.java,v 1.11 2006/02/05 21:47:42 elharo Exp $
 */

package org.orbeon.jaxen.function.ext;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;
import org.orbeon.jaxen.function.StringFunction;

import java.util.List;
import java.util.Locale;

/**
 * <p><code><i>string</i> upper-case(<i>string</i>)</code>
 *
 * This function can take a second parameter of the
 * <code>Locale</code> to use for the String conversion.
 * </p>
 *
 * <p>
 * For example
 *
 * <code>upper-case( /foo/bar )</code>
 * <code>upper-case( /foo/@name, $myLocale )</code>
 * </p>
 *
 * @author mark wilson (markw@wilsoncom.de)
 * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 */
public class UpperFunction extends LocaleFunctionSupport
{
    public Object call(Context context,
                       List args) throws FunctionCallException
    {
        Navigator navigator = context.getNavigator();
        int size = args.size();
        if (size > 0)
        {
            Object text = args.get(0);
            Locale locale = null;
            if (size > 1)
            {
                locale = getLocale( args.get(1), navigator );
            }
            return evaluate( text, locale, navigator );
        }
        throw new FunctionCallException( "upper-case() requires at least one argument." );
    }

    /**
     * Converts the given string value to upper case using an optional Locale
     *
     * @param strArg the value which gets converted to a String
     * @param locale the Locale to use for the conversion or null if
     *        English should be used
     * @param nav the Navigator to use
     */
    public static String evaluate(Object strArg,
                                  Locale locale,
                                  Navigator nav)
    {

        String str   = StringFunction.evaluate( strArg,
                                                nav );
        // it might be possible to use the xml:lang attribute to
        // pick a default locale
        if (locale == null) locale = Locale.ENGLISH;
        return str.toUpperCase(locale);
    }
}
