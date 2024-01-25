/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control

import cats.syntax.option._
import org.orbeon.datatypes.LocationData
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.rewrite.Rewrite
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.{AppearanceTrait, RepeatControl, SingleNodeTrait}
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, PartAnalysis, WithChildrenTrait}
import org.orbeon.oxf.xforms.control.controls.XFormsActionControl
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.model.StaticDataModel
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, XMLConstants}
import org.orbeon.saxon.om
import org.orbeon.xforms.Constants.RepeatSeparatorString
import org.orbeon.xforms.runtime.XFormsObject
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsId}
import org.xml.sax.Attributes

/**
 * Represents an XForms control.
 *
 * The implementation is split into a series of traits to make each chunk more palatable.
 */
class XFormsControl(
    val container   : XBLContainer,
    var parent      : XFormsControl, // var just so we can null it upon clone
    val element     : Element,
    var effectiveId : String         // var because can be updated upon iteration change
) extends ControlXPathSupport
     with Cloneable
     with ControlAjaxSupport
     with ControlLHHASupport
     with ControlLocalSupport
     with ControlExtensionAttributesSupport
     with ControlEventSupport
     with ControlBindingSupport
     with XFormsEventTarget
     with MaybeFocusableTrait {

  self =>

  // Type of the associated static control
  type Control <: ElementAnalysis // TODO: Use more specific to represent controls. Note that those can also be actions.

  require(container ne null)

  implicit final val containingDocument: XFormsContainingDocument = container.getContainingDocument
  implicit final def logger            : IndentedLogger = containingDocument.controls.indentedLogger

  final def part: PartAnalysis = container.getPartAnalysis

  // Static information (never changes for the lifetime of the containing document)
  // TODO: Pass staticControl during construction (find which callers don't pass the necessary information)
  final val staticControl   : Control         = part.getControlAnalysis(XFormsId.getPrefixedId(effectiveId)).asInstanceOf[Control]
  final def staticControlOpt: Option[Control] = Option(staticControl)

  final val prefixedId = staticControlOpt map (_.prefixedId) getOrElse XFormsId.getPrefixedId(effectiveId)
  final def absoluteId: String = XFormsId.effectiveIdToAbsoluteId(effectiveId)

  // Whether the control has been visited
  def visited = false

  parent match {
    case container: XFormsContainerControl => container.addChild(self)
    case _ =>
  }

  final def getId: String = staticControl.staticId
  final def getPrefixedId: String = prefixedId

  final def scope: Scope = staticControl.scope
  final def localName: String = staticControl.localName

  def getContextStack: XFormsContextStack = container.getContextStack

  final def getResolutionScope: Scope =
    part.scopeForPrefixedId(prefixedId)

  // Resolve an object relative to this control
  final def resolve(staticId: String, contextItem: Option[om.Item] = None): Option[XFormsObject] =
    container.resolveObjectByIdInScope(getEffectiveId, staticId, contextItem)

  // Update this control's effective id based on the parent's effective id
  def updateEffectiveId(): Unit = {
    if (staticControl.isWithinRepeat) {
      val parentEffectiveId = parent.getEffectiveId
      val parentSuffix = XFormsId.getEffectiveIdSuffix(parentEffectiveId)
      effectiveId = XFormsId.getPrefixedId(effectiveId) + RepeatSeparatorString + parentSuffix
      if (_childrenActions.nonEmpty)
        for (actionControl <- _childrenActions)
          actionControl.updateEffectiveId()
    }
  }

  def getEffectiveId: String = effectiveId

  // Used by repeat iterations
  def setEffectiveId(effectiveId: String): Unit =
    self.effectiveId = effectiveId

  final def getLocationData: LocationData =
    if (staticControl ne null) staticControl.locationData else if (element ne null) element.getData.asInstanceOf[LocationData] else null

  // Semi-dynamic information (depends on the tree of controls, but does not change over time)
  private var _childrenActions: List[XFormsActionControl] = Nil

  // Dynamic information (changes depending on the content of XForms instances)
  private var previousEffectiveId: String = null

  // NOP, can be overridden
  def iterationRemoved(): Unit = ()

  // Preceding control if any
  def preceding: Option[XFormsControl] = {
    // Unlike ElementAnalysis we don't store a `preceding` pointer so we have to search for it
    val siblingsOrSelf = parent.asInstanceOf[XFormsContainerControl].children
    val index          = siblingsOrSelf indexWhere (self eq _)

    index > 0 option siblingsOrSelf(index - 1)
  }

  // NOTE: As of 2011-11-22, this is here so that effective ids of nested actions can be updated when iterations
  // are moved around. Ideally anyway, iterations should not require effective id changes. An iteration tree should
  // probably have a uuid, and updating it should be done in constant time.
  // As of 2012-02-23, this is also here so that binding updates can take place on nested actions. However this too is
  // not really necessary, as an action's binding is just a copy of its parent binding as we don't want to needlessly
  // evaluate the actual binding before the action runs. But we have this so that the setBindingContext,
  // bindingContextForChild, bindingContextForFollowing operations work on nested actions too.
  // Possibly we could make *any* control able to have nested content. This would make sense from a class hierarchy
  // perspective.
  final def addChildAction(actionControl: XFormsActionControl): Unit =
    _childrenActions ::= actionControl

  final def childrenActions: List[XFormsActionControl] = _childrenActions

  final def previousEffectiveIdCommit(): String = {
    val result = previousEffectiveId
    previousEffectiveId = effectiveId
    result
  }

  def commitCurrentUIState(): Unit = {
    wasRelevantCommit()
    previousEffectiveIdCommit()
  }

  final def appearances: Set[QName] = XFormsControl.appearances(staticControl)
  def isStaticReadonly  = false

  // Optional mediatype
  final def mediatype: Option[String] = staticControl match {
    case appearanceTrait: AppearanceTrait => appearanceTrait.mediatype
    case _ => None
  }

  def hasJavaScriptInitialization = false

  def compareExternalMaybeClientValue(
    previousValueOpt   : Option[String],
    previousControlOpt : Option[XFormsControl],
    collector          : ErrorEventCollector
  ): Boolean =
    // NOTE: See https://github.com/orbeon/orbeon-forms/issues/2857. We might consider removing this
    // optimization as it is dangerous. `XFormsValueControl` works around it by calling
    // `compareExternalUseExternalValue` directly.
    (previousControlOpt exists (_ eq self)) && (getInitialLocal eq getCurrentLocal) ||
    compareExternalUseExternalValue(previousValueOpt, previousControlOpt, collector)

  // Compare this control with another control, as far as the comparison is relevant for the external world.
  def compareExternalUseExternalValue(
    previousExternalValueOpt: Option[String],
    previousControlOpt      : Option[XFormsControl],
    collector               : ErrorEventCollector
  ): Boolean =
    previousControlOpt match {
      case Some(other) =>
        isRelevant == other.isRelevant &&
        compareLHHA(other, collector)  &&
        compareExtensionAttributes(other)
      case _ => false
    }

  // Evaluate the control's value and metadata
  final def evaluate(collector: ErrorEventCollector): Unit =
    try preEvaluateImpl(relevant = true, parentRelevant = true, collector)
    catch {
      case e: ValidationException =>
        throw OrbeonLocationException.wrapException(
          e,
          XmlExtendedLocationData(
            getLocationData,
            "evaluating control".some,
            element = Option(element)
          )
        )
    }

  // Called to clear the control's values when the control becomes non-relevant
  final def evaluateNonRelevant(parentRelevant: Boolean, collector: ErrorEventCollector): Unit = {
    evaluateNonRelevantLHHA()
    evaluateNonRelevantExtensionAttribute()
    preEvaluateImpl(relevant = false, parentRelevant = parentRelevant, collector)
  }

  // Notify the control that some of its aspects (value, label, etc.) might have changed and require re-evaluation. It
  // is left to the control to figure out if this can be optimized.
  def markDirtyImpl(): Unit = {
    markLHHADirty()
    markExtensionAttributesDirty()
  }

  // Evaluate this control
  // NOTE: LHHA and extension attributes are computed lazily
  def preEvaluateImpl(relevant: Boolean, parentRelevant: Boolean, collector: ErrorEventCollector): Unit = ()

  /**
   * Clone a control. It is important to understand why this is implemented: to create a copy of a tree of controls
   * before updates that may change control bindings. Also, it is important to understand that we clone "back", that
   * is the new clone will be used as the reference copy for the difference engine.
   */
  def getBackCopy(collector: ErrorEventCollector): AnyRef = {
    // NOTE: this.parent is handled by subclasses
    val cloned = super.clone.asInstanceOf[XFormsControl]

    updateLHHACopy(cloned, collector)
    updateLocalCopy(cloned)

    cloned
  }

  // Build children controls if any, delegating the actual construction to the given `buildTree` function
  def buildChildren(
    buildTree: (XBLContainer, BindingContext, ElementAnalysis, Seq[Int], ErrorEventCollector) => Option[XFormsControl],
    idSuffix : Seq[Int],
    collector: ErrorEventCollector
  ): Unit =
    staticControl match {
      case withChildren: WithChildrenTrait => Controls.buildChildren(self, withChildren.children, buildTree, idSuffix, collector)
      case _ =>
    }
}

