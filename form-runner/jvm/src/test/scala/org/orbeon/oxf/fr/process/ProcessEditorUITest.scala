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
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class ProcessEditorUITest extends DocumentTestBase with ResourceManagerSupport with AnyFunSpecLike {

  describe("ProcessUI XML conversion") {

    it("should round-trip simple actions") {
      val processStr = "validate then save"
      val xml = ProcessEditorUI.processStringToXml(processStr)
      val serialized = ProcessEditorUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip actions with combinators and parameters") {
      val processStr = """validate then save recover error-message("error")"""
      val xml = ProcessEditorUI.processStringToXml(processStr)
      val steps = (xml child "step").toList
      assert(steps.size == 3)
      assert((steps(0) attValue "name") == "validate")
      assert((steps(1) attValue "name") == "save")
      assert((steps(1) attValue "combinator") == "then")
      assert((steps(2) attValue "name") == "error-message")
      assert((steps(2) attValue "combinator") == "recover")

      val serialized = ProcessEditorUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip groups") {
      val processStr = "validate then (save recover error-message)"
      val xml = ProcessEditorUI.processStringToXml(processStr)
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

      val serialized = ProcessEditorUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip conditions with `then` and `else`") {
      val processStr = """if ("$valid = true()") then save else error-message"""
      val xml = ProcessEditorUI.processStringToXml(processStr)
      val steps = (xml child "step").toList
      assert(steps.size == 1)
      assert((steps.head attValue "type") == "condition")
      assert((steps.head attValue "condition") == "$valid = true()")

      val serialized = ProcessEditorUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should round-trip conditions with multiple steps in branches") {
      val processStr = """if ("$valid = true()") then (save then success-message("saved")) else error-message"""
      val xml = ProcessEditorUI.processStringToXml(processStr)
      val serialized = ProcessEditorUI.xmlToProcessString(xml)
      assert(serialized == processStr)
    }

    it("should deserialize and serialize `<processes>` metadata") {
      val xmlConfig = ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(
        """<processes>
          |  <process name="save">validate then save</process>
          |  <process name="review">if ("true()") then review else edit</process>
          |</processes>""".stripMargin
      )

      val deserialized = ProcessEditorUI.deserializeProcesses(xmlConfig)
      val procList = (deserialized child "process").toList
      assert(procList.size == 2)
      assert((procList(0) attValue "name") == "save")
      assert((procList(0) child "step").toList.size == 2)
      assert((procList(1) attValue "name") == "review")
      assert((procList(1) child "step").toList.size == 1)

      val serialized = ProcessEditorUI.serializeProcesses(deserialized)
      val resProcList = (serialized child "process").toList
      assert(resProcList.size == 2)
      assert((resProcList(0) attValue "name") == "save")
      assert(resProcList(0).stringValue == "validate then save")
      assert((resProcList(1) attValue "name") == "review")
      assert(resProcList(1).stringValue == """if ("true()") then review else edit""")
    }

    it("should generate templates") {
      val actionXml = ProcessEditorUI.newActionTemplateXml("validate", "then")
      assert((actionXml attValue "type") == "action")
      assert((actionXml attValue "name") == "validate")
      assert((actionXml attValue "combinator") == "then")
      assert((actionXml child "param").nonEmpty)

      val groupXml = ProcessEditorUI.newGroupTemplateXml("recover")
      assert((groupXml attValue "type") == "group")
      assert((groupXml attValue "combinator") == "recover")

      val condXml = ProcessEditorUI.newConditionTemplateXml("then")
      assert((condXml attValue "type") == "condition")
      assert((condXml attValue "condition") == "true()")

      val blankActionXml = ProcessEditorUI.newActionTemplateXml("", "then")
      assert((blankActionXml attValue "type") == "action")
      assert((blankActionXml attValue "name") == "")
      assert((blankActionXml child "param").isEmpty)

      val elseXml = ProcessEditorUI.newElseTemplateXml()
      assert(elseXml.localname == "else")
      val elseSteps = (elseXml child "step").toList
      assert(elseSteps.size == 1)
      assert((elseSteps.head attValue "name") == "")

      val emptyProcXml = ProcessEditorUI.processStringToXml("")
      val emptySteps = (emptyProcXml child "step").toList
      assert(emptySteps.size == 1)
      assert((emptySteps.head attValue "name") == "")

      val actionsXml = ProcessEditorUI.availableActionsXml()
      assert(actionsXml.localname == "actions")
      assert((actionsXml child "action").nonEmpty)
    }

    describe("Indented process serialization") {

      it("should indent simple action sequences") {
        val xml = ProcessEditorUI.processStringToXml("""validate then save recover error-message("error")""")
        val expected =
          """validate
            |then save
            |recover error-message("error")""".stripMargin
        val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
        assert(ProcessEditorUI.xmlToIndentedProcessString(xml) == expected)
      }

      it("should not add newlines to a single action") {
        val xml = ProcessEditorUI.processStringToXml("save")
        assert(ProcessEditorUI.xmlToProcessString(xml, indent = true) == "save")
      }

      it("should indent groups") {
        val xml = ProcessEditorUI.processStringToXml("validate then (save recover error-message)")
        val expected =
          """validate
            |then (
            |    save
            |    recover error-message
            |)""".stripMargin
        val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent conditions with single-step branches") {
        val xml = ProcessEditorUI.processStringToXml("""if ("$valid = true()") then save else error-message""")
        val expected =
          """if ("$valid = true()") then
            |    save
            |else
            |    error-message""".stripMargin
        val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent conditions with multiple steps in branches") {
        val xml = ProcessEditorUI.processStringToXml("""if ("$valid = true()") then (save then success-message("saved")) else (error-message recover log)""")
        val expected =
          """if ("$valid = true()") then (
            |    save
            |    then success-message("saved")
            |)
            |else (
            |    error-message
            |    recover log
            |)""".stripMargin
        val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent condition without else branch") {
        val xml = ProcessEditorUI.processStringToXml("""if ("$valid = true()") then (save then success-message("saved"))""")
        val expected =
          """if ("$valid = true()") then (
            |    save
            |    then success-message("saved")
            |)""".stripMargin
        val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent nested groups and conditions") {
        val xml = ProcessEditorUI.processStringToXml(
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
        val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
        assert(indented == expected)
      }

      it("should indent chained conditions (else if)") {
        val xml = ProcessEditorUI.processStringToXml("""if ("$c1") then save else if ("$c2") then edit else review""")
        val expected =
          """if ("$c1") then
            |    save
            |else if ("$c2") then
            |    edit
            |else
            |    review""".stripMargin
        val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
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
          val xml = ProcessEditorUI.processStringToXml(proc)
          val indented = ProcessEditorUI.xmlToProcessString(xml, indent = true)
          val originalParsed = org.orbeon.oxf.fr.process.ProcessParser.parse(proc)
          val indentedParsed = org.orbeon.oxf.fr.process.ProcessParser.parse(indented)
          assert(indentedParsed == originalParsed)
        }
      }
    }

    describe("ProcessParams metadata and dialog validation helpers (#7871)") {

      it("should resolve unnamed default parameters matching runtime semantics") {
        assert(ProcessEditorParams.resolveUnnamedParamName("validate", "warning")              == "level")
        assert(ProcessEditorParams.resolveUnnamedParamName("success-message", "save-success")  == "resource")
        assert(ProcessEditorParams.resolveUnnamedParamName("error-message", "database-error")  == "resource")
        assert(ProcessEditorParams.resolveUnnamedParamName("confirm", "confirm-msg")           == "resource")
        assert(ProcessEditorParams.resolveUnnamedParamName("navigate", "/fr/orbeon/builder")   == "uri")
        assert(ProcessEditorParams.resolveUnnamedParamName("navigate", "https://orbeon.com")   == "uri")
        assert(ProcessEditorParams.resolveUnnamedParamName("navigate", "oxf.fr.detail.close")  == "property")
        assert(ProcessEditorParams.resolveUnnamedParamName("send", "oxf.fr.detail.send")       == "property")
        assert(ProcessEditorParams.resolveUnnamedParamName("process", "my-proc")               == "name")
        assert(ProcessEditorParams.resolveUnnamedParamName("rollback", "in-memory-form-data")  == "changes")
        assert(ProcessEditorParams.resolveUnnamedParamName("save", "true")                     == "")
      }

      it("should emit only Required and Common parameters by default and support singleParamTemplateXml") {
        // `save` has only Advanced parameters -> none inserted by default
        assert((ProcessEditorUI.actionParamsXml("save") child "param").isEmpty)

        // `result-dialog` takes no parameters
        assert((ProcessEditorUI.actionParamsXml("result-dialog") child "param").isEmpty)

        // `send` shows `uri` and `method` by default
        val sendDefaultNames = (ProcessEditorUI.actionParamsXml("send") child "param").map(_ attValue "name").toList
        assert(sendDefaultNames == List("uri", "method"))

        // `control-setvalue` shows `control-name` and `value` by default
        val setvalDefaultNames = (ProcessEditorUI.actionParamsXml("control-setvalue") child "param").map(_ attValue "name").toList
        assert(setvalDefaultNames == List("control-name", "value"))

        // `sleep` defaults to `1000` with `@unit="ms"` for `fr:quantity`
        val sleepParams = (ProcessEditorUI.actionParamsXml("sleep") child "param").toList
        assert(sleepParams.map(_ attValue "name") == List("duration"))
        assert(sleepParams.head.stringValue == "1000")
        assert((sleepParams.head attValue "unit") == "ms")

        // `sleep` duration deserializes into amount + `@unit` and serializes back to `<amount><unit>`
        val sleepXml = ProcessEditorUI.processStringToXml("""sleep(duration = "5s") then sleep(duration = "{$delay}")""")
        val sleepSteps = (sleepXml child "step").toList
        val staticDur  = (sleepSteps(0) child "param").head
        assert(staticDur.stringValue == "5")
        assert((staticDur attValue "unit") == "s")
        assert((staticDur attValue "avt-mode") == "false")
        val avtDur = (sleepSteps(1) child "param").head
        assert(avtDur.stringValue == "{$delay}")
        assert((avtDur attValue "avt-mode") == "true")
        assert(ProcessEditorUI.xmlToProcessString(sleepXml) == """sleep(duration = "5s") then sleep(duration = "{$delay}")""")

        // Adding an Advanced parameter vs a custom parameter
        val draftParam = ProcessEditorUI.singleParamTemplateXml("save", "draft")
        assert((draftParam attValue "name") == "draft")
        assert((draftParam attValue "custom") == "false")
        assert(draftParam.stringValue == "true")

        val customParam = ProcessEditorUI.singleParamTemplateXml("xf:dispatch", ProcessEditorUI.CustomParamSentinel)
        assert((customParam attValue "name") == "")
        assert((customParam attValue "custom") == "true")
      }

      it("should detect AVT mode on deserialization and switch to named syntax when an unnamed param becomes an AVT") {
        val xml = ProcessEditorUI.processStringToXml("""save(draft = "{$is-draft}") then validate("warning") then xf:dispatch(name = "my-event", my-prop = "{$val}")""")
        val steps = (xml child "step").toList

        val saveDraft = (steps(0) child "param").head
        assert((saveDraft attValue "name") == "draft")
        assert((saveDraft attValue "avt-mode") == "true")
        assert(ProcessEditorUI.defaultValueWhenLeavingAvtMode("save", "draft", "{$is-draft}") == "true")

        val validateLevel = (steps(1) child "param").head
        assert((validateLevel attValue "name") == "level")
        assert((validateLevel attValue "unnamed") == "true")
        assert((validateLevel attValue "avt-mode") == "false")

        // Mutating an unnamed param to an AVT must serialize as a named param so runtime evaluates the AVT
        org.orbeon.oxf.xforms.action.XFormsAPI.setvalue(Seq(validateLevel), "{$dyn-level}")
        val reserialized = ProcessEditorUI.xmlToProcessString(xml)
        assert(reserialized.contains("""validate(level = "{$dyn-level}")"""))

        // Custom param on `xf:dispatch`
        val dispatchParams = (steps(2) child "param").toList
        assert(dispatchParams.size == 2)
        assert((dispatchParams(1) attValue "name") == "my-prop")
        assert((dispatchParams(1) attValue "custom") == "true")
        assert(ProcessEditorUI.paramValueMode(dispatchParams(1)) == "avt")
        assert(ProcessEditorUI.isCustomParamNameValid(dispatchParams(1)))
      }

      it("should validate parameter static values, custom parameter names, and step-level rules") {
        val config = ProcessEditorUI.xmlStringToMutableNodeInfoRootElem(
          """<processes>
            |  <process name="p1">validate(level = "") then send(method = "POST") then control-setvalue(control-name = "c1", value = "1", at = "all", section-name = "s1")</process>
            |  <process name="p2">save(draft = "true")</process>
            |</processes>""".stripMargin
        )
        val deserialized = ProcessEditorUI.deserializeProcesses(config)
        val emptyContext = ProcessEditorUI.xmlStringToMutableNodeInfoRootElem("<form-context/>")

        val p1Steps = ((deserialized child "process").head child "step").toList

        // 1. `validate(level = "")`: `level` param itself is invalid, step is not double-flagged
        val levelParam = (p1Steps(0) child "param").head
        assert(! ProcessEditorUI.isParamValueValid(levelParam, emptyContext))
        assert(ProcessEditorUI.isActionStepValid(p1Steps(0), emptyContext))

        // 2. `send(method = "POST")`: neither `uri` nor `property` is present -> step-level error
        assert(! ProcessEditorUI.isActionStepValid(p1Steps(1), emptyContext))

        // 3. `control-setvalue` with both `at` and `section-name` -> mutually exclusive step-level error
        assert(! ProcessEditorUI.isActionStepValid(p1Steps(2), emptyContext))

        // 4. `serializeProcesses` must not leak namespace declarations onto `<processes>`
        val serialized = ProcessEditorUI.serializeProcesses(deserialized)
        val serializedStr = TransformerUtils.tinyTreeToString(serialized)
        assert(! serializedStr.contains("xmlns:"))

        // 5. `availableActionsXml` exposes the 8 UI action categories plus built-in `<sub-process>` entries
        val actionsDoc = ProcessEditorUI.availableActionsXml()
        val categories = (actionsDoc child "action").flatMap(_.attValueOpt("category")).toSet
        assert(
          categories == Set(
            "data-persistence",
            "validation-errors",
            "dialogs-messages",
            "navigation-modes",
            "sections-wizard",
            "integrations",
            "flow-control",
            "xforms"
          )
        )
        assert((actionsDoc child "action").exists(a => (a attValue "name") == "process" && (a attValue "category") == "flow-control"))
        assert((actionsDoc child "action").exists(a => (a attValue "name") == "open-rendered-format" && (a attValue "keywords").contains("pdf")))
        assert(! (actionsDoc child "action").exists(a => (a attValue "name") == "require-uploads"))
        assert((actionsDoc child "sub-process").exists(sp => (sp attValue "name") == "require-uploads" && (sp attValue "deprecated") == "false"))
      }

      it("should normalize bare sub-process calls into `process` steps and validate process names") {
        val xml = ProcessEditorUI.processStringToXml("""require-uploads then my-helper then process("other") then process(name = "{$dyn}")""")
        val steps = (xml child "step").toList
        assert(steps.size == 4)
        assert(steps.forall(s => (s attValue "name") == "process"))
        assert(steps.map(s => (s child "param").head.stringValue) == List("require-uploads", "my-helper", "other", "{$dyn}"))
        assert(((steps(3) child "param").head attValue "avt-mode") == "true")

        val serialized = ProcessEditorUI.xmlToProcessString(xml)
        assert(serialized == """process(name = "require-uploads") then process(name = "my-helper") then process("other") then process(name = "{$dyn}")""")

        // Process name syntax matches `ProcessParser.Name` (minus `:`)
        assert(ProcessEditorParams.ProcessNameRegex.matches("save-final"))
        assert(ProcessEditorParams.ProcessNameRegex.matches("_helper_2"))
        assert(ProcessEditorParams.ProcessNameRegex.matches("-custom"))
        assert(! ProcessEditorParams.ProcessNameRegex.matches("my.process"))
        assert(! ProcessEditorParams.ProcessNameRegex.matches("2026-submit"))
        assert(! ProcessEditorParams.ProcessNameRegex.matches("xf:custom"))
        assert(! ProcessEditorParams.ProcessNameRegex.matches("bad name"))
      }

      it("should syntax-highlight process definitions into safe HTML spans") {
        val html = ProcessSyntaxHighlighter.highlightToHtml(
          """if ("$a < 1 & $b") then
            |    sleep(duration = "1000ms")
            |else
            |    xf:dispatch(name = 'my-ev')""".stripMargin
        )
        assert(html.contains("""<span class="fr-proc-tok-keyword">if</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-string">"$a &lt; 1 &amp; $b"</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-keyword">then</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-action">sleep</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-param">duration</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-string">"1000ms"</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-keyword">else</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-action">xf:dispatch</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-param">name</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-string">'my-ev'</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-punct">(</span>"""))
        assert(html.contains("""<span class="fr-proc-tok-punct">=</span>"""))
      }
    }
  }
}
