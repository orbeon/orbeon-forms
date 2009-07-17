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

import java.util.Stack;

public class IndentedLogger {

    private Logger logger;
    private String prefix;
    private int logIndentLevel = 0;
    private Stack<Operation> stack = new Stack<Operation>();

    private class Operation {
        public String type;
        public String message;
        public long startTime;

        public Operation() {
            if (isDebugEnabled()) {
                startTime = System.currentTimeMillis();
            } else {
                startTime = 0;
            }
        }

        public Operation(String type, String message) {
            this();
            this.type = type;
            this.message = message;
        }

        public long getTimeElapsed() {
            return System.currentTimeMillis() - startTime;
        }
    }

    public IndentedLogger(Logger logger, String prefix) {
        this.logger = logger;
        this.prefix = prefix;
    }

    public IndentedLogger(Logger logger, String prefix, int logIndentLevel) {
        this.logger = logger;
        this.prefix = prefix;
        this.logIndentLevel = logIndentLevel;
    }

    public IndentedLogger(IndentedLogger indentedLogger) {
        this.logger = indentedLogger.logger;
        this.prefix = indentedLogger.prefix;
        this.logIndentLevel = indentedLogger.logIndentLevel;
    }

    public int getLogIndentLevel() {
        return logIndentLevel;
    }

    public final boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    public void startHandleOperation() {
        if (logger.isDebugEnabled()) {
            stack.push(null);
        }
        logIndentLevel++;
    }

    public void startHandleOperation(String type, String message) {
        if (logger.isDebugEnabled()) {
            stack.push(new Operation(type, message));
            logDebug(type, "start " + message);
        }
        logIndentLevel++;
    }

    public void startHandleOperation(String type, String message, String... parameters) {
        if (logger.isDebugEnabled()) {
            stack.push(new Operation(type, message));
            logDebug(type, "start " + message, parameters);
        }
        logIndentLevel++;
    }

    public void endHandleOperation() {
        logIndentLevel--;
        if (logger.isDebugEnabled()) {
            final Operation operation = stack.pop();
            if (operation != null) {
                logDebug(operation.type, "end " + operation.message, "time (ms)", Long.toString(operation.getTimeElapsed()));
            }
        }
    }

    public void endHandleOperation(String... parameters) {
        logIndentLevel--;
        if (logger.isDebugEnabled()) {
            final Operation operation = stack.pop();
            if (operation != null) {

                final String[] newParameters = new String[parameters.length + 2];
                newParameters[0] = "time (ms)";
                newParameters[1] = Long.toString(operation.getTimeElapsed());
                System.arraycopy(parameters, 0, newParameters, 2, parameters.length);

                logDebug(operation.type, "end " + operation.message, newParameters);
            }
        }
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

    public void logDebug(String type, String message, String... parameters) {
        log(Level.DEBUG, logIndentLevel, type, message, parameters);
    }

    public static void logDebugStatic(Logger logger, String prefix, String type, String message, String... parameters) {
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

                    if (paramName != null && paramValue != null) {
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
