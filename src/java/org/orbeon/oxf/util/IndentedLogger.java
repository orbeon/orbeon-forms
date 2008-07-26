/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.orbeon.saxon.om.FastStringBuffer;

public class IndentedLogger {

    private Logger logger;
    private String prefix;
    private int logIndentLevel = 0;

    public IndentedLogger(Logger logger, String prefix) {
        this.logger = logger;
        this.prefix = prefix;
    }

    public final boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void startHandleOperation() {
        logIndentLevel++;
    }

    public void endHandleOperation() {
        logIndentLevel--;
    }

    private static String getLogIndentSpaces(int level) {
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < level; i++)
            sb.append("  ");
        return sb.toString();
    }

    public void logDebug(String type, String message) {
        log(Level.DEBUG, logIndentLevel, type, message, null);
    }

    public void logDebug(String type, String message, String[] parameters) {
        log(Level.DEBUG, logIndentLevel, type, message, parameters);
    }

    public static void logDebugStatic(Logger logger, String prefix, String type, String message, String[] parameters) {
        log(logger, Level.DEBUG, 0, prefix, type, message, parameters);
    }

    public void logWarning(String type, String message, String[] parameters) {
        log(Level.WARN, logIndentLevel, type, message, parameters);
    }

    private void log(Level level, int indentLevel, String type, String message, String[] parameters) {
        log(logger, level, indentLevel, prefix, type, message, parameters);
    }

    private static void log(Logger logger, Level level, int indentLevel, String prefix, String type, String message, String[] parameters) {
        final String parametersString;
        if (parameters != null) {
            final FastStringBuffer sb = new FastStringBuffer(" {");
            if (parameters != null) {
                boolean first = true;
                for (int i = 0; i < parameters.length; i += 2) {
                    final String paramName = parameters[i];
                    final String paramValue = parameters[i + 1];

                    if (paramValue != null) {
                        if (!first)
                            sb.append(", ");

                        sb.append(paramName);
                        sb.append(": \"");
                        sb.append(paramValue);
                        sb.append('\"');

                        first = false;
                    }
                }
            }
            sb.append('}');
            parametersString = sb.toString();
        } else {
            parametersString = "";
        }

        logger.log(level, prefix + " - " + getLogIndentSpaces(indentLevel) + type + " - " + message + parametersString);
    }
}
