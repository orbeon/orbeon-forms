/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.test

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{Matchers, Mockito}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.InitUtils
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl, XFormsSingleNodeControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.XFormsEvent.PropertyGetter
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.itemset.{Itemset, ItemsetSupport}
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{Loggers, XFormsContainingDocument}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.xforms.runtime.XFormsObject
import org.orbeon.xforms.{Constants, EventNames}
import org.scalatestplus.mockito.MockitoSugar

import scala.reflect.ClassTag


trait XFormsSupport extends MockitoSugar {

  self: DocumentTestBase =>

  def withTestExternalContext[T](body: ExternalContext => T): T =
    InitUtils.withPipelineContext { pipelineContext =>
      body(
        PipelineSupport.setExternalContext(
          pipelineContext,
          PipelineSupport.DefaultRequestUrl,
          XFormsStateManager.sessionCreated,
          XFormsStateManager.sessionDestroyed
        )
      )
    }

  def withActionAndDoc[T](url: String)(body: => T): T =
    withActionAndDoc(setupDocument(url))(body)

  def withActionAndDoc[T](doc: XFormsContainingDocument)(body: => T): T =
    withScalaAction(mockActionInterpreter(doc)) { _ =>
      withContainingDocument(doc) {
        body
      }
    }

  def withAction[T](body: => T): T =
    document.withOutermostActionHandler {
      withScalaAction(mockActionInterpreter(inScopeContainingDocument))(_ => body)
    }

  private def mockActionInterpreter(doc: XFormsContainingDocument) = {
    val actionInterpreter = mock[XFormsActionInterpreter]
    Mockito when actionInterpreter.containingDocument thenReturn doc
    Mockito when actionInterpreter.container thenReturn doc
    Mockito when actionInterpreter.indentedLogger thenReturn new IndentedLogger(Loggers.logger)

    // Resolve assuming target relative to the document
    Mockito when actionInterpreter.resolveObject(Matchers.anyObject(), Matchers.anyString) thenAnswer new Answer[XFormsObject] {
      def answer(invocation: InvocationOnMock) = {
        val targetStaticOrAbsoluteId = invocation.getArguments()(1).asInstanceOf[String]
        doc.resolveObjectById(Constants.DocumentId, targetStaticOrAbsoluteId, contextItemOpt = None)
      }
    }

    actionInterpreter
  }

  // Dispatch a custom event to the object with the given prefixed id
  def dispatch(name: String, effectiveId: String, properties: PropertyGetter = XFormsEvent.EmptyGetter): Unit =
    Dispatch.dispatchEvent(
      new XFormsCustomEvent(
        name,
        document.getObjectByEffectiveId(effectiveId).asInstanceOf[XFormsEventTarget],
        properties,
        bubbles    = true,
        cancelable = true
      )
    )

  // Get a top-level instance
  def instance(instanceStaticId: String) =
    document.findInstance(instanceStaticId)

  // Convert an instance to a string
  def instanceToString(instance: XFormsInstance) =
    TransformerUtils.tinyTreeToString(instance.documentInfo)

  def getControlValue(controlEffectiveId: String) = getValueControl(controlEffectiveId).getValue
  def getControlExternalValue(controlEffectiveId: String) = getValueControl(controlEffectiveId).getExternalValue

  // This stores the value without testing for readonly
  def setControlValue(controlEffectiveId: String, value: String): Unit =
    document.withOutermostActionHandler {
      getValueControl(controlEffectiveId).storeExternalValue(value)
    }

  def setControlValueWithEventSearchNested(controlEffectiveId: String, value: String): Unit =
    getObject(controlEffectiveId) match {
      case c: XFormsControl =>
        ControlsIterator(c, includeSelf = true) collectFirst {
          case vc: XFormsValueControl if vc.allowExternalEvent(EventNames.XXFormsValue) =>
            vc
        } foreach { target =>
          ClientEvents.processEvent(document, new XXFormsValueEvent(target, value))
            document.afterExternalEvents(true)
            document.afterUpdateResponse()
            document.beforeExternalEvents(null, isAjaxRequest = true)
        }
      case _ =>
    }

  def isRelevant(controlEffectiveId: String) = getObject(controlEffectiveId).asInstanceOf[XFormsControl].isRelevant
  def isRequired(controlEffectiveId: String) = getSingleNodeControl(controlEffectiveId).isRequired
  def isReadonly(controlEffectiveId: String) = getSingleNodeControl(controlEffectiveId).isReadonly
  def isValid(controlEffectiveId: String)    = getSingleNodeControl(controlEffectiveId).isValid
  def getType(controlEffectiveId: String)    = getSingleNodeControl(controlEffectiveId).valueType

  def hasFocus(controlEffectiveId: String)   = document.controls.getFocusedControl exists (_ eq getSingleNodeControl(controlEffectiveId))

  def getItemset(controlEffectiveId: String): String = {
    val select1 = getObject(controlEffectiveId).asInstanceOf[XFormsSelect1Control]
    ItemsetSupport.asJSON(select1.getItemset, None, select1.mustEncodeValues, select1.staticControl.excludeWhitespaceTextNodesForCopy, null)
  }

  def getItemsetSearchNested(control: XFormsControl): Option[Itemset] = control match {
    case c: XFormsSelect1Control   => Some(c.getItemset)
    case c: XFormsComponentControl => ControlsIterator(c, includeSelf = false) collectFirst { case c: XFormsSelect1Control =>  c.getItemset }
    case _                         => None
  }

  def resolveObject[T: ClassTag](
    staticOrAbsoluteId : String,
    sourceEffectiveId  : String       = Constants.DocumentId,
    indexes            : List[Int]    = Nil,
    container          : XBLContainer = document
  ): Option[T] = {

    val resolvedOpt =
      container.resolveObjectByIdInScope(sourceEffectiveId, staticOrAbsoluteId) collect {
        case result if indexes.nonEmpty =>
          document.getObjectByEffectiveId(Dispatch.resolveRepeatIndexes(container, result, container.prefixedId, indexes mkString " "))
        case result =>
          result
      }

    resolvedOpt collect { case c: T => c }
  }

  def getControl(controlEffectiveId: String)           = getObject(controlEffectiveId).asInstanceOf[XFormsControl]
  def getSingleNodeControl(controlEffectiveId: String) = getObject(controlEffectiveId).asInstanceOf[XFormsSingleNodeControl]
  def getValueControl(controlEffectiveId: String)      = getObject(controlEffectiveId).asInstanceOf[XFormsValueControl]
  def getObject(effectiveId: String)                   = document.getObjectByEffectiveId(effectiveId)
}
