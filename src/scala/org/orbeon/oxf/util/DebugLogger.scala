/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.util

// More Scala-friendly indented logger API
object DebugLogger {

    // Error with optional parameters
    def error(message: ⇒ String, parameters: ⇒ Seq[(String, String)] = Seq())(implicit logger: IndentedLogger) =
        logger.logError("", message, flattenTuples(parameters): _*)

    // Warn with optional parameters
    def warn(message: ⇒ String, parameters: ⇒ Seq[(String, String)] = Seq())(implicit logger: IndentedLogger) =
        logger.logWarning("", message, flattenTuples(parameters): _*)

    // Info with optional parameters
    def info(message: ⇒ String, parameters: ⇒ Seq[(String, String)] = Seq())(implicit logger: IndentedLogger) =
        if (logger.isInfoEnabled)
            logger.logInfo("", message, flattenTuples(parameters): _*)

    // Debug with optional parameters
    def debug(message: ⇒ String, parameters: ⇒ Seq[(String, String)] = Seq())(implicit logger: IndentedLogger) =
        if (logger.isDebugEnabled)
            logger.logDebug("", message, flattenTuples(parameters): _*)

    // Debug block with optional parameters
    def withDebug[T](message: ⇒ String, parameters: ⇒ Seq[(String, String)] = Seq())(body: ⇒ T)(implicit logger: IndentedLogger): T =
        try {
            if (logger.isDebugEnabled)
                logger.startHandleOperation("", message, flattenTuples(parameters): _*)

            body
        } finally {
            if (logger.isDebugEnabled)
                logger.endHandleOperation()
        }

    // Call from a result block to set result parameters
    def debugResults(parameters: ⇒ Seq[(String, String)])(implicit logger: IndentedLogger) =
        if (logger.isDebugEnabled)
            logger.setDebugResults(flattenTuples(parameters): _*)

    private def flattenTuples(tuples: Seq[(String, String)]) =
        tuples flatMap { case (n, v) ⇒ Seq(n, v) }
}
