package org.orbeon.saxon.function

import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.value.AtomicValue
import shapeless.syntax.typeable.*


object CoreSupport {

  def property(propertyName: String): Option[AtomicValue] =
    if (propertyName.toLowerCase.contains("password"))
      None
    else {
      CoreCrossPlatformSupport.properties.getObjectOpt(propertyName) map
      SaxonUtils.convertJavaObjectToSaxonObject                      flatMap
      (_.cast[AtomicValue])
    }

  def propertyAsString(propertyName: String): Option[String] =
    property(propertyName) map (_.getStringValue)

  def propertiesStartsWith(propertyName: String): List[AtomicValue] =
    for {
      property <- CoreCrossPlatformSupport.properties.propertiesStartsWith(propertyName)
      if ! property.toLowerCase.contains("password")
    } yield
      SaxonUtils.convertJavaObjectToSaxonObject(property).asInstanceOf[AtomicValue]

  def rewriteResourceURI(uri: String, absolute: Boolean): String =
    CoreCrossPlatformSupport.externalContext.getResponse.rewriteResourceURL(
      uri,
      if (absolute)
        UrlRewriteMode.Absolute
      else
        UrlRewriteMode.AbsolutePathOrRelative
    )
}
