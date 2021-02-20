/**
 *  Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analytics

import org.orbeon.oxf.xforms.XFormsGlobalProperties

import scala.collection.mutable


// Gather request statistics
// For now, only support XPath statistics
trait RequestStats {

  var refreshes             : Int

  var controlsCreated       : Int
  var bindingsUpdated       : Int
  var bindingsRefreshed     : Int

  var eventsDispatched      : Int
  var eventsWithoutHandlers : Int

  var xpathCompiled         : Int
  var xpathEvaluated        : Int

  def afterInitialResponse()
  def afterUpdateResponse()
  def addXPathStat(expr: String, time: Long)
  def withXPath[T](expr: => String)(body: => T): T

  // For Java callers
  def getReporter: (String, Long) => Unit = addXPathStat
}

class RequestStatsImpl extends RequestStats {

  var refreshes             = 0

  var controlsCreated       = 0
  var bindingsUpdated       = 0
  var bindingsRefreshed     = 0

  var eventsDispatched      = 0
  var eventsWithoutHandlers = 0

  var xpathCompiled         = 0
  var xpathEvaluated        = 0

  private class XPathStats(val expr: String) {
    private var _count = 0
    private var _totalTime = 0L

    def addStat(time: Long) = {
      _count += 1
      _totalTime += time
    }

    def count = _count
    def totalTime = _totalTime
    def meanTime = _totalTime / _count

    override def toString =
      s"expr: $expr, count: $count, total time: $totalTime, mean time: $meanTime"
  }

  private val xpathStats = mutable.Map[String, XPathStats]()

  def addXPathStat(expr: String, time: Long): Unit = {
    xpathEvaluated += 1
    xpathStats.getOrElseUpdate(expr, new XPathStats(expr)).addStat(time)
  }

  private def topXPath(n: Int, f: XPathStats => Long) =
    xpathStats.values.toSeq sortBy f takeRight n reverse

  private def distinctXPath = xpathStats.size

  def afterInitialResponse(): Unit =
    afterUpdateResponse()

  def afterUpdateResponse(): Unit = {
    println("afterUpdateResponse statistics:")
    println(s"  refreshes:             $refreshes")
    println(s"  controlsCreated:       $controlsCreated")
    println(s"  bindingsUpdated:       $bindingsUpdated")
    println(s"  bindingsRefreshed:     $bindingsRefreshed")
    println(s"  eventsDispatched:      $eventsDispatched")
    println(s"  eventsWithoutHandlers: $eventsWithoutHandlers")
    println(s"  xpathEvaluated:        $xpathEvaluated")
    println(s"  distinct XPath:        $distinctXPath")
    println(s"  total time in XPath:   ${xpathStats.values.map(_.totalTime).sum}")
    println("  top XPath by mean time: ")
    for ((topXPath, i) <- topXPath(10, _.meanTime).zipWithIndex)
      println(s"    ${i + 1}: ${topXPath.toString}")
    println("  top XPath by total time: ")
    for ((topXPath, i) <- topXPath(10, _.totalTime).zipWithIndex)
      println(s"    ${i + 1}: ${topXPath.toString}")
  }

  def withXPath[T](expr: => String)(body: => T): T = {
    val startTime = System.nanoTime

    val result = body

    val totalTimeMicroSeconds = (System.nanoTime - startTime) / 1000 // never smaller on OS X
    if (totalTimeMicroSeconds > 0)
      addXPathStat(expr, totalTimeMicroSeconds)

    result
  }
}

object NOPRequestStats extends RequestStats {

  var refreshes             = 0

  var controlsCreated       = 0
  var bindingsUpdated       = 0
  var bindingsRefreshed     = 0

  var eventsDispatched      = 0
  var eventsWithoutHandlers = 0

  var xpathCompiled         = 0
  var xpathEvaluated        = 0

  def afterInitialResponse() = ()
  def afterUpdateResponse() = ()
  def addXPathStat(expr: String, time: Long) = ()
  def withXPath[T](expr: => String)(body: => T) = body
}

object RequestStatsImpl {
  def apply(): RequestStats =
    if (XFormsGlobalProperties.isRequestStats)
      new RequestStatsImpl
    else
      NOPRequestStats
}