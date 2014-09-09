/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.junit

import java.io.OutputStream
import java.io.PrintStream
import junit.framework.AssertionFailedError
import junit.framework.Test
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest

class LiveResultFormatter extends JUnitResultFormatter {

    private var out = System.out

    def addError(test: Test, error: Throwable): Unit = {
        logResult(test, "FAIL")
        error.printStackTrace(out) // TODO: use orbeon-errorified API/jar
    }

    def addFailure(test: Test, failure: AssertionFailedError): Unit = {
        addError(test, failure)
    }

    def endTest(test: Test): Unit = {
        logResult(test, "PASS")
    }

    def startTest(test: Test) = ()
    def endTestSuite(testSuite: JUnitTest) = ()
    def setOutput(out: OutputStream): Unit = this.out = new PrintStream(out)
    def setSystemError(text: String) = ()
    def setSystemOutput(text: String) = ()
    def startTestSuite(testSuite: JUnitTest) = ()

    private def logResult(test: Test, result: String): Unit = {
        out.println("[" + result + "] " + test)
        out.flush()
    }
}
