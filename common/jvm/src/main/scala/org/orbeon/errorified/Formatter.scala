// Distributed under the MIT license, see: http://www.opensource.org/licenses/MIT
package org.orbeon.errorified

// Exception formatter
// This takes a Throwable and formats it into an ASCII table, including error message, application-specific traces,
// and JVM stack traces.
trait Formatter {

    def Width: Int
    def MaxStackLength: Int

    def getThrowableMessage(t: Throwable): Option[String] = Option(t.getMessage)
    def getAllLocationData(t: Throwable): List[SourceLocation] = Nil

    private lazy val OuterHr      = '+' + "-" * (Width - 2) + '+'
    private lazy val InnerHr      = withBorder("-" * (Width - 2))
    private lazy val InnerHrLight = withBorder("·" * (Width - 2))
    private lazy val ScissorsHr   = withBorder("---8<-----" * ((Width - 2 + 9) / 10), Width)

    // Return the top-level error message or a default message
    def message(throwable: Throwable) =
        messageOrDefault(Exceptions.causesIterator(throwable) map Error.apply toList)

    private def messageOrDefault(errors: Seq[Error]) =
        errors.reverse collectFirst { case Error(_, Some(message), _, _) => message } getOrElse "[No error message provided.]"

    // Nicely format an exception into a String printable in a log file
    def format(throwable: Throwable): String = {

        // All nested errors from caused to cause
        val errors = Exceptions.causesIterator(throwable) map Error.apply toList

        // Root message
        val message = messageOrDefault(errors)

        val firstThrowableWithLocation = errors find (_.location.nonEmpty)
        val locations = firstThrowableWithLocation.toList flatMap (_.location)

        def formattedDropCaused(causedTrace: Option[List[String]], e: Error) = {
            val newTrace = e.stackTrace map (_.formatted(Width))

            val commonSize =
                causedTrace match {
                    case Some(causedTrace) => causedTrace zip newTrace takeWhile (pair => pair._1 == pair._2) size
                    case None => 0
                }

            (newTrace, newTrace drop commonSize)
        }

        def allFormattedJavaTraces: List[String] = {

            def truncate(trace: List[String], max: Int) =
                if (trace.size <= max + max / 10) // give it 10% tolerance
                    trace
                else
                    (trace take max / 2) ::: List(ScissorsHr) ::: (trace takeRight max / 2)

            def nextTraces(causedTrace: Option[List[String]], rest: List[Error]): List[String] = rest headOption match {
                case Some(error) =>
                    val (newTrace, newTraceCompact) = formattedDropCaused(causedTrace, error)

                    nextTraces(Some(newTrace), rest.tail) :::
                    InnerHr ::
                    withBorder("Exception: " + error.className, 120) ::
                    InnerHr ::
                    truncate(newTraceCompact.reverse, MaxStackLength)

                case None =>
                    Nil
            }

            nextTraces(None, errors)
        }

        val lines =
            OuterHr ::
            withBorder("An Error has Occurred", Width) ::
            InnerHr ::
            withBorder(wrap(message, Width - 2), Width) ::
            InnerHr ::
            withBorder("Application Call Stack", Width) ::
            InnerHr ::
            (locations flatMap (_.formatted(Width))) :::
            allFormattedJavaTraces :::
            OuterHr ::
            Nil

        "\n" + (lines mkString "\n")
    }

    def wrap(s: String, width: Int) = {
        def truncate(s: String) = s take width
        val restIterator = Iterator.iterate(s)(_ drop width) takeWhile (_.nonEmpty)

        restIterator map truncate mkString "\n"
    }

    // An error (throwable) with optional message, location and trace
    private case class Error(className: String, message: Option[String], location: List[SourceLocation], stackTrace: List[JavaStackEntry])

    private object Error {
        // Create from Throwable
        def apply(throwable: Throwable): Error =
            Error(throwable.getClass.getName,
                getThrowableMessage(throwable) filterNot isBlank,
                getAllLocationData(throwable),
                throwable.getStackTrace.reverseIterator map JavaStackEntry.apply toList)
    }

    private def remainder(l: List[String]) = (l map (_.size) sum) + l.size + 2

    // A source location in a file
    case class SourceLocation(file: String, line: Option[Int], col: Option[Int], description: Option[String], params: List[(String, String)]) {

        require(file ne null)

        def key = file + '-' + line + '-' + line

        // Format as string
        def formatted(width: Int): List[String] = {

            def formattedParams =
                if (params.nonEmpty) {
                    val maxParamNameSize = params collect { case (name, _) => name.size } max

                    def formattedParam = params flatMap { case (name, value) =>
                        val fixed = padded(Some(name), maxParamNameSize)

                        List(withBorder(fixed + '=' + padded(Some(value), width - remainder(List(fixed)))))
                    }

                    InnerHrLight :: formattedParam ::: InnerHr :: Nil
                } else
                    Nil

            val fixed = padded(description, 30) :: paddedInt(line, 4) :: Nil // NOTE: don't put column info as rarely useful

            def location =
                "" :: padded(Some(file), width - remainder(fixed), alignRight = true) :: fixed ::: "" :: Nil mkString "|"

            location :: formattedParams
        }
    }

    // A Java stack entry with optional file and line
    private case class JavaStackEntry(className: String, method: String, file: Option[String], line: Option[Int]) {
        // Format as string
        def formatted(width: Int) = {
            val fixed = padded(Option(method), 30) :: padded(file, 30) :: paddedInt(line, 4) :: Nil

            "" :: padded(Some(className), width - remainder(fixed), alignRight = true) :: fixed ::: "" :: Nil mkString "|"
        }
    }

    private object JavaStackEntry {
        def apply(element: StackTraceElement): JavaStackEntry =
            JavaStackEntry(element.getClassName, element.getMethodName, Option(element.getFileName), filterLineCol(element.getLineNumber))
    }

    private def isBlank(s: String): Boolean = {
        if ((s eq null) || (! s.nonEmpty))
            return true

        for (c <- s)
            if (! Character.isWhitespace(c))
                return false

        true
    }

    def filterLineCol(i: Int) = Option(i) filter (_ > -1)

    private def withBorder(s: String, width: Int): String = s split "\n" map (line => withBorder(padded(Some(line), width - 2))) mkString "\n"
    private def withBorder(s: String): String = '|' + s + '|'
    private def paddedInt(i: Option[Int], len: Int): String = padded(Some(i.getOrElse("").toString.reverse), len).reverse

    private def padded(s: Option[String], len: Int, alignRight: Boolean = false): String = {
        val t = s.getOrElse("")
        if (t.size <= len)
            t.padTo(len, ' ')
        else if (alignRight)
            t.substring(t.size - len, t.size)
        else
            t.substring(0, len)
    }
}
