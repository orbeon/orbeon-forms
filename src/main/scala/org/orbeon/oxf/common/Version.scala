/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.common

import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils._

import scala.util.control.NonFatal

// Product version information
// NOTE: This could be a trait, but this causes difficulties to XPath callers to reach `object Version` functions.
abstract class Version {

  import Version._

  def requirePEFeature(featureName: String): Unit
  def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean

  override def toString = VersionString
}

object Version {

  private val PossibleEditions = Set("CE", "PE")

  val VersionNumber : String = org.orbeon.oxf.common.BuildInfo.orbeonVersion
  val Edition       : String = org.orbeon.oxf.common.BuildInfo.orbeonEdition

  val VersionString = "Orbeon Forms " + versionWithEdition

  def versionWithEdition: String = VersionNumber + ' ' + Edition

  def versionStringIfAllowed: Option[String] =
    Properties.instance.getPropertySet.getBoolean("oxf.show-version", default = false) option VersionString

  // From XSLT only.
  //@XPathFunction
  def versionStringIfAllowedOrEmpty: String =
    versionStringIfAllowed.orNull

  // TODO: Remove?
  // For backward compatibility
  def getVersionString =
    versionStringIfAllowedOrEmpty

  val logger = LoggerFactory.createLogger(classOf[Version])

  // From XSLT only.
  //@XPathFunction
  def compare(leftVersion: String, rightVersion: String): Option[Int] =
    VersionSupport.compare(leftVersion, rightVersion)

  // Create a Version instance using reflection
  lazy val instance: Version = {

    val versionClassName = "org.orbeon.oxf.common." + Edition + "Version"

    def logContextClassLoaderIssue[T](message: String): PartialFunction[Throwable, Option[T]] = {
      case NonFatal(t) =>
        logger.info(t)(message)
        None
    }

    def contextClassLoaderOpt =
      try Option(Thread.currentThread.getContextClassLoader)
      catch logContextClassLoaderIssue("Failed to obtain context ClassLoader")

    def fromContextClassLoaderOpt =
      contextClassLoaderOpt flatMap { classLoader =>
        try Some(classLoader.loadClass(versionClassName).asInstanceOf[Class[Version]])
        catch logContextClassLoaderIssue("Failed to load Version from context ClassLoader")
      }

    def fromName =
      Class.forName(versionClassName).asInstanceOf[Class[Version]]

    (fromContextClassLoaderOpt getOrElse fromName).getConstructor().newInstance()
  }

  //@XPathFunction
  def isPE: Boolean = Edition == "PE"
}
