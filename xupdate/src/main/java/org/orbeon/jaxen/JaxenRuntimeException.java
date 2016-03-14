package org.orbeon.jaxen;

import java.io.PrintStream;
import java.io.PrintWriter;

/*
 * $Header: $
 * $Revision: $
 * $Date: $
 *
 * ====================================================================
 *
 * Copyright 2000-2005 bob mcwhirter & James Strachan.
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
 * $Id: $
*/

/**
 * This class exists to wrap Jaxen exceptions that otherwise wouldn't be propagated
 * up through the axis iterators.
 */
public class JaxenRuntimeException extends RuntimeException
{
    /**
     *
     */
    private static final long serialVersionUID = -930309761511911193L;
    private Throwable cause;
    private boolean causeSet = false;

    /**
     * Create a new JaxenRuntimeException.
     *
     * @param cause the nested exception that's wrapped
     *     inside this exception
     */
    public JaxenRuntimeException(Throwable cause)
    {
        super(cause.getMessage());
        initCause(cause);
    }

    /**
     * Create a new JaxenRuntimeException.
     *
     * @param message the detail message
     */
    public JaxenRuntimeException(String message) {
        super(message);
    }

    /**
     * Returns the exception that caused this exception.
     * This is necessary to implement Java 1.4 chained exception
     * functionality in a Java 1.3-compatible way.
     *
     * @return the exception that caused this exception
     */
    public Throwable getCause() {
        return cause;
    }


    /**
     * Sets the exception that caused this exception.
     * This is necessary to implement Java 1.4 chained exception
     * functionality in a Java 1.3-compatible way.
     *
     * @param cause the exception wrapped in this runtime exception
     *
     * @return this exception
     */
    public Throwable initCause(Throwable cause) {
        if (causeSet) throw new IllegalStateException("Cause cannot be reset");
        if (cause == this) throw new IllegalArgumentException("Exception cannot be its own cause");
        causeSet = true;
        this.cause = cause;
        return this;
    }

    /** Print this exception's stack trace, followed by the
     *  source exception's trace, if any.
     *
     * @param s the stream on which to print the stack trace
     */
    public void printStackTrace ( PrintStream s )
    {
        super.printStackTrace ( s );
        if (JaxenException.javaVersion < 1.4 && getCause() != null) {
            s.print( "Caused by: " );
            getCause().printStackTrace( s );
        }
    }

    /** Print this exception's stack trace, followed by the
     *  source exception's stack trace, if any.
     *
     * @param s the writer on which to print the stack trace
     */
    public void printStackTrace ( PrintWriter s )
    {
        super.printStackTrace( s );
        if (JaxenException.javaVersion < 1.4 && getCause() != null) {
            s.print( "Caused by: " );
            getCause().printStackTrace( s );
        }
    }

}
