package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.analysis.controls.ExternalEvents._
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, WithChildrenTrait}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.itemset.Itemset
import org.orbeon.xforms.EventNames
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


private object ExternalEvents {

  val TriggerExternalEvents = Set(XFORMS_FOCUS, XXFORMS_BLUR, XFORMS_HELP, DOM_ACTIVATE)
  val ValueExternalEvents   = TriggerExternalEvents + EventNames.XXFormsValue

  // NOTE: `xxforms-upload-done` is a trusted server event so doesn't need to be listed here
  val UploadExternalEvents  = Set(
    XFORMS_SELECT,
    EventNames.XXFormsUploadStart,
    EventNames.XXFormsUploadProgress,
    EventNames.XXFormsUploadCancel,
    EventNames.XXFormsUploadError
  )
}

abstract class CoreControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with ViewTrait
     with StaticLHHASupport

abstract class ValueControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends CoreControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with ValueTrait
     with WithChildrenTrait
     with FormatTrait

class InputValueControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ValueControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with RequiredSingleNode {
  override protected def externalEventsDef = super.externalEventsDef ++ ValueExternalEvents
  override val externalEvents = externalEventsDef
}

class SelectionControl(
  index                : Int,
  element              : Element,
  parent               : Option[ElementAnalysis],
  preceding            : Option[ElementAnalysis],
  staticId             : String,
  prefixedId           : String,
  namespaceMapping     : NamespaceMapping,
  scope                : Scope,
  containerScope       : Scope,
  val staticItemset    : Option[Itemset],
  val useCopy          : Boolean,
  val mustEncodeValues : Option[Boolean]
) extends InputValueControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with SelectionControlTrait {

  override protected val allowedExtensionAttributes = (! isMultiple && isFull set XXFORMS_GROUP_QNAME) + XXFORMS_TITLE_QNAME
}

class TriggerControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends CoreControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with OptionalSingleNode
     with TriggerAppearanceTrait
     with WithChildrenTrait {
  override protected def externalEventsDef = super.externalEventsDef ++ TriggerExternalEvents
  override val externalEvents              = externalEventsDef

  override protected val allowedExtensionAttributes = appearances(XFORMS_MINIMAL_APPEARANCE_QNAME) set XXFORMS_TITLE_QNAME
}

class UploadControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends InputValueControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope) {

  val multiple: Boolean = element.attributeValueOpt(XXFORMS_MULTIPLE_QNAME) contains "true"

  override protected def externalEventsDef = super.externalEventsDef ++ UploadExternalEvents
  override val externalEvents = externalEventsDef

  override protected val allowedExtensionAttributes = Set(ACCEPT_QNAME, MEDIATYPE_QNAME, XXFORMS_TITLE_QNAME)
}

class InputControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends InputValueControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope) {
  override protected val allowedExtensionAttributes = Set(
    XXFORMS_SIZE_QNAME,
    XXFORMS_MAXLENGTH_QNAME,
    XXFORMS_AUTOCOMPLETE_QNAME,
    XXFORMS_TITLE_QNAME,
    XXFORMS_PATTERN_QNAME,
  )
}

class SecretControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends InputValueControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope) {
  override protected val allowedExtensionAttributes = Set(
    XXFORMS_SIZE_QNAME,
    XXFORMS_MAXLENGTH_QNAME,
    XXFORMS_AUTOCOMPLETE_QNAME
  )
}

class TextareaControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends InputValueControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope) {
  override protected val allowedExtensionAttributes = Set(
    XXFORMS_MAXLENGTH_QNAME,
    XXFORMS_COLS_QNAME,
    XXFORMS_ROWS_QNAME
  )
}

class SwitchControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ContainerControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with OptionalSingleNode
     with StaticLHHASupport
     with AppearanceTrait {

  val caseref           = element.attributeValueOpt(CASEREF_QNAME)
  val hasFullUpdate     = element.attributeValueOpt(XXFORMS_UPDATE_QNAME).contains(XFORMS_FULL_UPDATE)

  lazy val caseControls = children collect { case c: CaseControl => c }
  lazy val caseIds      = caseControls map (_.staticId) toSet
}

class CaseControl(
  index               : Int,
  element             : Element,
  parent              : Option[ElementAnalysis],
  preceding           : Option[ElementAnalysis],
  staticId            : String,
  prefixedId          : String,
  namespaceMapping    : NamespaceMapping,
  scope               : Scope,
  containerScope      : Scope,
  val valueExpression : Option[String],
  val valueLiteral    : Option[String]
) extends ContainerControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with OptionalSingleNode
     with StaticLHHASupport {

  val selected: Option[String] = element.attributeValueOpt(SELECTED_QNAME)
}

class GroupControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ContainerControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with OptionalSingleNode
     with StaticLHHASupport {

  // Extension attributes depend on the name of the element
  override protected val allowedExtensionAttributes =
    elementQName match {
      case Some(elementQName) if Set("td", "th")(elementQName.localName) =>
        Set(QName("rowspan"), QName("colspan"))
      case Some(elementQName) if elementQName.localName == "input" =>
        Set(QName("placeholder"), QName("pattern"), QName("min"), QName("max"), QName("step"), QName("minlength"), QName("maxlength"))
      case _ =>
        Set.empty
    }

  override val externalEvents = super.externalEvents + DOM_ACTIVATE // allow DOMActivate
}

class DialogControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ContainerControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
   with OptionalSingleNode
   with StaticLHHASupport {

  override val externalEvents =
    super.externalEvents + XXFORMS_DIALOG_CLOSE // allow xxforms-dialog-close
}

class PropertyControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)

class HeaderControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with WithChildrenTrait

class NestedNameOrValueControl(
  index                    : Int,
  element                  : Element,
  parent                   : Option[ElementAnalysis],
  preceding                : Option[ElementAnalysis],
  staticId                 : String,
  prefixedId               : String,
  namespaceMapping         : NamespaceMapping,
  scope                    : Scope,
  containerScope           : Scope,
  val expressionOrConstant : Either[String, String],
) extends ElementAnalysis(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with OptionalSingleNode
     with WithExpressionOrConstantTrait