object XFormsControl {

  def controlSupportsRefreshEvents(control: XFormsControl): Boolean =
    (control ne null) && control.supportsRefreshEvents

  // Whether the given item is allowed as a binding item for the given control
  // TODO: don't like pattern matching here and revisit hierarchy
  def isAllowedBoundItem(control: XFormsControl, item: om.Item): Boolean = control.staticControl match {
    case singleNode: SingleNodeTrait => singleNode.isAllowedBoundItem(item)
    case _: RepeatControl            => StaticDataModel.isAllowedBoundItem(item)
    case _                           => false
  }

  // Rewrite an HTML value which may contain URLs, for example in @src or @href attributes. Also deals with closing element tags.
  def getEscapedHTMLValue(locationData: LocationData, rawValue: String): String = {

    if (rawValue eq null)
      return null

    val sb = new StringBuilder(rawValue.length * 2) // just an approx of the size it may take
    // NOTE: we do our own serialization here, but it's really simple (no namespaces) and probably reasonably efficient
    val rewriter = XFormsCrossPlatformSupport.externalContext.getResponse
    XFormsCrossPlatformSupport.streamHTMLFragment(
      rawValue,
      locationData,
      "xhtml")(
      Rewrite.getRewriteXMLReceiver(
        rewriter,
        new ForwardingXMLReceiver {

          private var isStartElement = false

          override def characters(chars: Array[Char], start: Int, length: Int): Unit = {
            sb.append(new String(chars, start, length).escapeXmlMinimal) // NOTE: not efficient to create a new String here
            isStartElement = false
          }

          override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
            sb.append('<')
            sb.append(localname)
            val attributeCount = attributes.getLength

            for (i <- 0 until attributeCount) {
              val currentName = attributes.getLocalName(i)
              val currentValue = attributes.getValue(i)
              sb.append(' ')
              sb.append(currentName)
              sb.append("=\"")
              sb.append(currentValue)
              sb.append('"')
            }

            sb.append('>')
            isStartElement = true
          }

          override def endElement(uri: String, localname: String, qName: String): Unit = {
            if (! isStartElement || ! VoidElements(localname)) {
              // We serialize to HTML: don't close elements that just opened (will cover `<br>`, `<hr>`, etc.). Be sure not
              // to drop closing elements of other tags though!
              sb.append("</")
              sb.append(localname)
              sb.append('>')
            }
            isStartElement = false
          }

        },
        fragment = true,
        XMLConstants.XHTML_NAMESPACE_URI
      )
    )

