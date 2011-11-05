/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;

import java.util.Stack;

/**
 * Abstraction over log4j, which provides:
 *
 * o start/end operation with parameters
 * o indenting depending on current nesting of operations
 * o custom handling of debug level
 */
public class IndentedLogger {

    private final Logger logger;
    private final Indentation indentation;
    private final String prefix;

    private final boolean debugEnabled;

    private Stack<Operation> stack = new Stack<Operation>();

    public IndentedLogger(Logger logger, String prefix) {
        this(logger, new Indentation(), prefix);
    }

    public IndentedLogger(Logger logger, Indentation indentation, String prefix) {
        this(logger, logger.isDebugEnabled(), indentation, prefix);
    }

    public IndentedLogger(Logger logger, boolean isDebugEnabled, String prefix) {
        this(logger, isDebugEnabled, new Indentation(), prefix);
    }

    public IndentedLogger(Logger logger, boolean debugEnabled, Indentation indentation, String prefix) {
        this.logger = logger;
        this.debugEnabled = debugEnabled;
        this.indentation = indentation;
        this.prefix = prefix;
    }

    /**
     * Create a new IndentedLogger allowing overriding indentation and/or isDebugEnabled.
     *
     * @param indentedLogger    initial logger
     * @param indentation       new indentation to use
     * @param isDebugEnabled    new isDebugEnabled
     */
    public IndentedLogger(IndentedLogger indentedLogger, Indentation indentation, boolean isDebugEnabled) {
        this(indentedLogger.logger, isDebugEnabled, indentation, indentedLogger.prefix);
    }

    public Logger getLogger() {
        return logger;
    }

    public Indentation getIndentation() {
        return indentation;
    }

    public final boolean isDebugEnabled() {
        return debugEnabled;
    }

    public final boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    public void startHandleOperation(String type, String message) {
        if (debugEnabled) {
            stack.push(new Operation(type, message));
            logDebug(type, "start " + message);
            indentation.indentation++;
        }
    }

    public void startHandleOperation(String type, String message, String... parameters) {
        if (debugEnabled) {
            stack.push(new Operation(type, message));
            logDebug(type, "start " + message, parameters);
            indentation.indentation++;
        }
    }

    public void endHandleOperation() {
        if (debugEnabled) {
            indentation.indentation--;
            final Operation operation = stack.pop();
            if (operation != null) {
                logDebug(operation.type, "end " + operation.message, "time (ms)", Long.toString(operation.getTimeElapsed()));
            }
        }
    }

    public void endHandleOperation(String... parameters) {
        if (debugEnabled) {
            indentation.indentation--;
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
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++)
            sb.append("  ");
        return sb.toString();
    }

    public void log(Level level, String type, String message, String... parameters) {
        log(level, indentation.indentation, type, message, parameters);
    }

    public void logDebug(String type, String message) {
        log(Level.DEBUG, indentation.indentation, type, message, (String[]) null);
    }

    public void logDebug(String type, String message, String... parameters) {
        log(Level.DEBUG, indentation.indentation, type, message, parameters);
    }

    public void logDebug(String type, String message, Throwable throwable) {
        log(Level.DEBUG, indentation.indentation, type, message, "throwable", OXFException.throwableToString(throwable));
    }

    public static void logWarningStatic(Logger logger, String prefix, String type, String message, String... parameters) {
        log(logger, Level.WARN, 0, prefix, type, message, parameters);
    }

    public static void logErrorStatic(Logger logger, String prefix, String type, String message, Throwable throwable) {
        log(logger, Level.ERROR, 0, prefix, type, message, "throwable", OXFException.throwableToString(throwable));
    }

    public void logWarning(String type, String message, String... parameters) {
        log(Level.WARN, indentation.indentation, type, message, parameters);
    }

    public void logInfo(String type, String message) {
        log(Level.INFO, indentation.indentation, type, message, (String[]) null);
    }

    public void logInfo(String type, String message, String... parameters) {
        log(Level.INFO, indentation.indentation, type, message, parameters);
    }

    public void logInfo(String type, String message, Throwable throwable) {
        log(Level.INFO, indentation.indentation, type, message, "throwable", OXFException.throwableToString(throwable));
    }

    public void logWarning(String type, String message, Throwable throwable) {
        log(Level.WARN, indentation.indentation, type, message, "throwable", OXFException.throwableToString(throwable));
    }

    public void logError(String type, String message, String... parameters) {
        log(Level.ERROR, indentation.indentation, type, message, parameters);
    }

    public void logError(String type, String message, Throwable throwable) {
        log(Level.ERROR, indentation.indentation, type, message, "throwable", OXFException.throwableToString(throwable));
    }

    private void log(Level level, int indentLevel, String type, String message, String... parameters) {
        if (!(level == Level.DEBUG && !debugEnabled)) // handle DEBUG level locally, everything else goes through
            log(logger, level, getActualIndentLevel(indentLevel), prefix, type, message, parameters);
    }

    private int getActualIndentLevel(int indentLevel) {
        return indentLevel;
    }

    private static void log(Logger logger, Level level, int indentLevel, String prefix, String type, String message, String... parameters) {
        final String parametersString;
        if (parameters != null) {
            final StringBuilder sb = new StringBuilder(" {");
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
            sb.append('}');
            parametersString = sb.toString();
        } else {
            parametersString = "";
        }

        logger.log(level, getLogIndentSpaces(indentLevel) + (org.apache.commons.lang.StringUtils.isNotEmpty(type) ? (type + " - ") : "") + message + parametersString);
//        logger.log(level, prefix + " - " + getLogIndentSpaces(indentLevel) + type + " - " + message + parametersString);
    }

    public static class Indentation {
        public int indentation;

        public Indentation() {
            this(0);
        }

        public Indentation(int indentation) {
            this.indentation = indentation;
        }
    }

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
}
