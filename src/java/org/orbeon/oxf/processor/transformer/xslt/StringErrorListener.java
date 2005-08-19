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
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.SAXException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMLocator;
import java.util.List;
import java.util.ArrayList;

public class StringErrorListener implements ErrorListener {

    private Logger logger;
    private boolean hasErrors;
    private List errorLocationData;
    private StringBuffer messages = new StringBuffer();

    public StringErrorListener(Logger logger) {
        this.logger = logger;
    }

    public void warning(TransformerException exception)
            throws TransformerException {

        String message = "Warning: ";
        if (exception.getLocator() != null)
            message += getLocationMessage(exception) + "\n  ";
        message += getExpandedMessage(exception);
        logger.warn(message);
        messages.append(message);
        messages.append("\n");
    }

    public void error(TransformerException exception) throws TransformerException {
        addError(exception);

        String locationMessage = getLocationMessage(exception);
        String message = "Error"
                + (locationMessage.length() > 0 ? " " + locationMessage + ":\n" : ": ")
                + getExpandedMessage(exception);
        logger.error(message);
        messages.append(message);
        messages.append("\n");
    }

    /**
     * Receive notification of a non-recoverable error.
     * <p/>
     * <p>The application must assume that the transformation cannot
     * continue after the Transformer has invoked this method,
     * and should continue (if at all) only to collect
     * addition error messages. In fact, Transformers are free
     * to stop reporting events once this method has been invoked.</p>
     *
     * @param exception The error information encapsulated in a
     *                  transformer exception.
     * @throws javax.xml.transform.TransformerException
     *          if the application
     *          chooses to discontinue the transformation.
     * @see javax.xml.transform.TransformerException
     */

    public void fatalError(TransformerException exception) throws TransformerException {
        error(exception);
        throw exception;
    }

    private void addError(TransformerException exception) {
        hasErrors = true;

        if (errorLocationData == null)
            errorLocationData = new ArrayList();

        final LocationData locationData = getTransformerExceptionLocationData(exception, null);
        if (locationData != null)
            errorLocationData.add(locationData);
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    public String getMessages() {
        return messages.toString();
    }

    public List getErrors() {
        return errorLocationData;
    }

    /**
     * Try to get the best possible location data for a TransformerException.
     *
     * @param te               TransformerException to process
     * @param defaultSystemId  System Id to use if none is found in the TransformerException.
     * @return ExtendedLocationData
     */
    public static ExtendedLocationData getTransformerExceptionLocationData(TransformerException te, String defaultSystemId) {
        final SourceLocator loc = te.getLocator();
        if (loc == null && defaultSystemId == null) {
            return null;
        } else if (loc == null) {
            return new ExtendedLocationData(defaultSystemId, -1, -1, null);
        } else {
            String description;
            if (loc instanceof DOMLocator) {
                description = ((DOMLocator) loc).getOriginatingNode().getNodeName();
            } else if (loc.getClass().getName().equals("net.sf.saxon.instruct.InstructionDetails")
                    || loc.getClass().getName().equals("org.orbeon.saxon.instruct.InstructionDetails")) {
                try {
                    description = loc.getClass().getMethod("getInstructionName", new Class[]{}).invoke(loc, new Object[]{}).toString();
                } catch (Exception e) {
                    // Let's not consider this a real issue, just clear the description
                    description = null;
                }
            } else {
                description = null;
            }
            return new ExtendedLocationData((loc.getSystemId() != null) ? loc.getSystemId() : defaultSystemId, loc.getLineNumber(), loc.getColumnNumber(), description);
        }
    }

    /**
     * Get a string identifying the location of an error.
     */
    private static String getLocationMessage(TransformerException te) {
        final ExtendedLocationData extendedLocationData = getTransformerExceptionLocationData(te, null);
        if (extendedLocationData != null) {
            return "at "
                    + ((extendedLocationData.getDescription() != null) ? extendedLocationData.getDescription() : "")
                    + ((extendedLocationData.getLine() != -1) ? ", line " + extendedLocationData.getLine() : "")
                    + ((extendedLocationData.getCol() != -1) ? ", column " + extendedLocationData.getCol() : "")
                    + ((extendedLocationData.getSystemID() != null) ? " of  " + extendedLocationData.getSystemID() : "");
        } else  if (te.getException() instanceof OXFException) {
            final Throwable t = OXFException.getRootThrowable(te.getException());
            // TODO: check this, should maybe look for root validation data?
            if (t instanceof ValidationException)
                return ((ValidationException) t).getLocationData().toString();
            else
                return ((OXFException) te.getException()).getMessage();
        } else {
            return "";
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
            if (next == null) next = "";
            if (!next.equals("TRaX Transform Exception") && !message.endsWith(next)) {
                if (!message.equals("")) {
                    message += ": ";
                }
                message += OXFException.getRootThrowable(e).getMessage();
            }
            if (e instanceof TransformerException) {
                e = ((TransformerException) e).getException();
            } else if (e instanceof SAXException) {
                e = ((SAXException) e).getException();
            } else {
                // e.printStackTrace();
                break;
            }
        }

        return message;
    }
}
