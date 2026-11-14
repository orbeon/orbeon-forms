/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fb.process

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class ProcessUITest extends DocumentTestBase with ResourceManagerSupport with AnyFunSpecLike {

  describe("ProcessUI XML conversion") {

    it("should round-trip simple actions") {
      val processStr = "validate then save"
      val xml = ProcessUI.processStringToXml(processStr)
      val serialized = ProcessUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip actions with combinators and parameters") {
      val processStr = """validate then save recover error-message("error")"""
      val xml = ProcessUI.processStringToXml(processStr)
      val steps = (xml child "step").toList
      assert(steps.size == 3)
      assert((steps(0) attValue "name") == "validate")
      assert((steps(1) attValue "name") == "save")
      assert((steps(1) attValue "combinator") == "then")
      assert((steps(2) attValue "name") == "error-message")
      assert((steps(2) attValue "combinator") == "recover")

      val serialized = ProcessUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip groups") {
      val processStr = "validate then (save recover error-message)"
      val xml = ProcessUI.processStringToXml(processStr)
      val steps = (xml child "step").toList
      assert(steps.size == 2)
      assert((steps(0) attValue "name") == "validate")
      assert((steps(1) attValue "type") == "group")
      assert((steps(1) attValue "combinator") == "then")
      val groupChildren = (steps(1) child "step").toList
      assert(groupChildren.size == 2)
      assert((groupChildren(0) attValue "name") == "save")
      assert((groupChildren(1) attValue "name") == "error-message")
      assert((groupChildren(1) attValue "combinator") == "recover")

      val serialized = ProcessUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip conditions with `then` and `else`") {
      val processStr = """if ("$valid = true()") then save else error-message"""
      val xml = ProcessUI.processStringToXml(processStr)
      val steps = (xml child "step").toList
      assert(steps.size == 1)
      assert((steps.head attValue "type") == "condition")
      assert((steps.head attValue "condition") == "$valid = true()")

      val serialized = ProcessUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip conditions with multiple steps in branches") {
      val processStr = """if ("$valid = true()") then (save then success-message("saved")) else error-message"""
      val xml = ProcessUI.processStringToXml(processStr)
      val serialized = ProcessUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should deserialize and serialize `<processes>` metadata") {
      val xmlConfig = ProcessUI.xmlStringToMutableNodeInfoRootElem(
        """<processes>
          |  <process name="save">validate then save</process>
          |  <process name="review">if ("true()") then review else edit</process>
          |</processes>""".stripMargin
      )

      val deserialized = ProcessUI.deserializeProcesses(xmlConfig)
      val procList = (deserialized child "process").toList
      assert(procList.size == 2)
      assert((procList(0) attValue "name") == "save")
      assert((procList(0) child "step").toList.size == 2)
      assert((procList(1) attValue "name") == "review")
      assert((procList(1) child "step").toList.size == 1)

      val serialized = ProcessUI.serializeProcesses(deserialized)
      val resProcList = (serialized child "process").toList
      assert(resProcList.size == 2)
      assert((resProcList(0) attValue "name") == "save")
      assert(resProcList(0).stringValue == "validate then save")
      assert((resProcList(1) attValue "name") == "review")
      assert(resProcList(1).stringValue == """if ("true()") then review else edit""")
    }

    it("should generate templates") {
      val actionXml = ProcessUI.newActionTemplateXml("validate", "then")
      assert((actionXml attValue "type") == "action")
      assert((actionXml attValue "name") == "validate")
      assert((actionXml attValue "combinator") == "then")
      assert((actionXml child "param").nonEmpty)

      val groupXml = ProcessUI.newGroupTemplateXml("recover")
      assert((groupXml attValue "type") == "group")
      assert((groupXml attValue "combinator") == "recover")

      val condXml = ProcessUI.newConditionTemplateXml("then")
      assert((condXml attValue "type") == "condition")
      assert((condXml attValue "condition") == "true()")

      val elseXml = ProcessUI.newElseTemplateXml()
      assert(elseXml.localname == "else")
      assert((elseXml child "step").nonEmpty)

      val actionsXml = ProcessUI.availableActionsXml()
      assert(actionsXml.localname == "actions")
      assert((actionsXml child "action").nonEmpty)
    }

    describe("Indented process serialization") {

      it("should indent simple action sequences") {
        val xml = ProcessUI.processStringToXml("""validate then save recover error-message("error")""")
        val expected =
          """validate
            |then save
            |recover error-message("error")""".stripMargin
        val indented = ProcessUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
        assert(ProcessUI.xmlToIndentedProcessString(xml) == expected)
      }

      it("should not add newlines to a single action") {
        val xml = ProcessUI.processStringToXml("save")
        assert(ProcessUI.xmlToProcessString(xml, indent = true) == "save")
      }

      it("should indent groups") {
        val xml = ProcessUI.processStringToXml("validate then (save recover error-message)")
        val expected =
          """validate
            |then (
            |    save
            |    recover error-message
            |)""".stripMargin
        val indented = ProcessUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent conditions with single-step branches") {
        val xml = ProcessUI.processStringToXml("""if ("$valid = true()") then save else error-message""")
        val expected =
          """if ("$valid = true()") then
            |    save
            |else
            |    error-message""".stripMargin
        val indented = ProcessUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent conditions with multiple steps in branches") {
        val xml = ProcessUI.processStringToXml("""if ("$valid = true()") then (save then success-message("saved")) else (error-message recover log)""")
        val expected =
          """if ("$valid = true()") then (
            |    save
            |    then success-message("saved")
            |)
            |else (
            |    error-message
            |    recover log
            |)""".stripMargin
        val indented = ProcessUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent condition without else branch") {
        val xml = ProcessUI.processStringToXml("""if ("$valid = true()") then (save then success-message("saved"))""")
        val expected =
          """if ("$valid = true()") then (
            |    save
            |    then success-message("saved")
            |)""".stripMargin
        val indented = ProcessUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent nested groups and conditions") {
        val xml = ProcessUI.processStringToXml(
          """(validate then if ("$valid = true()") then (save then success-message("saved")) else error-message then navigate)"""
        )
        val expected =
          """(
            |    validate
            |    then if ("$valid = true()") then (
            |        save
            |        then success-message("saved")
            |    )
            |    else
            |        error-message
            |    then navigate
            |)""".stripMargin
        val indented = ProcessUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent chained conditions (else if)") {
        val xml = ProcessUI.processStringToXml("""if ("$c1") then save else if ("$c2") then edit else review""")
        val expected =
          """if ("$c1") then
            |    save
            |else if ("$c2") then
            |    edit
            |else
            |    review""".stripMargin
        val indented = ProcessUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("indented process strings must parse and round-trip correctly") {
        val testProcesses = List(
          """validate then save recover error-message("error")""",
          """validate then (save recover error-message)""",
          """if ("$valid = true()") then save else error-message""",
          """if ("$valid = true()") then (save then success-message("saved")) else error-message""",
          """if ("$valid = true()") then (save then success-message("saved")) else (error-message recover log)""",
          """(validate then if ("$valid = true()") then (save then success-message("saved")) else error-message then navigate)""",
          """if ("$c1") then save else if ("$c2") then edit else review"""
        )
        for (proc <- testProcesses) {
          val xml = ProcessUI.processStringToXml(proc)
          val indented = ProcessUI.xmlToProcessString(xml, indent = true)
          val originalParsed = org.orbeon.oxf.fr.process.ProcessParser.parse(proc)
          val indentedParsed = org.orbeon.oxf.fr.process.ProcessParser.parse(indented)
          assert(indentedParsed == originalParsed)
        }
      }
    }

    describe("ProcessParams metadata and dialog validation helpers (#7871)") {

      it("should resolve unnamed default parameters matching runtime semantics") {
        assert(ProcessParams.resolveUnnamedParamName("validate", "warning")              == "level")
        assert(ProcessParams.resolveUnnamedParamName("success-message", "save-success")  == "resource")
        assert(ProcessParams.resolveUnnamedParamName("error-message", "database-error")  == "resource")
        assert(ProcessParams.resolveUnnamedParamName("confirm", "confirm-msg")           == "resource")
        assert(ProcessParams.resolveUnnamedParamName("navigate", "/fr/orbeon/builder")   == "uri")
        assert(ProcessParams.resolveUnnamedParamName("navigate", "https://orbeon.com")   == "uri")
        assert(ProcessParams.resolveUnnamedParamName("navigate", "oxf.fr.detail.close")  == "property")
        assert(ProcessParams.resolveUnnamedParamName("send", "oxf.fr.detail.send")       == "property")
        assert(ProcessParams.resolveUnnamedParamName("process", "my-proc")               == "name")
        assert(ProcessParams.resolveUnnamedParamName("rollback", "in-memory-form-data")  == "changes")
        assert(ProcessParams.resolveUnnamedParamName("save", "true")                     == "")
      }

      it("should emit only Required and Common parameters by default and support singleParamTemplateXml") {
        // `save` has only Advanced parameters -> none inserted by default
        assert((ProcessUI.actionParamsXml("save") child "param").isEmpty)

        // `result-dialog` takes no parameters
        assert((ProcessUI.actionParamsXml("result-dialog") child "param").isEmpty)

        // `send` shows `uri` and `method` by default
        val sendDefaultNames = (ProcessUI.actionParamsXml("send") child "param").map(_ attValue "name").toList
        assert(sendDefaultNames == List("uri", "method"))

        // `control-setvalue` shows `control-name` and `value` by default
        val setvalDefaultNames = (ProcessUI.actionParamsXml("control-setvalue") child "param").map(_ attValue "name").toList
        assert(setvalDefaultNames == List("control-name", "value"))

        // `sleep` defaults to `1000ms` (with required duration unit)
        val sleepParams = (ProcessUI.actionParamsXml("sleep") child "param").toList
        assert(sleepParams.map(_ attValue "name") == List("duration"))
        assert(sleepParams.head.stringValue == "1000ms")

        // Adding an Advanced parameter vs a custom parameter
        val draftParam = ProcessUI.singleParamTemplateXml("save", "draft")
        assert((draftParam attValue "name") == "draft")
        assert((draftParam attValue "custom") == "false")
        assert(draftParam.stringValue == "true")

        val customParam = ProcessUI.singleParamTemplateXml("xf:dispatch", ProcessUI.CustomParamSentinel)
        assert((customParam attValue "name") == "")
        assert((customParam attValue "custom") == "true")
      }

      it("should detect AVT mode on deserialization and switch to named syntax when an unnamed param becomes an AVT") {
        val xml = ProcessUI.processStringToXml("""save(draft = "{$is-draft}") then validate("warning") then xf:dispatch(name = "my-event", my-prop = "{$val}")""")
        val steps = (xml child "step").toList

        val saveDraft = (steps(0) child "param").head
        assert((saveDraft attValue "name") == "draft")
        assert((saveDraft attValue "avt-mode") == "true")
        assert(ProcessUI.defaultValueWhenLeavingAvtMode("save", "draft", "{$is-draft}") == "true")

        val validateLevel = (steps(1) child "param").head
        assert((validateLevel attValue "name") == "level")
        assert((validateLevel attValue "unnamed") == "true")
        assert((validateLevel attValue "avt-mode") == "false")

        // Mutating an unnamed param to an AVT must serialize as a named param so runtime evaluates the AVT
        org.orbeon.oxf.xforms.action.XFormsAPI.setvalue(Seq(validateLevel), "{$dyn-level}")
        val reserialized = ProcessUI.xmlToProcessString(xml)
        assert(reserialized.contains("""validate(level = "{$dyn-level}")"""))

        // Custom param on `xf:dispatch`
        val dispatchParams = (steps(2) child "param").toList
        assert(dispatchParams.size == 2)
        assert((dispatchParams(1) attValue "name") == "my-prop")
        assert((dispatchParams(1) attValue "custom") == "true")
        assert(ProcessUI.paramValueMode(dispatchParams(1)) == "avt")
        assert(ProcessUI.isCustomParamNameValid(dispatchParams(1)))
      }

      it("should validate parameter static values, custom parameter names, and step-level rules") {
        val config = ProcessUI.xmlStringToMutableNodeInfoRootElem(
          """<processes>
            |  <process name="p1">validate(level = "") then send(method = "POST") then control-setvalue(control-name = "c1", value = "1", at = "all", section-name = "s1")</process>
            |  <process name="p2">save(draft = "true")</process>
            |</processes>""".stripMargin
        )
        val deserialized = ProcessUI.deserializeProcesses(config)
        val emptyContext = ProcessUI.xmlStringToMutableNodeInfoRootElem("<form-context/>")

        val p1Steps = ((deserialized child "process").head child "step").toList

        // 1. `validate(level = "")`: `level` param itself is invalid, step is not double-flagged
        val levelParam = (p1Steps(0) child "param").head
        assert(! ProcessUI.isParamValueValid(levelParam, emptyContext))
        assert(ProcessUI.isActionStepValid(p1Steps(0), emptyContext))

        // 2. `send(method = "POST")`: neither `uri` nor `property` is present -> step-level error
        assert(! ProcessUI.isActionStepValid(p1Steps(1), emptyContext))

        // 3. `control-setvalue` with both `at` and `section-name` -> mutually exclusive step-level error
        assert(! ProcessUI.isActionStepValid(p1Steps(2), emptyContext))

        // 4. `serializeProcesses` must not leak namespace declarations onto `<processes>`
        val serialized = ProcessUI.serializeProcesses(deserialized)
        val serializedStr = TransformerUtils.tinyTreeToString(serialized)
        assert(! serializedStr.contains("xmlns:"))

        // 5. `availableActionsXml` exposes all four UI categories matching XBL/resource keys
        val actionsDoc = ProcessUI.availableActionsXml()
        val categories = (actionsDoc child "action").flatMap(_.attValueOpt("category")).toSet
        assert(categories == Set("form-runner", "sub-process", "flow-control", "xforms"))
      }
    }
  }
}
