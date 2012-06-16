/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.exception

// Exception formatter
// This takes a Throwable and formats it into an ASCII table, including error message, application-specific traces,
// and JVM stack traces.
trait Formatter {

    def Width: Int
    def MaxStackLength: Int

    def getThrowableMessage(t: Throwable): Option[String]
    def getAllLocationData(t: Throwable): List[SourceLocation]

    private lazy val OuterHr = '+' + "-" * (Width - 2) + '+'
    private lazy val InnerHr = withBorder("-" * (Width - 2))
    private lazy val DottedHr = withBorder("---8<-----" * ((Width - 2 + 9) / 10), Width)

    // Nicely format an exception into a String printable in a log file
    def format(throwable: Throwable): String = {

        // All nested errors from caused to cause
        val errors = Exceptions.causesIterator(throwable) map (Error(_)) toList

        // Top-level message
        val message = errors.last.message getOrElse "[No error message provided.]"

        val firstThrowableWithLocation = errors find (_.location.nonEmpty)
        val locations = firstThrowableWithLocation.toList flatMap (_.location)

        def formattedDropCaused(causedTrace: Option[List[String]], e: Error) = {
            val newTrace = e.stackTrace map (_.formatted(Width))

            val commonSize =
                causedTrace match {
                    case Some(causedTrace) ⇒ causedTrace zip newTrace takeWhile (pair ⇒ pair._1 == pair._2) size
                    case None ⇒ 0
                }

            (newTrace, newTrace drop commonSize)
        }

        def allFormattedJavaTraces: List[String] = {

            def truncate(trace: List[String], max: Int) =
                if (trace.size <= max + max / 10) // give it 10% tolerance
                    trace
                else
                    (trace take max / 2) ::: List(DottedHr) ::: (trace takeRight max / 2)

            def nextTraces(causedTrace: Option[List[String]], rest: List[Error]): List[String] = rest headOption match {
                case Some(error) ⇒
                    val (newTrace, newTraceCompact) = formattedDropCaused(causedTrace, error)

                    nextTraces(Some(newTrace), rest.tail) :::
                    InnerHr ::
                    withBorder("Exception: " + error.className, 120) ::
                    InnerHr ::
                    truncate(newTraceCompact.reverse, MaxStackLength)

                case None ⇒
                    Nil
            }

            nextTraces(None, errors)
        }

        val lines =
            OuterHr ::
            withBorder("An Error has Occurred in Orbeon Forms", Width) ::
            InnerHr ::
            withBorder(message, Width) ::
            InnerHr ::
            withBorder("Orbeon Forms Call Stack", Width) ::
            InnerHr ::
            (locations map (_.formatted(Width))) :::
            allFormattedJavaTraces :::
            OuterHr ::
            Nil

        "\n" + (lines mkString "\n")
    }

    // An error (throwable) with optional message, location and trace
    private case class Error(className: String, message: Option[String], location: List[SourceLocation], stackTrace: List[JavaStackEntry])

    private object Error {
        // Create from Throwable
        def apply(throwable: Throwable): Error =
            Error(throwable.getClass.getName,
                getThrowableMessage(throwable) filter (! isBlank(_)),
                getAllLocationData(throwable),
                throwable.getStackTrace.reverseIterator map (JavaStackEntry(_)) toList)
    }

    // A source location in a file
    case class SourceLocation(file: String, line: Option[Int], col: Option[Int], description: Option[String], params: List[(String, String)]) {

        require(file ne null)

        def key = file + '-' + line + '-' + line

        // Format as string
        def formatted(width: Int) = {
            val fixed = paddedInt(line, 4) :: paddedInt(col, 4) :: padded(description, 50) :: Nil
            val remainder = (fixed.foldLeft(0)(_ + _.size)) + fixed.size + 2

            ("" :: padded(Some(file), width - remainder) :: fixed ::: "" :: Nil) mkString "|"
        }
    }

    // A Java stack entry with optional file and line
    private case class JavaStackEntry(className: String, method: String, file: Option[String], line: Option[Int]) {
        // Format as string
        def formatted(width: Int) = {
            val fixed = padded(Option(method), 30) :: padded(file, 30) :: paddedInt(line, 4) :: Nil
            val remainder = (fixed.foldLeft(0)(_ + _.size)) + fixed.size + 2

            ("" :: padded(Some(className), width - remainder) :: fixed ::: "" :: Nil) mkString "|"
        }
    }

    private object JavaStackEntry {
        def apply(element: StackTraceElement): JavaStackEntry =
            JavaStackEntry(element.getClassName, element.getMethodName, Option(element.getFileName), filterLineCol(element.getLineNumber))
    }

    private def isBlank(s: String): Boolean = {
        if ((s eq null) || (! s.nonEmpty))
            return true

        for (c ← s)
            if (! Character.isWhitespace(c))
                return false

        true
    }

    def filterLineCol(i: Int) = Option(i) filter (_ > -1)

    private def withBorder(s: String, width: Int): String = s split "\n" map (line ⇒ withBorder(padded(Some(line), width - 2))) mkString "\n"
    private def withBorder(s: String): String = '|' + s + '|'
    private def padded(s: Option[String], len: Int): String = s.getOrElse("").padTo(len, ' ').substring(0, len)
    private def paddedInt(i: Option[Int], len: Int): String = padded(Some(i.getOrElse("").toString.reverse), len).reverse
}
