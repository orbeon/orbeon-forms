package org.orbeon.saxon.function

import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.util.CollectionUtils.collectByErasedType
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.value.AtomicValue


object CoreSupport {

  def property(propertyName: String): Option[AtomicValue] =
    if (propertyName.toLowerCase.contains("password"))
      None
    else {
      CoreCrossPlatformSupport.properties.getObjectOpt(propertyName) map
      SaxonUtils.convertJavaObjectToSaxonObject                      flatMap
      collectByErasedType[AtomicValue]
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
        URLRewriter.REWRITE_MODE_ABSOLUTE
      else
        URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE
    )
}
