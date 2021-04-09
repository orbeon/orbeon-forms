package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.model.InstanceCaching


trait XFormsServerSharedInstancesCacheTrait {

  type InstanceLoader = (String, Boolean) => DocumentNodeInfoType

  // Try to find instance content in the cache but do not attempt to load it if not found
  def findContent(
    instanceCaching  : InstanceCaching,
    readonly         : Boolean,
    exposeXPathTypes : Boolean)(implicit
    indentedLogger   : IndentedLogger
  ): Option[DocumentNodeInfoType]

  // Try to find instance content in the cache or load it
  def findContentOrLoad(
    instanceCaching  : InstanceCaching,
    readonly         : Boolean,
    exposeXPathTypes : Boolean,
    loadInstance     : InstanceLoader)(implicit
    indentedLogger   : IndentedLogger
  ): DocumentNodeInfoType

  // Remove the given entry from the cache if present
  def remove(
    instanceSourceURI : String,
    requestBodyHash   : String,
    handleXInclude    : Boolean,
    ignoreQueryString : Boolean)(implicit
    indentedLogger    : IndentedLogger
  ): Unit

  // Empty the cache
  def removeAll(implicit indentedLogger: IndentedLogger): Unit
}
