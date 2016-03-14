/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/function/NormalizeSpaceFunction.java,v 1.17 2006/02/05 21:47:41 elharo Exp $
 * $Revision: 1.17 $
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
 * $Id: NormalizeSpaceFunction.java,v 1.17 2006/02/05 21:47:41 elharo Exp $
 */


package org.orbeon.jaxen.function;

import org.orbeon.jaxen.Context;
import org.orbeon.jaxen.Function;
import org.orbeon.jaxen.FunctionCallException;
import org.orbeon.jaxen.Navigator;

import java.util.List;

/**
 * <p>
 * <b>4.2</b> <code><i>string</i> normalize-space(<i>string</i>)</code>
 * </p>
 *
 * <blockquote src="http://www.w3.org/TR/xpath#function-normalize-space">
 * The <b>normalize-space</b> function
 * returns the argument string with whitespace normalized by stripping
 * leading and trailing whitespace and replacing sequences of whitespace
 * characters by a single space. Whitespace characters are the same as
 * those allowed by the <a href="http://www.w3.org/TR/REC-xml#NT-S" target="_top">S</a>
 * production in XML. If the argument is omitted, it defaults to the
 * context node converted to a string, in other words the <a
 * href="http://www.w3.org/TR/xpath#dt-string-value"
 * target="_top">string-value</a> of the context node.
 * </blockquote>
 *
 * @author James Strachan (james@metastuff.com)
 * @see <a href="http://www.w3.org/TR/xpath#function-normalize-space"
 *      target="_top">Section 4.2 of the XPath Specification</a>
 */
public class NormalizeSpaceFunction implements Function
{


    /**
     * Create a new <code>NormalizeSpaceFunction</code> object.
     */
    public NormalizeSpaceFunction() {}

    /**
     * Returns the string-value of the first item in <code>args</code>
     * after removing all leading and trailing white space, and
     * replacing each other sequence of whitespace by a single space.
     * Whitespace consists of the characters space (0x32), carriage return (0x0D),
     * linefeed (0x0A), and tab (0x09).
     *
     * @param context the context at the point in the
     *         expression when the function is called
     * @param args a list that contains exactly one item
     *
     * @return a normalized <code>String</code>
     *
     * @throws FunctionCallException if <code>args</code> does not have length one
     */
    public Object call(Context context,
                       List args) throws FunctionCallException
    {

        if (args.size() == 0) {
            return evaluate( context.getNodeSet(),
                             context.getNavigator() );
        }
        else if (args.size() == 1)
        {
            return evaluate( args.get(0),
                             context.getNavigator() );
        }

        throw new FunctionCallException( "normalize-space() cannot have more than one argument" );
    }

    /**
     * Returns the string-value of <code>strArg</code> after removing
     * all leading and trailing white space, and
     * replacing each other sequence of whitespace by a single space.
     * Whitespace consists of the characters space (0x32), carriage return (0x0D),
     * linefeed (0x0A), and tab (0x09).
     *
     * @param strArg the object whose string-value is normalized
     * @param nav the context at the point in the
     *         expression when the function is called
     *
     * @return the normalized string-value
     */
    public static String evaluate(Object strArg,
                                  Navigator nav)
    {
        String str = StringFunction.evaluate( strArg,
                                              nav );

        char[] buffer = str.toCharArray();
        int write = 0;
        int lastWrite = 0;
        boolean wroteOne = false;
        int read = 0;
        while (read < buffer.length)
        {
            if (isXMLSpace(buffer[read]))
            {
                if (wroteOne)
                {
                    buffer[write++] = ' ';
                }
                do
                {
                    read++;
                }
                while(read < buffer.length && isXMLSpace(buffer[read]));
            }
            else
            {
                buffer[write++] = buffer[read++];
                wroteOne = true;
                lastWrite = write;
            }
        }

        return new String(buffer, 0, lastWrite);
    }


    private static boolean isXMLSpace(char c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }

}
