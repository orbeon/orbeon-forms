/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/ext/LocaleFunctionSupport.java,v 1.10 2006/02/05 21:47:42 elharo Exp $
 * $Revision: 1.10 $
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
 * $Id: LocaleFunctionSupport.java,v 1.10 2006/02/05 21:47:42 elharo Exp $
 */

package org.orbeon.jaxen.function.ext;

import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.Navigator;
import org.orbeon.jaxen.function.StringFunction;

import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 * <p>An abstract base class for Locale-specific extension
 * functions. This class provides convenience methods that
 * can be inherited, specifically to find a Locale from
 * an XPath function argument value.
 * </p>
 *
 * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 */
public abstract class LocaleFunctionSupport implements Function
{

    /**
     * Attempts to convert the given function argument value
     * into a Locale either via casting, extracting it from a List
     * or looking up the named Locale using reflection.
     *
     * @param value is either a Locale, a List containing a Locale
     *      or a String containing the name of a Locale
     *      as defined by the Locale static members.
     *
     * @return the Locale for the value or null if one could
     *      not be deduced
     */
    protected Locale getLocale(Object value, Navigator navigator)
    {
        if (value instanceof Locale)
        {
            return (Locale) value;
        }
        else if(value instanceof List)
        {
            List list = (List) value;
            if ( ! list.isEmpty() )
            {
                return getLocale( list.get(0), navigator );
            }
        }
        else {
            String text = StringFunction.evaluate( value, navigator );
            if (text != null && text.length() > 0)
            {
                return findLocale( text );
            }
        }
        return null;
    }

    /**
     * Tries to find a Locale instance by name using
     * <a href="http://www.ietf.org/rfc/rfc3066.txt" target="_top">RFC 3066</a>
     * language tags such as 'en', 'en-US', 'en-US-Brooklyn'.
     *
     * @param localeText the RFC 3066 language tag
     * @return the locale for the given text or null if one could not
     *      be found
     */
    protected Locale findLocale(String localeText) {
        StringTokenizer tokens = new StringTokenizer( localeText, "-" );
        if (tokens.hasMoreTokens())
        {
            String language = tokens.nextToken();
            if (! tokens.hasMoreTokens())
            {
                return findLocaleForLanguage(language);
            }
            else
            {
                String country = tokens.nextToken();
                if (! tokens.hasMoreTokens())
                {
                    return new Locale(language, country);
                }
                else
                {
                    String variant = tokens.nextToken();
                    return new Locale(language, country, variant);
                }
            }
        }
        return null;
    }

    /**
     * Finds the locale with the given language name with no country
     * or variant, such as Locale.ENGLISH or Locale.FRENCH
     *
     * @param language the language code to look for
     * @return the locale for the given language or null if one could not
     *      be found
     */
    protected Locale findLocaleForLanguage(String language) {
        Locale[] locales = Locale.getAvailableLocales();
        for ( int i = 0, size = locales.length; i < size; i++ )
        {
            Locale locale = locales[i];
            if ( language.equals( locale.getLanguage() ) )
            {
                String country = locale.getCountry();
                if ( country == null || country.length() == 0 )
                {
                    String variant = locale.getVariant();
                    if ( variant == null || variant.length() == 0 )
                    {
                        return locale;
                    }
                }
            }
        }
        return null;
    }
}
