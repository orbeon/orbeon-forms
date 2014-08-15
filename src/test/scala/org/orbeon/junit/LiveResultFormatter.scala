package org.orbeon.junit

import java.io.OutputStream
import java.io.PrintStream
import junit.framework.AssertionFailedError
import junit.framework.Test
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest

class LiveResultFormatter extends JUnitResultFormatter {

    private var out = System.out

    override def addError(test: Test, error: Throwable): Unit = {
        logResult(test, "ERR")
        out.println(error.getMessage)
    }

    override def addFailure(test: Test, failure: AssertionFailedError): Unit = {
        logResult(test, "FAIL")
        out.println(failure.getMessage)
    }

    override def endTest(test: Test): Unit = {
        logResult(test, "PASS")
    }

    override def startTest(test: Test): Unit = { }
    override def endTestSuite(testSuite: JUnitTest): Unit = { }
    override def setOutput(out: OutputStream): Unit = this.out = new PrintStream(out)
    override def setSystemError(text: String): Unit = {}
    override def setSystemOutput(text: String): Unit = {}
    override def startTestSuite(testSuite: JUnitTest): Unit = { }

    private def logResult(test: Test, result: String): Unit = {
        out.println("[" + result + "] " + test)
        out.flush()
    }
}
