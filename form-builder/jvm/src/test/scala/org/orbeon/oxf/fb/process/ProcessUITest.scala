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
  }
}
