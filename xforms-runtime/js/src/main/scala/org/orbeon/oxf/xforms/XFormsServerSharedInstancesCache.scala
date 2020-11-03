package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.model.InstanceCaching


object XFormsServerSharedInstancesCache extends XFormsServerSharedInstancesCacheTrait {

  def findContentOrNull(
      instance        : Instance,
      instanceCaching : InstanceCaching,
      readonly        : Boolean)(implicit
      indentedLogger  : IndentedLogger
  ): DocumentNodeInfoType = ???

  def findContentOrLoad(
      instance        : Instance,
      instanceCaching : InstanceCaching,
      readonly        : Boolean,
      loadInstance    : InstanceLoader)(implicit
      indentedLogger  : IndentedLogger
  ): DocumentNodeInfoType = ???

  def remove(
    instanceSourceURI : String,
    requestBodyHash   : String,
    handleXInclude    : Boolean)(implicit
    indentedLogger    : IndentedLogger
  ): Unit = ???

  def removeAll(implicit indentedLogger: IndentedLogger): Unit = ???
}
