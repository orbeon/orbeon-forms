/*
 * $Header: /home/projects/jaxen/scm/jaxen/src/java/main/org/jaxen/JaxenException.java,v 1.17 2006/05/03 16:07:03 elharo Exp $
 * $Revision: 1.17 $
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
 * $Id: JaxenException.java,v 1.17 2006/05/03 16:07:03 elharo Exp $
 */


package org.orbeon.jaxen;


/**
 * Generic Jaxen exception.
 *
 * <p> This is the root of all Jaxen exceptions. It may wrap other exceptions.
 *
 * @author <a href="mailto:bob@werken.com">bob mcwhirter</a>
 */
public class JaxenException extends org.orbeon.jaxen.saxpath.SAXPathException
{

    /**
     *
     */
    private static final long serialVersionUID = 7132891439526672639L;
    static double javaVersion = 1.4;

    static {
        try {
            String versionString = System.getProperty("java.version");
            versionString = versionString.substring(0, 3);
            javaVersion = Double.valueOf(versionString).doubleValue();
        }
        catch (RuntimeException ex) {
            // The version string format changed so presumably it's
            // 1.4 or later.
        }
    }

    /**
     * Create an exception with a detail message.
     *
     * @param message the error message
     */
    public JaxenException( String message )
    {
        super( message );
    }

    /**
     * Create an exception caused by another exception.
     *
     * @param rootCause the root cause of this exception
     */
    public JaxenException( Throwable rootCause )
    {
        super( rootCause );
    }

    /**
     * Create a new JaxenException with the specified detail message
     * and root cause.
     *
     * @param message the detail message
     * @param nestedException the cause of this exception
     */
    public JaxenException(String message, Throwable nestedException) {
        super( message, nestedException );
    }

}

