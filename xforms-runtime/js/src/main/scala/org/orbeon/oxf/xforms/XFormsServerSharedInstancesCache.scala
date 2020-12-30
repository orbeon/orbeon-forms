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
  ): DocumentNodeInfoType = {
    // XXX TODO
    println(s"xxx findContentOrNull TODO for `${instanceCaching.pathOrAbsoluteURI}`")
    null
  }

  def findContentOrLoad(
      instance        : Instance,
      instanceCaching : InstanceCaching,
      readonly        : Boolean,
      loadInstance    : InstanceLoader)(implicit
      indentedLogger  : IndentedLogger
  ): DocumentNodeInfoType = {
    // XXX TODO: cache
    loadInstance(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude)
  }

  def remove(
    instanceSourceURI : String,
    requestBodyHash   : String,
    handleXInclude    : Boolean)(implicit
    indentedLogger    : IndentedLogger
  ): Unit = {
    // XXX TODO
    ()
  }

  def removeAll(implicit indentedLogger: IndentedLogger): Unit = {
    // XXX TODO
    ()
  }
}
