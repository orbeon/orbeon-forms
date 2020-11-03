package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.model.InstanceCaching


trait XFormsServerSharedInstancesCacheTrait {

  type InstanceLoader = (String, Boolean) => DocumentNodeInfoType

  // Try to find instance content in the cache but do not attempt to load it if not found
  def findContentOrNull(
      instance        : Instance,
      instanceCaching : InstanceCaching,
      readonly        : Boolean)(implicit
      indentedLogger  : IndentedLogger
  ): DocumentNodeInfoType

  // Try to find instance content in the cache or load it
  def findContentOrLoad(
      instance        : Instance,
      instanceCaching : InstanceCaching,
      readonly        : Boolean,
      loadInstance    : InstanceLoader)(implicit
      indentedLogger  : IndentedLogger
  ): DocumentNodeInfoType

  // Remove the given entry from the cache if present
  def remove(
    instanceSourceURI : String,
    requestBodyHash   : String,
    handleXInclude    : Boolean)(implicit
    indentedLogger    : IndentedLogger
  ): Unit

  // Empty the cache
  def removeAll(implicit indentedLogger: IndentedLogger): Unit
}
