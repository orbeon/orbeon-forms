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
package org.orbeon.saxon.function

import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CollectionUtils.collectByErasedType
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xml.{DefaultFunctionSupport, RuntimeDependentFunction, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.saxon.value.{AtomicValue, StringValue}
import org.orbeon.scaxon.Implicits._

class Property extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def evaluateItem(xpathContext: XPathContext): AtomicValue =
    Property.property(stringArgument(0)(xpathContext)).orNull
}

object Property {

  def property(propertyName: String): Option[AtomicValue] =
    if (propertyName.toLowerCase.contains("password"))
      None
    else {
      Properties.instance.getPropertySet.getObjectOpt(propertyName) map
      SaxonUtils.convertJavaObjectToSaxonObject                     flatMap
      collectByErasedType[AtomicValue]
    }

  def propertyAsString(propertyName: String): Option[String] =
    property(propertyName) map (_.getStringValue)
}

class PropertiesStartsWith extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator =
    PropertiesStartsWith.propertiesStartsWith(stringArgument(0)(xpathContext))
}

object PropertiesStartsWith {

  def propertiesStartsWith(propertyName: String): List[AtomicValue] =
    for {
      property <- Properties.instance.getPropertySet.propertiesStartsWith(propertyName)
      if ! property.toLowerCase.contains("password")
    } yield
      SaxonUtils.convertJavaObjectToSaxonObject(property).asInstanceOf[AtomicValue]

}

class RewriteResourceURI extends DefaultFunctionSupport {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {
    implicit val ctx = xpathContext
    RewriteResourceURI.rewriteResourceURI(
      uri      = stringArgument(0),
      absolute = booleanArgumentOpt(1) contains true
    )
  }
}

object RewriteResourceURI {
  def rewriteResourceURI(uri: String, absolute: Boolean): String =
    NetUtils.getExternalContext.getResponse.rewriteResourceURL(
      uri,
      if (absolute)
        UrlRewriteMode.Absolute
      else
        UrlRewriteMode.AbsolutePathOrRelative
    )
}

trait EnvironmentVariable extends DefaultFunctionSupport with RuntimeDependentFunction {
  def enabled: Boolean

  override def evaluateItem(xpathContext: XPathContext): StringValue = {
    if (enabled)
      try
        System.getenv(stringArgument(0)(xpathContext))
      catch {
        // If forbidden by the security manager, do as if the `enabled` property was set to `false`
        case _: SecurityException => null
      }
    else
      null
  }
}

class EnvironmentVariableEnabledByProperty extends EnvironmentVariable {
  override def enabled: Boolean =
    Properties.instance.getPropertySet.getBoolean("oxf.xpath.environment-variable.enabled")
}

class EnvironmentVariableAlwaysEnabled extends EnvironmentVariable {
  override val enabled: Boolean = true
}
