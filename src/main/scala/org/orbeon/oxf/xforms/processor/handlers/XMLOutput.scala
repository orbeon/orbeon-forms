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
package org.orbeon.oxf.xforms.processor.handlers

import java.{lang ⇒ jl}

import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.controls.AppearanceTrait
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xml.{Dom4j, XMLReceiver, XMLReceiverSupport}
import Dom4j._

//
// TODO:
//
// - check custom MIPs naming
// - multiple alerts
// - incremental
// - select1 getGroupName
//
// Could have configuration tokens, e.g.:
//
// - prune non-relevant controls
// - prune internals of XBL value controls
// - show XFormsVariableControl | XXFormsAttributeControl | XFormsActionControl
//
object XMLOutput extends XMLReceiverSupport {

    def send(
        xfcd            : XFormsContainingDocument,
        template        : AnnotatedTemplate,
        externalContext : ExternalContext)(implicit
        xmlReceiver     : XMLReceiver
    ): Unit =
        withDocument {
            applyMatchers(xfcd.getControls.getCurrentControlTree.getRoot, xmlReceiver)
        }

    def matchLHHA(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XFormsControl ⇒
            implicit val _xmlReceiver = xmlReceiver
            Option(c.getLabel) foreach (v ⇒ element("label", text = v))
            Option(c.getHelp)  foreach (v ⇒ element("help",  text = v))
            Option(c.getHint)  foreach (v ⇒ element("hint",  text = v))
            Option(c.getAlert) foreach (v ⇒ element("alert", text = v))
    }

    def matchAppearances(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c.staticControl collect {
        case c: AppearanceTrait ⇒
            implicit val _xmlReceiver = xmlReceiver

            c.appearances.iterator map
            (AppearanceTrait.encodeAppearanceValue(new jl.StringBuilder, _).toString) foreach
            (appearance ⇒ element("appearance", text = appearance))
    }

    def matchSingleNode(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XFormsSingleNodeControl ⇒
            implicit val _xmlReceiver = xmlReceiver
            element(
                "mips",
                List(
                    "readonly" → c.isReadonly.toString,
                    "required" → c.isRequired.toString,
                    "valid"    → c.isValid.toString
                ) ++
                    (c.valueTypeOpt.toList map (t ⇒ "datatype" → t.uriQualifiedName)) ++
                    c.customMIPs // CHECK
            )
    }

    def matchValue(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XFormsValueControl if c.isRelevant ⇒
            implicit val _xmlReceiver = xmlReceiver
            element("value", text = c.getValue)
            c.externalValueOpt foreach (v ⇒ element("external-value", text = v))
            // getRelevantEscapedExternalValue?
    }

    def matchVisitable(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: VisitableTrait if c.isRelevant ⇒
            implicit val _xmlReceiver = xmlReceiver
            if (c.visited)
                element("visited", text = c.visited.toString)
    }

    def matchItemset(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XFormsSelect1Control if c.isRelevant ⇒
            implicit val _xmlReceiver = xmlReceiver
            withElement("items") {
                c.getItemset.allItemsIterator foreach {
                    case item @ Item(_, _, _, value, atts) ⇒
                        val attsList = atts map { case (k, v) ⇒ k.uriQualifiedName → v }
                        withElement("item", List("value" → value) ++ attsList) {
                            item.iterateLHHA foreach { case (name, lhha) ⇒
                                element(name, lhha.isHTML list ("html" → lhha.isHTML.toString), text = lhha.label)
                            }
                        }
                }
            }
    }

    def matchContainer(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XFormsContainerControl ⇒
            c.children foreach (applyMatchers(_, xmlReceiver))
    }

    def matchRepeat(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XFormsRepeatControl if c.isRelevant ⇒
            implicit val _xmlReceiver = xmlReceiver
            element("repeat", List("index" → c.getIndex.toString))
    }

    def matchSwitchCase(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XFormsSwitchControl if c.isRelevant ⇒
            implicit val _xmlReceiver = xmlReceiver
            element("switch", List("selected" → (c.selectedCase map (_.getId) orNull)))
    }

    def matchFileMetadata(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: FileMetadata ⇒
            implicit val _xmlReceiver = xmlReceiver

            val properties = c.iterateProperties collect { case (k, Some(v)) ⇒ k → v } toList

            if (properties.nonEmpty)
                element("file-metadata", properties)
    }

    def matchDialog(c: XFormsControl, xmlReceiver: XMLReceiver): Unit = c collect {
        case c: XXFormsDialogControl ⇒
            implicit val _xmlReceiver = xmlReceiver
            if(c.isVisible)
                element("visible", text = "true")
    }

    val Matchers =
        List[(XFormsControl, XMLReceiver) ⇒ Unit](
            matchAppearances,
            matchLHHA,
            matchSingleNode,
            matchValue,
            matchVisitable,
            matchItemset,
            matchRepeat,
            matchSwitchCase,
            matchDialog,
            matchContainer,
            matchFileMetadata
        )

    def applyMatchers(c: XFormsControl, xmlReceiver: XMLReceiver) = c match {
        case _: XFormsVariableControl | _: XXFormsAttributeControl | _: XFormsActionControl ⇒
        case _ ⇒
            implicit val _xmlReceiver = xmlReceiver

            val extensionAttributes =
                c.evaluatedExtensionAttributes.iterator collect {
                    case (name, value) if name.getNamespaceURI == "" ⇒ name.getName → value
                }

            withElement(
                "control",
                List(
                    "id"       → c.getEffectiveId,
                    "type"     → c.staticControl.localName,
                    "relevant" → c.isRelevant.toString
                ) ++ extensionAttributes) {
                Matchers foreach (_.apply(c, xmlReceiver))
            }
    }
}
