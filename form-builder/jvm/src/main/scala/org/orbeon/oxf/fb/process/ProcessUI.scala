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

import org.orbeon.oxf.fr.process.ProcessParser
import org.orbeon.oxf.fr.process.ProcessParser.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport

import scala.util.{Failure, Success, Try}


object ProcessUI {

  case class ActionParamDef(name: String, defaultVal: String = "")
  case class ActionDef(name: String, label: String, params: List[ActionParamDef] = Nil)

  val DefaultParamNames: Map[String, String] = Map(
    "success-message"    -> "message",
    "error-message"      -> "message",
    "confirm"            -> "message",
    "email"              -> "name",
    "set-workflow-stage" -> "name",
    "log"                -> "message",
    "result-dialog"      -> "name",
    "xf:send"            -> "submission",
    "xf:dispatch"        -> "name",
    "xf:show"            -> "dialog",
    "xf:hide"            -> "dialog"
  )

  val ActionDefinitions: List[ActionDef] = List(
    // Common Form Runner actions
    ActionDef("save",                  "Save",                      Nil),
    ActionDef("validate",              "Validate",                  List(ActionParamDef("level", "error"))),
    ActionDef("success-message",       "Success Message",           List(ActionParamDef("message", "Data saved successfully"))),
    ActionDef("error-message",         "Error Message",             List(ActionParamDef("message", "An error occurred"))),
    ActionDef("confirm",               "Confirmation Dialog",       List(ActionParamDef("message", "Are you sure?"))),
    ActionDef("navigate",              "Navigate",                  List(ActionParamDef("uri", "/"))),
    ActionDef("email",                 "Send Email",                List(ActionParamDef("name", ""))),
    ActionDef("set-workflow-stage",    "Set Workflow Stage",        List(ActionParamDef("name", ""))),
    ActionDef("change-mode",           "Change Mode",               List(ActionParamDef("mode", "edit"))),
    ActionDef("control-setvalue",      "Set Control Value",         List(ActionParamDef("control", ""), ActionParamDef("value", ""))),
    ActionDef("result-dialog",         "Result Dialog",             List(ActionParamDef("name", ""))),
    ActionDef("open-rendered-format",  "Open Rendered Format",      List(ActionParamDef("format", "pdf"))),
    ActionDef("log",                   "Log Message",               List(ActionParamDef("message", ""))),
    ActionDef("sleep",                 "Sleep",                     List(ActionParamDef("duration", "1000"))),
    ActionDef("send",                  "Send HTTP",                 List(ActionParamDef("uri", ""), ActionParamDef("method", "POST"))),
    ActionDef("pending-uploads",       "Check Pending Uploads",     Nil),
    ActionDef("relinquish-lease",      "Relinquish Lease",          Nil),
    ActionDef("visit-all",             "Show All Errors",           Nil),
    ActionDef("unvisit-all",           "Clear Error Highlights",    Nil),
    ActionDef("expand-all",            "Expand All Sections",       Nil),
    ActionDef("collapse-all",          "Collapse All Sections",     Nil),
    ActionDef("expand-invalid",        "Expand Invalid Sections",   Nil),
    ActionDef("captcha",               "Verify CAPTCHA",            Nil),
    ActionDef("captcha-reset",         "Reset CAPTCHA",             Nil),
    ActionDef("wizard-prev",           "Wizard Previous",           Nil),
    ActionDef("wizard-next",           "Wizard Next",               Nil),
    ActionDef("new-to-edit",           "Switch New to Edit",        Nil),
    ActionDef("edit-to-new",           "Switch Edit to New",        Nil),
    ActionDef("review",                "Go to Review",              Nil),
    ActionDef("edit",                  "Go to Edit",                Nil),

    // XForms actions
    ActionDef("xf:setvalue",           "XForms: Set Value",         List(ActionParamDef("ref", ""), ActionParamDef("value", ""))),
    ActionDef("xf:show",               "XForms: Show Dialog",       List(ActionParamDef("dialog", ""))),
    ActionDef("xf:hide",               "XForms: Hide Dialog",       List(ActionParamDef("dialog", ""))),
    ActionDef("xf:dispatch",           "XForms: Dispatch Event",    List(ActionParamDef("name", ""), ActionParamDef("targetid", ""))),
    ActionDef("xf:send",               "XForms: Send Submission",   List(ActionParamDef("submission", ""))),

    // Process flow control actions
    ActionDef("terminate-with-success", "Terminate with Success",   Nil),
    ActionDef("terminate-with-failure", "Terminate with Failure",   Nil),
    ActionDef("continue-with-success",  "Continue with Success",    Nil),
    ActionDef("continue-with-failure",  "Continue with Failure",    Nil),
    ActionDef("suspend",                "Suspend Process",          Nil),
    ActionDef("resume-with-success",    "Resume with Success",      Nil),
    ActionDef("resume-with-failure",    "Resume with Failure",      Nil)
  )

