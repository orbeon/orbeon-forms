package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.QName
import org.orbeon.oxf.util.StaticXPath
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.xforms.HeadElement
import org.orbeon.xforms.xbl.Scope
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
  allowMinimalLabelHint       : Boolean,
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

// NOTE: 2018-02-23: This is only created if the `AbstractBinding` has a template. Wondering if we should support components with
// no templates (or creating an empty template in that case) so that we don't have to special-case bindings without templates.
case class ConcreteBinding(
  innerScope       : Scope,             // each binding defines a new scope
  templateTree     : SAXStore,          // template with relevant markup for output, including XHTML when needed
  boundElementAtts : Map[QName, String] // attributes on the bound element
)

case class XBLAssets(css: List[HeadElement], js: List[HeadElement])
