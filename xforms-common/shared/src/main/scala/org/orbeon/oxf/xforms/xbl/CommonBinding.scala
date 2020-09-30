package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.xml.NamespaceMapping


case class CommonBinding(
  bindingElemNamespaceMapping: NamespaceMapping,
  directName                 : Option[QName],
  cssName                    : Option[String],
  containerElementName       : String,
  modeBinding                : Boolean,
  modeValue                  : Boolean,
  modeExternalValue          : Boolean,
  modeJavaScriptLifecycle    : Boolean,
  modeLHHA                   : Boolean,
  modeFocus                  : Boolean,
  modeItemset                : Boolean,
  modeSelection              : Boolean,
  modeHandlers               : Boolean,
  standardLhhaAsSeq          : Seq[LHHA],
  standardLhhaAsSet          : Set[LHHA],
  labelFor                   : Option[String],
  formatOpt                  : Option[String],
  serializeExternalValueOpt  : Option[String],
  deserializeExternalValueOpt: Option[String],
  debugBindingName           : String,
  cssClasses                 : String,
  allowedExternalEvents      : Set[String],
  constantInstances          : Map[(Int, Int), DocumentInfo]
)
