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
        logResult(test, "ERR")
        out.println(error.getMessage)
    }

    def addFailure(test: Test, failure: AssertionFailedError): Unit = {
        logResult(test, "FAIL")
        out.println(failure.getMessage)
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
