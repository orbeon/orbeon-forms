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
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.XPathDependencies

import scala.util.control.NonFatal

// Product version information
// NOTE: This could be a trait, but this causes difficulties to XPath callers to reach `object Version` functions.
abstract class Version {

  import Version._

  def requirePEFeature(featureName: String)
  def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean
  def createUIDependencies(containingDocument: XFormsContainingDocument): XPathDependencies

  override def toString = VersionString
}

object Version {

  private val PossibleEditions = Set("CE", "PE")

  val VersionNumber = org.orbeon.oxf.common.BuildInfo.orbeonVersion
  val Edition       = org.orbeon.oxf.common.BuildInfo.orbeonEdition

  val VersionString = "Orbeon Forms " + VersionNumber + ' ' + Edition

  def versionStringIfAllowed =
    Properties.instance.getPropertySet.getBoolean("oxf.show-version", default = false) option VersionString

  // For XPath callers
  def versionStringIfAllowedOrEmpty =
    versionStringIfAllowed.orNull

  // For backward compatibility
  def getVersionString =
    versionStringIfAllowedOrEmpty

  val logger = LoggerFactory.createLogger(classOf[Version])

  // Create a Version instance using reflection
  lazy val instance: Version = {

    val versionClassName = "org.orbeon.oxf.common." + Edition + "Version"

    def logContextClassLoaderIssue[T](message: String): PartialFunction[Throwable, Option[T]] = {
      case NonFatal(t) ⇒
        logger.info(message, t)
        None
    }

    def contextClassLoaderOpt =
      try Option(Thread.currentThread.getContextClassLoader)
      catch logContextClassLoaderIssue("Failed to obtain context ClassLoader")

    def fromContextClassLoaderOpt =
      contextClassLoaderOpt flatMap { classLoader ⇒
        try Some(classLoader.loadClass(versionClassName).asInstanceOf[Class[Version]])
        catch logContextClassLoaderIssue("Failed to load Version from context ClassLoader")
      }

    def fromName =
      Class.forName(versionClassName).asInstanceOf[Class[Version]]

    fromContextClassLoaderOpt getOrElse fromName newInstance
  }

  //@XPathFunction
  def isPE = Edition == "PE"
}
