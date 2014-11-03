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

import java.util.concurrent.Semaphore
import javax.servlet._
import javax.servlet.http.HttpServletRequest

import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.ScalaUtils._
import org.slf4j.LoggerFactory

import scala.util.matching.Regex

class LimiterFilter extends Filter {

    import LimiterFilter._

    private case class FilterSettings(semaphore: Semaphore, include: Regex, exclude: Regex)

    private var settingsOpt: Option[FilterSettings] = None

    override def init(config: FilterConfig) = {

        val limit = desiredParallelism(config.getInitParameter)

        Logger.info("initializing limiter filter")

        val settings =
            FilterSettings(
                new Semaphore(limit, true),
                (nonEmptyOrNone(config.getInitParameter("include")) getOrElse "$.").r,
                (nonEmptyOrNone(config.getInitParameter("exclude")) getOrElse "$.").r
            )

        Logger.info(s"limiter filter configuration: $settings")

        settingsOpt = Some(settings)
    }

    override def destroy() = {
        Logger.info(s"destroying limiter filter")
        settingsOpt = None
    }

    override def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit =
        settingsOpt foreach { case FilterSettings(semaphore, include, exclude) ⇒

            val httpReq = req.asInstanceOf[HttpServletRequest]

            NetUtils.getRequestPathInfo(httpReq) match {
                case path if include.pattern.matcher(path).matches && ! exclude.pattern.matcher(path).matches ⇒
                    Logger.debug(s"before acquiring semaphore for '$path'")
                    val timestamp = if (Logger.isDebugEnabled) System.nanoTime else -1
                    semaphore.acquire()
                    try {
                        Logger.debug(s"after acquiring semaphore for '$path' (waited ${(System.nanoTime - timestamp) / 1000000} ms)")
                        chain.doFilter(req, res)
                    }  finally
                        semaphore.release()
                case path ⇒
                    Logger.debug(s"skipping semaphore for '$path'")
                    chain.doFilter(req, res)
            }
        }

    // Inspired from Scala code so under Scala license http://www.scala-lang.org/license.html
    // https://github.com/scala/scala/blob/2.11.x/src/library/scala/concurrent/impl/ExecutionContextImpl.scala
    def desiredParallelism(param: String ⇒ String) = {

        def stringParamWithDefault(name: String, default: String) =
            nonEmptyOrNone(param(name)) getOrElse default

        def getInt(name: String, default: String) =
            stringParamWithDefault(name, default) match {
                case s if s.charAt(0) == 'x' ⇒ (Runtime.getRuntime.availableProcessors * s.substring(1).toDouble).ceil.toInt
                case other                   ⇒ other.toInt
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