  private val ActionDefsByName: Map[String, ActionDef] =
    ActionDefinitions.map(a => a.name -> a).toMap

  def xmlStringToMutableNodeInfoRootElem(xml: String): NodeInfo = {
    val doc = XFormsCrossPlatformSupport.stringToTinyTree(XPath.GlobalConfiguration, xml, handleXInclude = false, handleLexical = false)
    TransformerUtils.extractAsMutableDocument(doc).rootElement
  }

  /*@XPathFunction*/
  def availableActionsXml(): NodeInfo = {
    val itemsXml = ActionDefinitions.map { a =>
      val paramsXml = a.params.map { p =>
        s"""<param name="${p.name.escapeXmlForAttribute}" default="${p.defaultVal.escapeXmlForAttribute}"/>"""
      }.mkString
      s"""<action name="${a.name.escapeXmlForAttribute}" label="${a.label.escapeXmlForAttribute}">$paramsXml</action>"""
    }.mkString
    xmlStringToMutableNodeInfoRootElem(s"""<actions>$itemsXml</actions>""")
  }

  /*@XPathFunction*/
  def actionParamsXml(actionName: String): NodeInfo = {
    val paramsXml = actionParamsXmlString(actionName)
    xmlStringToMutableNodeInfoRootElem(s"""<params>$paramsXml</params>""")
  }

  private def actionParamsXmlString(actionName: String): String =
    ActionDefsByName.get(actionName) match {
      case Some(actionDef) =>
        actionDef.params.map { p =>
          s"""<param name="${p.name.escapeXmlForAttribute}">${p.defaultVal.escapeXmlMinimal}</param>"""
        }.mkString
      case None =>
        ""
    }

  /*@XPathFunction*/
  def newActionTemplateXml(name: String, combinator: String): NodeInfo = {
    val paramsXml = actionParamsXmlString(name)
    xmlStringToMutableNodeInfoRootElem(
      s"""<step
         |    type="action"
         |    combinator="${combinator.trimAllToOpt.getOrElse("then").escapeXmlForAttribute}"
         |    name="${name.escapeXmlForAttribute}">$paramsXml</step>""".stripMargin
    )
  }

  /*@XPathFunction*/
  def newGroupTemplateXml(combinator: String): NodeInfo =
    xmlStringToMutableNodeInfoRootElem(
      s"""<step
         |    type="group"
         |    combinator="${combinator.trimAllToOpt.getOrElse("then").escapeXmlForAttribute}"/>""".stripMargin
    )

  /*@XPathFunction*/
  def newConditionTemplateXml(combinator: String): NodeInfo =
    xmlStringToMutableNodeInfoRootElem(
      s"""<step
         |    type="condition"
         |    combinator="${combinator.trimAllToOpt.getOrElse("then").escapeXmlForAttribute}"
         |    condition="true()">
         |    <then/>
         |</step>""".stripMargin
    )

  /*@XPathFunction*/
  def newElseTemplateXml(): NodeInfo =
    xmlStringToMutableNodeInfoRootElem(
      s"""<else>
         |    <step type="action" combinator="then" name="save"/>
         |</else>""".stripMargin
    )

  // AST ExprNode to XML String
  def exprNodeToXmlString(expr: ExprNode, combinator: String): String = expr match {
    case ActionNode(name, params) =>
      val paramsXml = params.map {
        case (Some(pName), pVal) =>
          s"""<param name="${pName.escapeXmlForAttribute}">${pVal.escapeXmlMinimal}</param>"""
        case (None, pVal) =>
          val defaultName = DefaultParamNames.getOrElse(name, "")
          s"""<param name="${defaultName.escapeXmlForAttribute}" unnamed="true">${pVal.escapeXmlMinimal}</param>"""
      }.mkString
      s"""<step type="action" combinator="${combinator.escapeXmlForAttribute}" name="${name.escapeXmlForAttribute}">$paramsXml</step>"""

    case GroupNode(first, rest) =>
      val childrenXml = (("then", first) :: rest.map { case (c, e) => (c.name, e) }).map {
        case (comb, childExpr) => exprNodeToXmlString(childExpr, comb)
      }.mkString
      s"""<step type="group" combinator="${combinator.escapeXmlForAttribute}">$childrenXml</step>"""

    case ConditionNode(xpath, thenBranch, elseBranch) =>
      val thenStepsXml = thenBranch match {
        case GroupNode(first, rest) =>
          (("then", first) :: rest.map { case (c, e) => (c.name, e) }).map {
            case (comb, childExpr) => exprNodeToXmlString(childExpr, comb)
          }.mkString
        case single =>
          exprNodeToXmlString(single, "then")
      }
      val elseXml = elseBranch match {
        case Some(GroupNode(first, rest)) =>
          val elseSteps = (("then", first) :: rest.map { case (c, e) => (c.name, e) }).map {
            case (comb, childExpr) => exprNodeToXmlString(childExpr, comb)
          }.mkString
          s"""<else>$elseSteps</else>"""
        case Some(single) =>
          s"""<else>${exprNodeToXmlString(single, "then")}</else>"""
        case None =>
          ""
      }
      s"""<step type="condition" combinator="${combinator.escapeXmlForAttribute}" condition="${xpath.escapeXmlForAttribute}"><then>$thenStepsXml</then>$elseXml</step>"""
  }

