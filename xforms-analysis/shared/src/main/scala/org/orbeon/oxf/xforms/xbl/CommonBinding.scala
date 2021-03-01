package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.QName
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.xml.NamespaceMapping


case class CommonBinding(
  bindingElemId               : Option[String],
  bindingElemNamespaceMapping : NamespaceMapping,
  directName                  : Option[QName],
  cssName                     : Option[String],
  containerElementName        : String,
  modeBinding                 : Boolean,
  modeValue                   : Boolean,
  modeExternalValue           : Boolean,
  modeJavaScriptLifecycle     : Boolean,
  modeLHHA                    : Boolean,
  modeFocus                   : Boolean,
  modeItemset                 : Boolean,
  modeSelection               : Boolean,
  modeHandlers                : Boolean,
  standardLhhaAsSeq           : Seq[LHHA],
  labelFor                    : Option[String],
  formatOpt                   : Option[String],
  serializeExternalValueOpt   : Option[String],
  deserializeExternalValueOpt : Option[String],
  cssClasses                  : String,
  allowedExternalEvents       : Set[String],
  constantInstances           : Map[(Int, Int), StaticXPath.DocumentNodeInfoType]
) {
  val standardLhhaAsSet: Set[LHHA] = standardLhhaAsSeq.toSet
}
