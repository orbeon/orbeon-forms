/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.oxf.xforms.analysis.controls._
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model, Submission}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.expr.StringLiteral
import org.orbeon.xforms.EventNames
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope

object ControlAnalysisFactory {

  // Control factories
  type ControlFactory = (PartAnalysisImpl, Int, Element,  Option[ElementAnalysis], Option[ElementAnalysis], Scope) => ElementAnalysis

  private val TriggerExternalEvents = Set(XFORMS_FOCUS, XXFORMS_BLUR, XFORMS_HELP, DOM_ACTIVATE)
  private val ValueExternalEvents   = TriggerExternalEvents + EventNames.XXFormsValue

  // NOTE: xxforms-upload-done is a trusted server event so doesn't need to be listed here
  private val UploadExternalEvents  = Set(
    XFORMS_SELECT,
    EventNames.XXFormsUploadStart,
    EventNames.XXFormsUploadProgress,
    EventNames.XXFormsUploadCancel,
    EventNames.XXFormsUploadError
  )

  abstract class CoreControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element   : Element,
    parent    : Option[ElementAnalysis],
    preceding : Option[ElementAnalysis],
    scope     : Scope
  ) extends ElementAnalysis(part, index, element, parent, preceding, scope)
       with ViewTrait
       with StaticLHHASupport


  abstract class ValueControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element   : Element,
    parent    : Option[ElementAnalysis],
    preceding : Option[ElementAnalysis],
    scope     : Scope
  ) extends CoreControl(part, index, element, parent, preceding, scope)
       with ValueTrait
       with WithChildrenTrait
       with FormatTrait

  class InputValueControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element   : Element,
    parent    : Option[ElementAnalysis],
    preceding : Option[ElementAnalysis],
    scope     : Scope
  ) extends ValueControl(part, index, element, parent, preceding, scope)
       with RequiredSingleNode {
    override protected def externalEventsDef = super.externalEventsDef ++ ValueExternalEvents
    override val externalEvents = externalEventsDef
  }

  class SelectionControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element   : Element,
    parent    : Option[ElementAnalysis],
    preceding : Option[ElementAnalysis],
    scope     : Scope
  ) extends InputValueControl(part, index, element, parent, preceding, scope)
       with SelectionControlTrait {
    override protected val allowedExtensionAttributes = (! isMultiple && isFull set XXFORMS_GROUP_QNAME) + XXFORMS_TITLE_QNAME
  }

  class TriggerControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends CoreControl(part, index, element, parent, preceding, scope)
       with OptionalSingleNode
       with TriggerAppearanceTrait
       with WithChildrenTrait {
    override protected def externalEventsDef = super.externalEventsDef ++ TriggerExternalEvents
    override val externalEvents              = externalEventsDef

    override protected val allowedExtensionAttributes = appearances(XFORMS_MINIMAL_APPEARANCE_QNAME) set XXFORMS_TITLE_QNAME
  }

  class UploadControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends InputValueControl(part, index, element, parent, preceding, scope) {

    val multiple: Boolean = element.attributeValueOpt(XXFORMS_MULTIPLE_QNAME) contains "true"

    override protected def externalEventsDef = super.externalEventsDef ++ UploadExternalEvents
    override val externalEvents = externalEventsDef

    override protected val allowedExtensionAttributes = Set(ACCEPT_QNAME, MEDIATYPE_QNAME, XXFORMS_TITLE_QNAME)
  }

  class InputControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element   : Element,
    parent    : Option[ElementAnalysis],
    preceding : Option[ElementAnalysis],
    scope     : Scope
  ) extends InputValueControl(part, index, element, parent, preceding, scope) {
    override protected val allowedExtensionAttributes = Set(
      XXFORMS_SIZE_QNAME,
      XXFORMS_TITLE_QNAME,
      XXFORMS_MAXLENGTH_QNAME,
      XXFORMS_PATTERN_QNAME,  // HTML 5 forms attribute
      XXFORMS_AUTOCOMPLETE_QNAME
    )
  }

  class SecretControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends InputValueControl(part, index, element, parent, preceding, scope) {
    override protected val allowedExtensionAttributes = Set(
      XXFORMS_SIZE_QNAME,
      XXFORMS_MAXLENGTH_QNAME,
      XXFORMS_AUTOCOMPLETE_QNAME
    )
  }

  class TextareaControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends InputValueControl(part, index, element, parent, preceding, scope) {
    override protected val allowedExtensionAttributes = Set(
      XXFORMS_MAXLENGTH_QNAME,
      XXFORMS_COLS_QNAME,
      XXFORMS_ROWS_QNAME
    )
  }

  class SwitchControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends ContainerControl(part, index, element, parent, preceding, scope)
       with OptionalSingleNode
       with StaticLHHASupport
       with AppearanceTrait {

    val caseref           = element.attributeValueOpt(CASEREF_QNAME)
    val hasFullUpdate     = element.attributeValueOpt(XXFORMS_UPDATE_QNAME).contains(XFORMS_FULL_UPDATE)

    lazy val caseControls = children collect { case c: CaseControl => c }
    lazy val caseIds      = caseControls map (_.staticId) toSet
  }

  class CaseControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends ContainerControl(part, index, element, parent, preceding, scope)
       with OptionalSingleNode
       with StaticLHHASupport {

    val selected        = element.attributeValueOpt(SELECTED_QNAME)
    val valueExpression = element.attributeValueOpt(VALUE_QNAME)
    val valueLiteral    = valueExpression flatMap { valueExpr =>

      val literal =
        XPath.evaluateAsLiteralIfPossible(
          xpathString      = XPath.makeStringExpression(valueExpr),
          namespaceMapping = namespaceMapping,
          locationData     = locationData,
          functionLibrary  = part.staticState.functionLibrary,
          avt              = false
        )

      literal collect {
        case literal: StringLiteral => literal.getStringValue
      }
    }
  }

  class GroupControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends ContainerControl(part, index, element, parent, preceding, scope)
       with OptionalSingleNode
       with StaticLHHASupport {

    // Extension attributes depend on the name of the element
    override protected val allowedExtensionAttributes =
      elementQName match {
        case Some(elementQName) if Set("td", "th")(elementQName.localName) =>
          Set(QName("rowspan"), QName("colspan"))
        case _ =>
          Set.empty
      }

    override val externalEvents = super.externalEvents + DOM_ACTIVATE // allow DOMActivate
  }

  class DialogControl(
    part      : PartAnalysisImpl,
    index     : Int,
    element            : Element,
    parent             : Option[ElementAnalysis],
    preceding          : Option[ElementAnalysis],
    scope              : Scope
  ) extends ContainerControl(part, index, element, parent, preceding, scope)
     with OptionalSingleNode
     with StaticLHHASupport {

    override val externalEvents =
      super.externalEvents + XXFORMS_DIALOG_CLOSE // allow xxforms-dialog-close
  }

  private val VariableControlFactory: ControlFactory = new VariableControl(_, _, _, _, _, _)
  private val LHHAControlFactory    : ControlFactory = new LHHAAnalysis(_, _, _, _, _, _)

  // Variable factories indexed by QName
  // NOTE: We have all these QNames for historical reasons (XForms 2 is picking <xf:var>)
  private val variableFactory =
    Seq(XXFORMS_VARIABLE_QNAME, XXFORMS_VAR_QNAME, XFORMS_VARIABLE_QNAME, XFORMS_VAR_QNAME, EXFORMS_VARIABLE_QNAME) map
      (qName => qName -> VariableControlFactory) toMap

  // Other factories indexed by QName
  private val byQNameFactory = Map[QName, ControlFactory](
    XBL_TEMPLATE_QNAME            -> (new ContainerControl(_, _, _, _, _, _)),
    // Core value controls
    XFORMS_INPUT_QNAME            -> (new InputControl(_, _, _, _, _, _)),
    XFORMS_SECRET_QNAME           -> (new SecretControl(_, _, _, _, _, _)),
    XFORMS_TEXTAREA_QNAME         -> (new TextareaControl(_, _, _, _, _, _)),
    XFORMS_UPLOAD_QNAME           -> (new UploadControl(_, _, _, _, _, _)),
    XFORMS_RANGE_QNAME            -> (new InputValueControl(_, _, _, _, _, _)),
    XXFORMS_TEXT_QNAME            -> (new OutputControl(_, _, _, _, _, _)),// TODO: don't accept any external events
    XFORMS_OUTPUT_QNAME           -> (new OutputControl(_, _, _, _, _, _)),
    // Core controls
    XFORMS_TRIGGER_QNAME          -> (new TriggerControl(_, _, _, _, _, _)),
    XFORMS_SUBMIT_QNAME           -> (new TriggerControl(_, _, _, _, _, _)),
    // Selection controls
    XFORMS_SELECT_QNAME           -> (new SelectionControl(_, _, _, _, _, _)),
    XFORMS_SELECT1_QNAME          -> (new SelectionControl(_, _, _, _, _, _)),
    // Attributes
    XXFORMS_ATTRIBUTE_QNAME       -> (new AttributeControl(_, _, _, _, _, _)),
    // Container controls
    XFORMS_GROUP_QNAME            -> (new GroupControl(_, _, _, _, _, _)),
    XFORMS_SWITCH_QNAME           -> (new SwitchControl(_, _, _, _, _, _)),
    XFORMS_CASE_QNAME             -> (new CaseControl(_, _, _, _, _, _)),
    XXFORMS_DIALOG_QNAME          -> (new DialogControl(_, _, _, _, _, _)),
    // Dynamic control
    XXFORMS_DYNAMIC_QNAME         -> (new ElementAnalysis(_, _, _, _, _, _) with RequiredSingleNode with ViewTrait), // NOTE: No longer `ContainerControl`!
    // Repeat control
    XFORMS_REPEAT_QNAME           -> (new RepeatControl(_, _, _, _, _, _)),
    XFORMS_REPEAT_ITERATION_QNAME -> (new RepeatIterationControl(_, _, _, _, _, _)),
    // LHHA
    LABEL_QNAME                   -> LHHAControlFactory,
    HELP_QNAME                    -> LHHAControlFactory,
    HINT_QNAME                    -> LHHAControlFactory,
    ALERT_QNAME                   -> LHHAControlFactory,
    // Model
    XFORMS_MODEL_QNAME            -> (new Model(_, _, _, _, _, _)),
    XFORMS_SUBMISSION_QNAME       -> (new Submission(_, _, _, _, _, _)),
    XFORMS_INSTANCE_QNAME         -> (new Instance(_, _, _, _, _, _)),
    // Itemsets
    XFORMS_CHOICES_QNAME          -> (new ElementAnalysis(_, _, _, _, _, _) with WithChildrenTrait),
    XFORMS_ITEM_QNAME             -> (new ElementAnalysis(_, _, _, _, _, _) with WithChildrenTrait),
    XFORMS_ITEMSET_QNAME          -> (new ElementAnalysis(_, _, _, _, _, _) with WithChildrenTrait),
    XFORMS_VALUE_QNAME            -> (new ElementAnalysis(_, _, _, _, _, _) with ValueTrait with OptionalSingleNode),
    XFORMS_COPY_QNAME             -> (new ElementAnalysis(_, _, _, _, _, _) with RequiredSingleNode)
  ) ++ variableFactory

  private val ControlFactory: PartialFunction[Element, ControlFactory] =
    { case e: Element if byQNameFactory.isDefinedAt(e.getQName) => byQNameFactory(e.getQName) }

  private val ControlOrActionFactory = ControlFactory orElse XFormsActions.ActionFactory lift

  private val ComponentFactories: Map[(Boolean, Boolean), ControlFactory] = Map(
    (false, false) -> (new ComponentControl(_, _, _, _, _, _)                                       ),
    (false, true)  -> (new ComponentControl(_, _, _, _, _, _) with                          StaticLHHASupport),
    (true,  false) -> (new ComponentControl(_, _, _, _, _, _) with ValueComponentTrait                       ),
    (true,  true)  -> (new ComponentControl(_, _, _, _, _, _) with ValueComponentTrait with StaticLHHASupport)
  )

  def create(
    part           : PartAnalysisImpl,
    index          : Int,
    controlElement : Element,
    parent         : Option[ElementAnalysis],
    preceding      : Option[ElementAnalysis],
    scope          : Scope
  ): Option[ElementAnalysis] = {

    require(controlElement ne null)
    require(scope ne null)

    val factory =
      part.metadata.findAbstractBindingByPrefixedId(scope.prefixedIdForStaticId(controlElement.idOrNull)) match {
        case Some(abstractBinding) => ComponentFactories.get(abstractBinding.modeValue, abstractBinding.modeLHHA)
        case None                  => ControlOrActionFactory(controlElement)
      }

    factory map (_(part, index, controlElement, parent, preceding, scope))
  }

  def isVariable(qName: QName): Boolean = variableFactory.contains(qName)
}
