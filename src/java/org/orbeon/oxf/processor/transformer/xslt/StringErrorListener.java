/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.transformer.xslt;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.xml.sax.SAXException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMLocator;
import java.lang.reflect.InvocationTargetException;

public class StringErrorListener implements ErrorListener {

    private Logger logger;
    private boolean hasErrors;
    private StringBuffer messages = new StringBuffer();

    public StringErrorListener(Logger logger) {
        this.logger = logger;
    }

    public void warning(TransformerException exception)
        throws TransformerException {

        String message = "Warning: ";
        if (exception.getLocator()!=null)
            message += getLocationMessage(exception) + "\n  ";
        message += getExpandedMessage(exception);
        logger.warn(message);
        messages.append(message + "\n");
    }

    public void error(TransformerException exception) throws TransformerException {
        hasErrors = true;
        String locationMessage = getLocationMessage(exception);
        String message = "Error"
                         + (locationMessage.length() > 0 ? " " + locationMessage + ":\n" : ": ") 
                         + getExpandedMessage(exception);
        logger.error(message);
        messages.append(message + "\n");
    }

    /**
     * Receive notification of a non-recoverable error.
     *
     * <p>The application must assume that the transformation cannot
     * continue after the Transformer has invoked this method,
     * and should continue (if at all) only to collect
     * addition error messages. In fact, Transformers are free
     * to stop reporting events once this method has been invoked.</p>
     *
     * @param exception The error information encapsulated in a
     *                  transformer exception.
     *
     * @throws javax.xml.transform.TransformerException if the application
     * chooses to discontinue the transformation.
     *
     * @see javax.xml.transform.TransformerException
     */

    public void fatalError(TransformerException exception) throws TransformerException {
        error(exception);
        throw exception;
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    public String getMessages() {
        return messages.toString();
    }

    /**
    * Get a string identifying the location of an error.
    */

    private static String getLocationMessage(TransformerException err) {
        try {
            SourceLocator loc = err.getLocator();
            if (loc==null) {
                if(err.getException() instanceof OXFException) {
                    Throwable t = OXFException.getRootThrowable(err.getException());
                    if(t instanceof ValidationException)
                        return ((ValidationException)t).getLocationData().toString();
                    else
                        return ((OXFException)err.getException()).getMessage();
                }
                return  "";
            } else {
                String locmessage = "";
                if (loc instanceof DOMLocator) {
                    locmessage += "at " + ((DOMLocator)loc).getOriginatingNode().getNodeName() + " ";
                } else if (loc.getClass().getName().equals("net.sf.saxon.instruct.InstructionDetails")
                           || loc.getClass().getName().equals("org.orbeon.saxon.instruct.InstructionDetails")) {
                    locmessage += "at "
                            + loc.getClass().getMethod("getInstructionName", new Class[] {}).invoke(loc, new Object[]{})
                            + " ";
                }
                locmessage += "on line " + loc.getLineNumber() + " ";
                if (loc.getColumnNumber() != -1) {
                    locmessage += "column " + loc.getColumnNumber() + " ";
                }
                if (loc.getSystemId() != null) {
                    locmessage += "of " + loc.getSystemId();
                }
                return  locmessage;
            }
        } catch (IllegalAccessException e) {
            throw new OXFException(e);
        } catch (IllegalArgumentException e) {
            throw new OXFException(e);
        } catch (InvocationTargetException e) {
            throw new OXFException(e);
        } catch (NoSuchMethodException e) {
            throw new OXFException(e);
        } catch (SecurityException e) {
            throw new OXFException(e);
        }
    }

    /**
    * Get a string containing the message for this exception and all contained exceptions
    */

    private static String getExpandedMessage(TransformerException err) {
        String message = "";
        Throwable e = err;
        while (true) {
            if (e == null) {
                break;
            }
            String next = e.getMessage();
            if (next==null) next="";
            if (!next.equals("TRaX Transform Exception") && !message.endsWith(next)) {
                if (!message.equals("")) {
                    message += ": ";
                }
                message += OXFException.getRootThrowable(e).getMessage();
            }
            if (e instanceof TransformerException) {
                e = ((TransformerException)e).getException();
            } else if (e instanceof SAXException) {
                e = ((SAXException)e).getException();
            } else {
                // e.printStackTrace();
                break;
            }
        }

        return message;
    }
}