    sb.toString
  }

  // Base trait for a control property (label, itemset, etc.)
  trait ControlProperty[T >: Null] {
    def value(collector: ErrorEventCollector): T
    def handleMarkDirty(force: Boolean = false): Unit
    def copy: ControlProperty[T]
  }

  // Immutable control property
  class ImmutableControlProperty[T >: Null](private val _value: T) extends ControlProperty[T] {
    def value(collector: ErrorEventCollector): T = _value
    def handleMarkDirty(force: Boolean): Unit = ()
    def copy: ImmutableControlProperty[T] = this
  }

  // Mutable control property supporting optimization
  trait MutableControlProperty[T >: Null] extends ControlProperty[T] with Cloneable {

    private var _value: T = null
    private var isEvaluated = false
    private var isOptimized = false

    protected def isRelevant: Boolean
    protected def wasRelevant: Boolean
    protected def requireUpdate: Boolean
    protected def notifyCompute(): Unit
    protected def notifyOptimized(): Unit

    protected def evaluateValue(collector: ErrorEventCollector): T
    protected def nonRelevantValue: T = null

    final def value(collector: ErrorEventCollector): T = {
      if (! isEvaluated) {
        _value =
          if (isRelevant) {
            notifyCompute()
            evaluateValue(collector)
          } else {
            // NOTE: if the control is not relevant, nobody should ask about this in the first place
            // In practice, this can be called as of 2012-06-20
            nonRelevantValue
          }
        isEvaluated = true
      } else if (isOptimized) {
        // This is only for statistics: if the value was not re-evaluated because of the dependency engine
        // giving us the green light, the first time the value is asked we notify the dependency engine of that
        // situation.
        notifyOptimized()
        isOptimized = false
      }

      _value
    }

    def handleMarkDirty(force: Boolean): Unit = {

      def isDirty = ! isEvaluated
      def markOptimized(): Unit = isOptimized = true

      if (! isDirty) {
        // don't do anything if we are already dirty
        if (force || isRelevant != wasRelevant) {
          // Control becomes relevant or non-relevant
          markDirty()
        } else if (isRelevant) {
          // Control remains relevant
          if (requireUpdate)
            markDirty()
          else
            markOptimized() // for statistics only
        }
      }
    }

    protected def markDirty(): Unit = {
      _value = null
      isEvaluated = false
      isOptimized = false
    }

    def copy: MutableControlProperty[T] = super.clone.asInstanceOf[MutableControlProperty[T]]
  }

  // Return the set of appearances for the given element, if any
  def appearances(elementAnalysis: ElementAnalysis): Set[QName] = elementAnalysis match {
    case appearanceTrait: AppearanceTrait => appearanceTrait.appearances
    case _                                => Set.empty[QName]
  }
}
