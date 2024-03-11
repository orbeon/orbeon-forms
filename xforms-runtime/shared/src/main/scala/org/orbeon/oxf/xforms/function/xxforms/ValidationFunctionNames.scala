package org.orbeon.oxf.xforms.function.xxforms

object ValidationFunctionNames {
  val UploadMaxSizePerFile             = "upload-max-size-per-file"
  val UploadMaxSizeAggregatePerControl = "upload-max-size-aggregate-per-control"
  val UploadMediatypes                 = "upload-mediatypes"

  // Backward compatibility
  val UploadMaxSize                    = "upload-max-size"

  private val oldToCurrent: Map[String, String] = Map(UploadMaxSize -> UploadMaxSizePerFile)
  private val currentToOld: Map[String, String] = oldToCurrent.map(_.swap)

  def currentName(name: String): String     = oldToCurrent.getOrElse(name, name)
  def oldName(name: String): Option[String] = currentToOld.get(name)
}
