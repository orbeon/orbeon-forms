/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/saxpath/XPathSyntaxException.java,v 1.10 2006/05/03 16:07:03 elharo Exp $
 * $Revision: 1.10 $
 * $Date: 2006/05/03 16:07:03 $
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
 * $Id: XPathSyntaxException.java,v 1.10 2006/05/03 16:07:03 elharo Exp $
 */

package org.orbeon.jaxen.saxpath;


/**
 * Represents a syntax error in an XPath expression.
 * This is a compile-time error that is detectable irrespective of
 * the context in which the XPath expression is evaluated.
 */
public class XPathSyntaxException extends SAXPathException
{
    /**
     *
     */
    private static final long serialVersionUID = 3567675610742422397L;
    private String xpath;
    private int    position;
    private final static String lineSeparator = System.getProperty("line.separator");

    /**
     * Creates a new XPathSyntaxException.
     *
     * @param xpath the incorrect XPath expression
     * @param position the index of the character at which the syntax error was detected
     * @param message the detail message
     */
    public XPathSyntaxException(String xpath,
                                int position,
                                String message)
    {
        super( message );
        this.position = position;
        this.xpath    = xpath;
    }

    /**
     * <p>
     * Returns the index of the character at which the syntax error was detected
     * in the XPath expression.
     * </p>
     *
     * @return the character index in the XPath expression
     *     at which the syntax error was detected
     */
    public int getPosition()
    {
        return this.position;
    }

    /**
     * <p>
     * Returns the syntactically incorrect XPath expression.
     * </p>
     *
     * @return the syntactically incorrect XPath expression
     */
    public String getXPath()
    {
        return this.xpath;
    }

    public String toString()
    {
        return getClass() + ": " + getXPath() + ": " + getPosition() + ": " + getMessage();
    }

    /**
     * <p>
     * Returns a string in the form <code>"   ^"</code> which, when placed on the line
     * below the XPath expression in a monospaced font, should point to the
     * location of the error.
     * </p>
     *
     * @return the position marker
     */
    private String getPositionMarker()
    {
        int pos = getPosition();
        StringBuffer buf = new StringBuffer(pos+1);
        for ( int i = 0 ; i < pos ; ++i )
        {
            buf.append(" ");
        }

        buf.append("^");

        return buf.toString();

    }

    /**
     * <p>
     * Returns a long formatted description of the error,
     * including line breaks.
     * </p>
     *
     * @return a longer description of the error on multiple lines
     */
    public String getMultilineMessage()
    {
        StringBuffer buf = new StringBuffer();

        buf.append( getMessage() );
        buf.append( lineSeparator );
        buf.append( getXPath() );
        buf.append( lineSeparator );

        buf.append( getPositionMarker() );

        return buf.toString();
    }

}
