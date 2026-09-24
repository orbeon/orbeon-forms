package org.orbeon.saxon.function

import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.value.AtomicValue
import shapeless.syntax.typeable.*

object CoreSupport {

  // Also in `object Property` in `core`
  def property(propertyName: String, profileOpt: Option[String]): Option[AtomicValue] =
    if (PropertySet.isSensitivePropertyName(propertyName))
      None
    else
      CoreCrossPlatformSupport
        .properties
        .getObjectOpt(propertyName, profileOpt)
        .map(SaxonUtils.convertJavaObjectToSaxonObject)
        .flatMap(_.cast[AtomicValue])

  def propertyAsString(propertyName: String, profileOpt: Option[String]): Option[String] =
    property(propertyName, profileOpt).map(_.getStringValue)

  def propertiesStartsWith(propertyName: String): List[AtomicValue] =
    for {
      property <- CoreCrossPlatformSupport.properties.propertiesStartsWith(propertyName)
      if ! PropertySet.isSensitivePropertyName(property)
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
