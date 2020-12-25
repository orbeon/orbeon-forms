package org.orbeon.oxf.xforms.processor

import org.orbeon.dom
import org.orbeon.oxf.http.BasicCredentials
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, SaxonConfiguration}


trait XFormsURIResolver {
  def readAsDom4j(urlString: String, credentials: BasicCredentials): dom.Document
  def readAsTinyTree(configuration: SaxonConfiguration, urlString: String, credentials: BasicCredentials): DocumentNodeInfoType
}
