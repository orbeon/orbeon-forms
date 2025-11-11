package org.orbeon.oxf.fr.persistence.relational

import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.resources.URLFactory

import java.io.{InputStreamReader, StringWriter}

object SqlReader {

  private val Base = "oxf:/apps/fr/persistence/relational/ddl/"

  def read(file: String): Seq[String] = {

    val url = Base ++ file
    val inputStream = URLFactory.createURL(url).openStream()
    val reader = new InputStreamReader(inputStream)
    val writer = new StringWriter
    copyReaderAndClose(reader, writer)
    val fileContentAsString = writer.toString

    val lines = fileContentAsString.split("\n").toList.filter(_ != "/")

    val next = lines.tail.map(Some(_)) :+ None
    val linesWithNext = lines.zip(next)

    val linesWithMarkers = linesWithNext.map { case (line, next) =>
      (line, line.endsWith(";") && (! line.startsWith(" ") || next.forall(_.length == 0)))
    }

    val allStatements = scala.collection.mutable.ArrayBuffer[String]()
    val currentStatement = scala.collection.mutable.ArrayBuffer[String]()
    linesWithMarkers.foreach { case (line, isLastLineOfStatement) =>
      val partToKeep = if (isLastLineOfStatement && ! line.endsWith("END;")) line.substring(0, line.length - 1) else line
      currentStatement += partToKeep
      if (isLastLineOfStatement) {
        allStatements += currentStatement.mkString("\n")
        currentStatement.clear()
      }
    }
    allStatements.toList
  }
}