  // Parse process string into XML String for <process>
  def processStringToXmlString(processString: String): String = {
    if (processString.trim.isEmpty) {
      """<step type="action" combinator="then" name="save"/>"""
    } else {
      Try(ProcessParser.parse(processString)) match {
        case Success(rootGroup) =>
          val steps = ("then", rootGroup.expr) :: rootGroup.rest.map { case (c, e) => (c.name, e) }
          steps.map { case (comb, expr) => exprNodeToXmlString(expr, comb) }.mkString
        case Failure(_) =>
          // Fallback if parsing fails: create a single save action step
          """<step type="action" combinator="then" name="save"/>"""
      }
    }
  }

  /*@XPathFunction*/
  def processStringToXml(processString: String): NodeInfo =
    xmlStringToMutableNodeInfoRootElem(s"""<process>${processStringToXmlString(processString)}</process>""")

  private val IndentSpaces = "    "

  private def serializeAction(step: NodeInfo): String = {
    val name = step attValueOpt "name" getOrElse ""
    val params = (step child "param").toList
    if (params.isEmpty) {
      name
    } else {
      val paramsStr = params.map { p =>
        val pName   = p attValueOpt "name" getOrElse ""
        val unnamed = (p attValueOpt "unnamed").contains("true")
        val pVal    = p.stringValue
        if (pName.nonEmpty && ! unnamed)
          s"""$pName = ${ProcessParser.quote(pVal)}"""
        else
          ProcessParser.quote(pVal)
      }.mkString(", ")
      s"$name($paramsStr)"
    }
  }

  // Serialize a single step NodeInfo back to process syntax
  def serializeStep(step: NodeInfo): String = {
    val stepType = step attValueOpt "type" getOrElse "action"
    stepType match {
      case "action" =>
        serializeAction(step)

      case "group" =>
        val childSteps = (step child "step").toList
        val serializedChildren = serializeStepSequence(childSteps)
        s"($serializedChildren)"

      case "condition" =>
        val conditionXPath = step attValueOpt "condition" getOrElse "true()"
        val thenSteps = (step / "then" / "step").toList
        val thenStr =
          if (thenSteps.isEmpty) "continue-with-success"
          else if (thenSteps.size == 1) serializeStep(thenSteps.head)
          else s"(${serializeStepSequence(thenSteps)})"

        val elseSteps = (step / "else" / "step").toList
        val elseStr =
          if (elseSteps.isEmpty) ""
          else if (elseSteps.size == 1) s" else ${serializeStep(elseSteps.head)}"
          else s" else (${serializeStepSequence(elseSteps)})"

        s"if (${ProcessParser.quote(conditionXPath)}) then $thenStr$elseStr"

      case _ =>
        ""
    }
  }

  def serializeStepSequence(steps: List[NodeInfo]): String =
    steps match {
      case Nil => ""
      case first :: rest =>
        val firstStr = serializeStep(first)
        val restStr = rest.map { s =>
          val comb = s attValueOpt "combinator" getOrElse "then"
          s" $comb ${serializeStep(s)}"
        }.mkString
        firstStr + restStr
    }

