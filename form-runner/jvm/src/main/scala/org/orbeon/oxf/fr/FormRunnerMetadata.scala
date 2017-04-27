/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsConstants.LHHA
import org.orbeon.oxf.xforms.XFormsInstance
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.controls.StaticLHHASupport
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl, XFormsValueControl}
import org.orbeon.oxf.xforms.submission.SubmissionUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.orbeon.xbl.ErrorSummary

import scala.xml.Elem

trait FormRunnerMetadata {

  //noinspection MapFlatten
  //@XPathFunction
  def createFormMetadataDocument(data: NodeInfo): NodeInfo = {

    val doc = XFormsAPI.inScopeContainingDocument

    val controls = doc.getControls.getCurrentControlTree.effectiveIdsToControls

    def instanceInScope(control: XFormsSingleNodeControl, staticId: String): Option[XFormsInstance] =
      control.container.resolveObjectByIdInScope(control.getEffectiveId, staticId, None) flatMap
        collectByErasedType[XFormsInstance]

    def resourcesInstance(control: XFormsSingleNodeControl): Option[XFormsInstance] =
      instanceInScope(control, FormResources)

    def isBoundToFormDataInScope(control: XFormsSingleNodeControl): Boolean = {

      val boundNode = control.boundNode
      val data      = instanceInScope(control, FormInstance)

      (boundNode map (_.getDocumentRoot)) == (data map (_.root))
    }

    def iterateResources(resourcesInstance: XFormsInstance): Iterator[(String, NodeInfo)] =
      for (resource ← resourcesInstance.rootElement / Resource iterator)
      yield
        resource.attValue("*:lang") → resource

    def resourcesForControl(staticControl: StaticLHHASupport, lang: String, resourcesRoot: NodeInfo, controlName: String) = {

      val enclosingHolder = resourcesRoot descendant controlName take 1

      val lhhas =
        for {
          lhha     ← LHHA.values.to[List]
          lhhaName = lhha.name
          if staticControl.hasLHHA(lhhaName)
          holder   ← enclosingHolder child lhhaName
        } yield
          <dummy>{holder.stringValue}</dummy>.copy(label = lhhaName)

      val items =
        (enclosingHolder child Item nonEmpty) list
          <items>{
            for (item ← enclosingHolder child Item)
            yield
              <item>{
                for (el ← item child *)
                yield
                  <dummy>{el.stringValue}</dummy>.copy(label = el.localname)
              }</item>

          }</items>


      // TODO: multiple alerts: level of alert

      lhhas ++ items
    }

    val selectedControls =
      (controls.values map collectByErasedType[XFormsSingleNodeControl] flatten) filter isBoundToFormDataInScope

    val repeatDepth = doc.getStaticState.topLevelPart.repeatDepthAcrossParts

    def sortString(control: XFormsControl) =
      ErrorSummary.controlSortString(control.absoluteId, repeatDepth)

    val sortedControls =
      selectedControls.to[List].sortWith(sortString(_) < sortString(_))

    val controlMetadata =
      for {
        control           ← sortedControls
        staticControl     ← collectByErasedType[StaticLHHASupport](control.staticControl)
        resourcesInstance ← resourcesInstance(control)
        if ! staticControl.staticId.startsWith("fb-lhh-editor-for-") // HACK for this in grid.xbl
        controlName       ← FormRunner.controlNameFromIdOpt(control.getId)
        boundNode         ← control.boundNode
        dataHash          = SubmissionUtils.dataNodeHash(boundNode)
      } yield
        dataHash →
          <control
            name={controlName}
            type={staticControl.localName}
            datatype={control.getTypeLocalNameOpt.orNull}>{
            for ((lang, resourcesRoot) ← iterateResources(resourcesInstance))
            yield
              <resources lang={lang}>{resourcesForControl(staticControl, lang, resourcesRoot, controlName)}</resources>
          }{
            for (valueControl ← collectByErasedType[XFormsValueControl](control).toList)
            yield
              <value>{valueControl.getValue}</value>
          }</control>

    import scala.{xml ⇒ sxml}

    def addAttribute(elem: Elem, name: String, value: String) =
      elem % sxml.Attribute(None, name, sxml.Text(value), sxml.Null)

    val groupedMetadata =
      controlMetadata groupByKeepOrder (x ⇒ x._2) map
      { case (elem, hashes) ⇒ addAttribute(elem, "for", hashes map (_._1) mkString " ")}

    <metadata>{groupedMetadata}</metadata>
  }
}

object FormRunnerMetadata extends FormRunnerMetadata