/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.workflow

import io.circe.{Decoder, Encoder}
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpec

import scala.util.{Failure, Success}


class WorkflowDataModel20201Test extends AnyFunSpec with XMLSupport {

  import definitions20201._

  val TestState = WorkflowConfig(
    Vector(
      Stage(Workflow20201.StartedStageName,   ""),
      Stage(Workflow20201.SubmittedStageName, "")
    ),
    Vector(
      PermissionsToWorkflowConfig.predefinedButton(ButtonName("save")),
      PermissionsToWorkflowConfig.predefinedButton(ButtonName("send"))
    ),
    Vector(
      PermissionsToWorkflowConfig.CreatorPermissionPerspective.builder(ToAnyoneAvailability)
    )
  )

  val XmlResult: NodeInfo =
    <json type="object">
        <stages type="array">
            <_ type="object">
                <name>started</name>
                <documentation/>
            </_>
            <_ type="object">
                <name>submitted</name>
                <documentation/>
            </_>
        </stages>
        <buttons type="array">
            <_ type="object">
                <name type="object">
                    <name>save</name>
                </name>
                <documentation/>
                <label type="PredefinedButtonLabel"/>
                <actions type="array">
                   <_ type="RunProcessWorkflowAction">
                       <process>save</process>
                   </_>
                </actions>
            </_>
            <_ type="object">
                <name type="object">
                    <name>send</name>
                </name>
                <documentation/>
                <label type="PredefinedButtonLabel"/>
                <actions type="array">
                   <_ type="RunProcessWorkflowAction">
                       <process>send</process>
                   </_>
                </actions>
            </_>
        </buttons>
        <perspectives type="array">
            <_ type="object">
                <name>creator</name>
                <documentation/>
                <accessTo type="array">
                   <_ type="NewPage">
                       <initialStage type="object">
                           <name>started</name>
                           <documentation/>
                       </initialStage>
                    </_>
                </accessTo>
                <availability type="ToAnyoneAvailability"/>
                <summaryButtons type="array"/>
                <editButtons type="array">
                    <_ type="object">
                        <name>summary</name>
                    </_>
                    <_ type="object">
                        <name>clear</name>
                    </_>
                    <_ type="object">
                        <name>pdf</name>
                    </_>
                    <_ type="object">
                        <name>save-final</name>
                    </_>
                    <_ type="object">
                        <name>review</name>
                    </_>
                </editButtons>
                <viewButtons type="array"/>
            </_>
        </perspectives>
    </json>

  import io.circe.generic.auto._

  // The `auto` thing is often  tricky. Try to instantiate the encoders/decoders once only here.
  implicit val decoder = implicitly[Decoder[WorkflowConfig]]
  implicit val encoder = implicitly[Encoder[WorkflowConfig]]

  describe("2020.1 workflow data model") {

    import io.circe.generic.auto._
    import io.circe.parser
    import io.circe.syntax._

    it("must encode/decode back to original via JSON") {
      val jsonString = TestState.asJson.noSpaces
      assert(TestState == parser.decode[WorkflowConfig](jsonString).fold(Failure.apply, Success.apply).get)
    }

    it("must convert to expected XML") {
      assertXMLDocumentsIgnoreNamespacesInScope(
        XmlResult.root,
        Workflow20201.fullXmlToSimplifiedXml(Workflow20201.stateToFullXml(TestState))
      )
    }

    it("must encode/decode back to original via full XML") {
      assertXMLDocumentsIgnoreNamespacesInScope(
        Workflow20201.simplifiedXmlToFullXml(XmlResult.root),
        Workflow20201.stateToFullXml(TestState)
      )
    }

    it("must encode/decode back to original via simplified XML") {
      assert(
        Success(TestState) == Workflow20201.simplifiedXmlToState(Workflow20201.fullXmlToSimplifiedXml(Workflow20201.stateToFullXml(TestState)).rootElement)
      )
    }
  }
}