  def serializeIndentedStep(step: NodeInfo, indentLevel: Int, isFirst: Boolean): String = {
    val prefix     = IndentSpaces * indentLevel
    val combOpt    = if (isFirst) None else Some(step attValueOpt "combinator" getOrElse "then")
    val combPrefix = combOpt.map(_ + " ").getOrElse("")
    val stepType   = step attValueOpt "type" getOrElse "action"

    stepType match {
      case "action" =>
        s"$prefix$combPrefix${serializeAction(step)}"

      case "group" =>
        val childSteps = (step child "step").toList
        if (childSteps.isEmpty) {
          s"$prefix$combPrefix()"
        } else {
          val header = s"$prefix$combPrefix("
          val body   = serializeIndentedStepSequence(childSteps, indentLevel + 1)
          val footer = s"$prefix)"
          s"$header\n$body\n$footer"
        }

      case "condition" =>
        val conditionXPath = step attValueOpt "condition" getOrElse "true()"
        val quotedCond     = ProcessParser.quote(conditionXPath)
        val condHeader     = s"$prefix${combPrefix}if ($quotedCond) then"

        val rawThenSteps = (step / "then" / "step").toList
        val (thenSteps, isThenGroup) =
          if (rawThenSteps.size == 1 && (rawThenSteps.head attValueOpt "type").contains("group"))
            ((rawThenSteps.head child "step").toList, true)
          else
            (rawThenSteps, rawThenSteps.size > 1)

        val thenStr =
          if (thenSteps.isEmpty) {
            s"$condHeader\n$prefix${IndentSpaces}continue-with-success"
          } else if (isThenGroup) {
            val thenHeader = s"$condHeader ("
            val thenBody   = serializeIndentedStepSequence(thenSteps, indentLevel + 1)
            val thenFooter = s"$prefix)"
            s"$thenHeader\n$thenBody\n$thenFooter"
          } else {
            val singleStep = thenSteps.head
            s"$condHeader\n" + serializeIndentedStep(singleStep, indentLevel + 1, isFirst = true)
          }

        val rawElseBranch = (step child "else").toList
        val rawElseSteps  = rawElseBranch.flatMap(e => (e child "step").toList)

        val elseStrOpt =
          if (rawElseSteps.isEmpty) {
            None
          } else {
            val (elseSteps, isElseGroup) =
              if (rawElseSteps.size == 1 && (rawElseSteps.head attValueOpt "type").contains("group"))
                ((rawElseSteps.head child "step").toList, true)
              else
                (rawElseSteps, rawElseSteps.size > 1)

            if (isElseGroup) {
              val elseHeader = s"${prefix}else ("
              val elseBody   = serializeIndentedStepSequence(elseSteps, indentLevel + 1)
              val elseFooter = s"$prefix)"
              Some(s"$elseHeader\n$elseBody\n$elseFooter")
            } else {
              val singleStep   = elseSteps.head
              val isNestedCond = (singleStep attValueOpt "type").contains("condition")
              if (isNestedCond) {
                val nestedCondStr = serializeIndentedStep(singleStep, indentLevel, isFirst = true)
                Some(s"${prefix}else " + nestedCondStr.drop(prefix.length))
              } else {
                Some(s"${prefix}else\n" + serializeIndentedStep(singleStep, indentLevel + 1, isFirst = true))
              }
            }
          }

        thenStr + elseStrOpt.map("\n" + _).getOrElse("")

      case _ =>
        ""
    }
  }

  def serializeIndentedStepSequence(steps: List[NodeInfo], indentLevel: Int): String =
    steps.zipWithIndex.map { case (step, idx) =>
      serializeIndentedStep(step, indentLevel, isFirst = idx == 0)
    }.filter(_.nonEmpty).mkString("\n")

  /*@XPathFunction*/
  def xmlToProcessString(processElem: NodeInfo): String =
    xmlToProcessString(processElem, indent = false)

  /*@XPathFunction*/
  def xmlToProcessString(processElem: NodeInfo, indent: Boolean): String = {
    val steps =
      if (processElem.localname == "step")
        List(processElem)
      else
        (processElem child "step").toList
    if (indent)
      serializeIndentedStepSequence(steps, indentLevel = 0)
    else
      serializeStepSequence(steps)
  }

  /*@XPathFunction*/
  def xmlToIndentedProcessString(processElem: NodeInfo): String =
    xmlToProcessString(processElem, indent = true)

  /*@XPathFunction*/
  def deserializeProcesses(configNode: NodeInfo): NodeInfo = {
    val processes = (configNode / "process").toList
    val processesXml =
      if (processes.isEmpty) {
        """<process name=""><step type="action" combinator="then" name="save"/></process>"""
      } else {
        processes.map { p =>
          val name = p attValueOpt "name" getOrElse ""
          val source = p.stringValue
          val stepsXml = processStringToXmlString(source)
          s"""<process name="${name.escapeXmlForAttribute}">$stepsXml</process>"""
        }.mkString
      }
    xmlStringToMutableNodeInfoRootElem(s"""<processes>$processesXml</processes>""")
  }

  /*@XPathFunction*/
  def serializeProcesses(processesNode: NodeInfo): NodeInfo = {
    val processes = (processesNode / "process").toList
    val serializedProcessesXml = processes.map { p =>
      val name = p attValueOpt "name" getOrElse ""
      val serialized = xmlToProcessString(p)
      s"""<process name="${name.escapeXmlForAttribute}">${serialized.escapeXmlMinimal}</process>"""
    }.mkString
    xmlStringToMutableNodeInfoRootElem(s"""<processes>$serializedProcessesXml</processes>""")
  }
}
