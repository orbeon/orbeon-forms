/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/FunctionCallException.java,v 1.13 2006/05/03 16:07:03 elharo Exp $
 * $Revision: 1.13 $
 * $Date: 2006/05/03 16:07:03 $
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
 * $Id: FunctionCallException.java,v 1.13 2006/05/03 16:07:03 elharo Exp $
 */


package org.orbeon.jaxen;

/** <code>FunctionCallException</code> is thrown if an exception
 * occurs during the evaluation of a function.
 * This exception may include a root exception, such as if the
 * real exception was failure to load an XML document via the
 * document() function call.
 *
 * @author bob mcwhirter (bob @ werken.com)
 * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 */
public class FunctionCallException extends JaxenException
{

    /**
     *
     */
    private static final long serialVersionUID = 7908649612495640943L;

    /**
     * Create a new FunctionCallException with the specified detail message.
     *
     * @param message the detail message
     */
    public FunctionCallException(String message) {
        super( message );
    }

    /**
     * Create a new FunctionCallException with the specified root cause.
     *
     * @param nestedException the cause of this exception
     */
    public FunctionCallException(Throwable nestedException) {
        super( nestedException );
    }

    /**
     * Create a new FunctionCallException with the specified detail message
     * and root cause.
     *
     * @param message the detail message
     * @param nestedException the cause of this exception
     */
    public FunctionCallException(String message, Exception nestedException) {
        super( message, nestedException );
    }

    /**
     * <p>
     * Returns the exception that caused this function call to fail.
     * Use getCause() instead.
     * </p>
     *
     * @return the exception that caused this fucntion call to fail
     *
     * @deprecated replaced by {@link #getCause()}
     */
    public Throwable getNestedException() {
        return getCause();
    }

}
