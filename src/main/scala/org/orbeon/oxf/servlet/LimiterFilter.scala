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
package org.orbeon.oxf.servlet

import org.orbeon.oxf.logging.{LifecycleLogger, MinimalRequest}
import org.orbeon.oxf.util.StringUtils._
import org.slf4j.LoggerFactory

import java.util.concurrent.Semaphore
import scala.util.matching.Regex

// Servlet filter to limit the number of concurrent threads entering the filter chain
//
// Features:
//
// - paths to include/exclude can be configured with regular expressions
// - the minimum, requested, and maximum number of concurrent threads allowed can be configured
//
// By default, the filter sets a limit to the number of CPU cores returned by the JVM. This includes hyper-threading,
// see: http://stackoverflow.com/questions/11738133/is-it-possible-to-check-in-java-if-the-cpu-is-hyper-threading
//
// The threads configuration can be set to multiples of the number of available cores with the `x` prefix, for example:
//
// - x0.5
// - x1.25

// For backward compatibility
class LimiterFilter extends JavaxLimiterFilter

class JavaxLimiterFilter   extends JavaxFilter  (new LimiterFilterImpl)
class JakartaLimiterFilter extends JakartaFilter(new LimiterFilterImpl)

class LimiterFilterImpl extends Filter {

  import LimiterFilter.Logger._

  private case class FilterSettings(semaphore: Semaphore, include: Regex, exclude: Regex)

  private var settingsOpt: Option[FilterSettings] = None

  override def init(config: FilterConfig): Unit = {

    val limit = desiredParallelism(config.getInitParameter)

    info("initializing")

    val settings =
      FilterSettings(
        new Semaphore(limit, true),
        (config.getInitParameter("include").trimAllToOpt getOrElse "$.").r,
        (config.getInitParameter("exclude").trimAllToOpt getOrElse "$.").r
      )

    info(s"configuring: $settings")

    settingsOpt = Some(settings)
  }

  override def destroy(): Unit = {
    info(s"destroying")
    settingsOpt = None
  }

  override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit =
    settingsOpt foreach { case FilterSettings(semaphore, include, exclude) =>

      val httpReq = MinimalRequest(req.asInstanceOf[HttpServletRequest])

      httpReq.getRequestPath match {
        case path if include.pattern.matcher(path).matches && ! exclude.pattern.matcher(path).matches =>
          LifecycleLogger.withEvent(httpReq, "limiter", "filter", Nil) {
            val timestamp = System.currentTimeMillis
            semaphore.acquire()
            try {
              // Log request details again in case wait takes a while
              val logParams = LifecycleLogger.basicRequestDetails(httpReq) ::: List("wait" -> LifecycleLogger.formatDelay(timestamp))
              LifecycleLogger.withEvent(httpReq, "limiter", "chain", logParams) {
                chain.doFilter(req, res)
              }
            } finally
              semaphore.release()
          }
        case path =>
          LifecycleLogger.withEvent(httpReq, "limiter", "nofilter", Nil) {
            chain.doFilter(req, res)
          }
      }
    }

  // Inspired from Scala code so under Scala license http://www.scala-lang.org/license.html
  // https://github.com/scala/scala/blob/2.11.x/src/library/scala/concurrent/impl/ExecutionContextImpl.scala
  private def desiredParallelism(param: String => String) = {

    def stringParamWithDefault(name: String, default: String) =
      param(name).trimAllToOpt getOrElse default

    def getInt(name: String, default: String) =
      stringParamWithDefault(name, default) match {
        case s if s.charAt(0) == 'x' => (Runtime.getRuntime.availableProcessors * s.substring(1).toDouble).ceil.toInt
        case other                   => other.toInt
      }

    def range(floor: Int, desired: Int, ceiling: Int) =
      scala.math.min(scala.math.max(floor, desired), ceiling)

    range(
      getInt("min-threads", "1"),
      getInt("num-threads", "x1"),
      getInt("max-threads", "x1")
    )
  }
}

private object LimiterFilter {
  val Logger = LoggerFactory.getLogger("org.orbeon.filter.limiter")
}