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

import org.orbeon.oxf.fb.{FormBuilder, FormBuilderDocContext, FormBuilderXPathApi}
import org.orbeon.oxf.fb.process.ProcessParams.*
import org.orbeon.oxf.fr.{FormRunner, FormRunnerParams}
import org.orbeon.oxf.fr.process.{ProcessInterpreter, ProcessParser}
import org.orbeon.oxf.fr.process.ProcessParser.*
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsNames}

import scala.util.{Failure, Success, Try}


object ProcessUI {

  val CustomParamSentinel = "__custom__"

  val ProcessNamespaceMapping: Map[String, String] =
    (ProcessInterpreter.StandardNamespaceMapping.mapping - "xml") +
      ("frf" -> "java:org.orbeon.oxf.fr.FormRunner")

  private val ProcessNamespaceDeclsXml: String =
    ProcessNamespaceMapping
      .toList
      .sortBy(_._1)
      .map { case (prefix, uri) => s"""xmlns:$prefix="${uri.escapeXmlForAttribute}"""" }
      .mkString(" ")

  def xmlStringToMutableNodeInfoRootElem(xml: String): NodeInfo = {
    val doc = XFormsCrossPlatformSupport.stringToTinyTree(XPath.GlobalConfiguration, xml, handleXInclude = false, handleLexical = false)
    TransformerUtils.extractAsMutableDocument(doc).rootElement
  }

  /*@XPathFunction*/
  def availableActionsXml(): NodeInfo = {
    val itemsXml = ActionDefinitions.map { a =>
      val paramsXml = a.params.map { p =>
        val choicesXml = p.kind.choices.map { c =>
          s"""<item label="${c.escapeXmlForAttribute}" value="${c.escapeXmlForAttribute}"/>"""
        }.mkString
        s"""<param
           | name="${p.name.escapeXmlForAttribute}"
           | tier="${p.tier.entryName}"
           | value-mode="${p.valueMode.entryName}"
           | kind="${p.kind.entryName}"
           | has-widget="${p.kind.hasConstrainedWidget}"
           | initial="${p.initialValue.escapeXmlForAttribute}"
           | default="${p.initialValue.escapeXmlForAttribute}"
           | allow-blank="${p.allowBlank}"
           | deprecated="${p.deprecated}"
           | placeholder="${p.placeholder.escapeXmlForAttribute}">$choicesXml</param>""".stripMargin.replace('\n', ' ')
      }.mkString
      s"""<action
         | name="${a.name.escapeXmlForAttribute}"
         | label="${a.label.escapeXmlForAttribute}"
         | category="${a.category.entryName}"
         | allows-custom-params="${a.allowsCustomParams}"
         | deprecated="${a.deprecated}">$paramsXml</action>""".stripMargin.replace('\n', ' ')
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
        actionDef.params.filter(_.shownByDefault).map { p =>
          s"""<param name="${p.name.escapeXmlForAttribute}" avt-mode="false" custom="false">${p.initialValue.escapeXmlMinimal}</param>"""
        }.mkString
      case None =>
        ""
    }

  /*@XPathFunction*/
  def singleParamTemplateXml(actionName: String, paramName: String): NodeInfo =
    if (paramName == CustomParamSentinel) {
      xmlStringToMutableNodeInfoRootElem("""<param name="" avt-mode="false" custom="true"/>""")
    } else {
      val initialVal =
        ActionDefsByName
          .get(actionName)
          .flatMap(_.paramsByName.get(paramName))
          .map(_.initialValue)
          .getOrElse("")
      xmlStringToMutableNodeInfoRootElem(
        s"""<param name="${paramName.escapeXmlForAttribute}" avt-mode="false" custom="false">${initialVal.escapeXmlMinimal}</param>"""
      )
    }

  /*@XPathFunction*/
  def defaultValueWhenLeavingAvtMode(actionName: String, paramName: String, currentValue: String): String =
    ActionDefsByName.get(actionName).flatMap(_.paramsByName.get(paramName)) match {
      case Some(paramDef) =>
        if (shouldStartInAvtMode(actionName, paramName, currentValue))
          paramDef.initialValue
        else
          currentValue
      case None =>
        currentValue
    }

  /*@XPathFunction*/
  def newActionTemplateXml(name: String, combinator: String): NodeInfo = {
    val paramsXml = actionParamsXmlString(name)
    xmlStringToMutableNodeInfoRootElem(
      s"""<step
         |    type="action"
         |    combinator="${combinator.trimAllToOpt.getOrElse("then").escapeXmlForAttribute}"
         |    name="${name.escapeXmlForAttribute}"
         |    _add-param="">$paramsXml</step>""".stripMargin
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
         |    <step type="action" combinator="then" name="save" _add-param=""/>
         |</else>""".stripMargin
    )

  /*@XPathFunction*/
  def formContextXml(): NodeInfo = {
    val (controlsXml, sectionsXml, emailTemplatesXml, pdfTemplatesXml, customModesXml) =
      Try {
        implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

        val someResourcesMap =
          Some(FormBuilder.currentResources.child(*).map(r => r.localname -> r).toMap)

        val controls =
          FormBuilderXPathApi.getControlsLabelValueItemset().map { item =>
            val label = item.attValue("label")
            val value = item.attValue("value")
            s"""<item label="${label.escapeXmlForAttribute}" value="${value.escapeXmlForAttribute}"/>"""
          }.mkString

        val sections =
          FormRunner.getAllContainersWithIds
            .filter(FormRunner.IsSection)
            .flatMap { sectionElem =>
              FormRunner.getControlNameOpt(sectionElem).map { sectionName =>
                val sectionLabel =
                  FormBuilder.getControlResourceOrEmpty(
                    sectionName,
                    "label",
                    removeHtmlTags  = true,
                    resourcesMapOpt = someResourcesMap
                  )
                val label = if (sectionLabel.nonEmpty) s"$sectionLabel ($sectionName)" else sectionName
                s"""<item label="${label.escapeXmlForAttribute}" value="${sectionName.escapeXmlForAttribute}"/>"""
              }
            }.mkString

        val emailTemplates =
          (ctx.metadataRootElem / "email" / "templates" / "template")
            .flatMap(_.attValueOpt("name"))
            .flatMap(_.trimAllToOpt)
            .distinct
            .map(n => s"""<item label="${n.escapeXmlForAttribute}" value="${n.escapeXmlForAttribute}"/>""")
            .mkString

        val pdfTemplates =
          (ctx.modelElem / XFormsNames.XFORMS_INSTANCE_QNAME)
            .find(_.id == "fr-form-attachments")
            .toList
            .flatMap(inst => (inst / * / "pdf").toList)
            .flatMap(_.attValueOpt("name"))
            .flatMap(_.trimAllToOpt)
            .distinct
            .map(n => s"""<item label="${n.escapeXmlForAttribute}" value="${n.escapeXmlForAttribute}"/>""")
            .mkString

        val customModes =
          Try(FormRunner.customModes(FormRunnerParams()).map(_.publicName).distinct).getOrElse(Nil)
            .map(m => s"""<item label="${m.escapeXmlForAttribute}" value="${m.escapeXmlForAttribute}"/>""")
            .mkString

        (controls, sections, emailTemplates, pdfTemplates, customModes)
      }.getOrElse(("", "", "", "", ""))

    xmlStringToMutableNodeInfoRootElem(
      s"""<form-context>
         |  <controls>$controlsXml</controls>
         |  <sections>$sectionsXml</sections>
         |  <email-templates>$emailTemplatesXml</email-templates>
         |  <pdf-templates>$pdfTemplatesXml</pdf-templates>
         |  <custom-modes>$customModesXml</custom-modes>
         |</form-context>""".stripMargin
    )
  }

  private def formProcessContextFromXml(
    rootProcessesOpt : Option[NodeInfo],
    formContextOpt   : Option[NodeInfo]
  ): FormProcessContext = {
    val formProcessNames =
      rootProcessesOpt.toList
        .flatMap(r => (r / "process").toList)
        .flatMap(_.attValueOpt("name"))
        .flatMap(_.trimAllToOpt)
        .toSet

    formContextOpt match {
      case Some(fc) if (fc / *).nonEmpty =>
        val controlItems = (fc / "controls" / "item").map(_.attValue("value")).filter(_.nonEmpty).toSet
        val sectionItems = (fc / "sections" / "item").map(_.attValue("value")).filter(_.nonEmpty).toSet
        val emailItems   = (fc / "email-templates" / "item").map(_.attValue("value")).filter(_.nonEmpty).toSet
        val pdfItems     = (fc / "pdf-templates" / "item").map(_.attValue("value")).filter(_.nonEmpty).toSet
        val modeItems    = (fc / "custom-modes" / "item").map(_.attValue("value")).filter(_.nonEmpty).toSet

        // Only enforce control/section set membership when `formContextXml` actually ran in a Form Builder doc context.
        val hasDocCtx =
          controlItems.nonEmpty || sectionItems.nonEmpty || Try(FormBuilderDocContext()).isSuccess

        FormProcessContext(
          controlNames     = if (hasDocCtx) Some(controlItems) else None,
          sectionNames     = if (hasDocCtx) Some(sectionItems) else None,
          emailTemplates   = emailItems,
          pdfTemplates     = pdfItems,
          customModes      = modeItems,
          formProcessNames = formProcessNames
        )

      case _ =>
        FormProcessContext(formProcessNames = formProcessNames)
    }
  }

  /*@XPathFunction*/
  def paramValueMode(paramElem: NodeInfo): String = {
    val actionName = paramElem.parentOption.flatMap(_.attValueOpt("name")).getOrElse("")
    val paramName  = paramElem.attValueOpt("name").getOrElse("")
    ActionDefsByName.get(actionName) match {
      case Some(actionDef) =>
        actionDef.paramsByName.get(paramName) match {
          case Some(paramDef) => paramDef.valueMode.entryName
          case None           => if (actionDef.allowsCustomParams) ValueMode.Avt.entryName else ValueMode.Literal.entryName
        }
      case None =>
        ValueMode.Literal.entryName
    }
  }

  /*@XPathFunction*/
  def isCustomParamNameValid(paramElem: NodeInfo): Boolean = {
    val stepOpt   = paramElem.parentOption
    val actionOpt = stepOpt.flatMap(_.attValueOpt("name")).flatMap(ActionDefsByName.get)
    val paramName = paramElem.attValueOpt("name").flatMap(_.trimAllToOpt).getOrElse("")

    if (! isValidParamIdentifier(paramName)) {
      false
    } else {
      val collidesWithKnown =
        actionOpt.exists(_.paramsByName.contains(paramName))
      val duplicateCount =
        stepOpt.toList
          .flatMap(s => (s child "param").toList)
          .count(p => p.attValueOpt("name").flatMap(_.trimAllToOpt).contains(paramName))
      duplicateCount <= 1 && ! collidesWithKnown
    }
  }

  private def computeParamStaticError(
    paramElem      : NodeInfo,
    formContextOpt : Option[NodeInfo]
  ): Option[ParamValidationError] = {
    val stepOpt          = paramElem.parentOption
    val actionName       = stepOpt.flatMap(_.attValueOpt("name")).getOrElse("")
    val paramName        = paramElem.attValueOpt("name").getOrElse("")
    val rawValue         = paramElem.stringValue
    val siblingNames     =
      stepOpt.toList
        .flatMap(s => (s child "param").toList)
        .filterNot(_ isSameNodeInfo paramElem)
        .flatMap(_.attValueOpt("name"))
        .filter(_.nonEmpty)
        .toSet
    val enclosingProcess = paramElem.ancestor("process").headOption.flatMap(_.attValueOpt("name")).flatMap(_.trimAllToOpt)
    val processesRootOpt = paramElem.ancestor("processes").headOption
    val ctx              = formProcessContextFromXml(processesRootOpt, formContextOpt)

    validateParamStaticValue(
      actionName        = actionName,
      paramName         = paramName,
      rawValue          = rawValue,
      siblingParamNames = siblingNames,
      enclosingProcess  = enclosingProcess,
      ctx               = ctx
    )
  }

  /*@XPathFunction*/
  def isParamValueValid(paramElem: NodeInfo, formContextElem: NodeInfo): Boolean =
    computeParamStaticError(paramElem, Option(formContextElem)).isEmpty

  private val DefaultAlertMessages: Map[String, String] = Map(
    "required"          -> "Value is required",
    "invalid-xpath"     -> "Incorrect XPath expression",
    "invalid-avt"       -> "Incorrect attribute value template",
    "invalid-boolean"   -> "Must be \"true\", \"false\", or an AVT expression",
    "invalid-choice"    -> "Invalid parameter value",
    "invalid-token"     -> "Contains an unsupported value",
    "invalid-duration"  -> "Must be a valid duration with unit (e.g. 1000ms or 5s)",
    "invalid-at"        -> "Must be \"start\", \"end\", \"all\", or a positive integer",
    "invalid-mode"      -> "Unsupported target mode",
    "invalid-ncname"    -> "Must be a valid XML identifier",
    "unknown-control"   -> "Unknown control name",
    "unknown-section"   -> "Unknown section name",
    "unknown-process"   -> "Unknown sub-process name",
    "self-process"      -> "A process cannot call itself",
    "unknown-param"     -> "Unsupported parameter for this action",
    "invalid-param-name"-> "A unique, valid parameter name is required"
  )

  /*@XPathFunction*/
  def paramAlertMessage(paramElem: NodeInfo, resourcesElem: NodeInfo, formContextElem: NodeInfo): String = {
    val key =
      computeParamStaticError(paramElem, Option(formContextElem))
        .map(_.alertKey)
        .getOrElse {
          paramValueMode(paramElem) match {
            case "xpath" => "invalid-xpath"
            case "avt"   => "invalid-avt"
            case _       => "invalid-choice"
          }
        }

    Option(resourcesElem)
      .flatMap(res => (res / "dialog-process-editor" / "alerts" / key).headOption)
      .flatMap(_.stringValue.trimAllToOpt)
      .getOrElse(DefaultAlertMessages.getOrElse(key, "Invalid value"))
  }

  private def computeDialogStepErrors(
    stepElem       : NodeInfo,
    formContextOpt : Option[NodeInfo]
  ): List[StepValidationError] = {
    val actionName       = stepElem.attValueOpt("name").getOrElse("")
    val paramElems       = (stepElem child "param").toList
    val presentNames     = paramElems.flatMap(_.attValueOpt("name")).filter(_.nonEmpty).toSet
    val params           = paramElems.map { p =>
      val unnamed = p.attValueOpt("unnamed").contains("true")
      val pName   = p.attValueOpt("name").getOrElse("")
      val keyOpt  = if (unnamed && pName.isEmpty) None else Some(pName)
      (keyOpt, p.stringValue)
    }
    val enclosingProcess = stepElem.ancestor("process").headOption.flatMap(_.attValueOpt("name")).flatMap(_.trimAllToOpt)
    val processesRootOpt = stepElem.ancestor("processes").headOption
    val ctx              = formProcessContextFromXml(processesRootOpt, formContextOpt)

    // Filter out errors already reported directly on child `<param>` controls so `<fr:error-summary>` never duplicates them
    validateActionStep(actionName, params, enclosingProcess, ctx).filterNot {
      case StepValidationError.MissingRequiredParam(_, pName) if presentNames.contains(pName)          => true
      case StepValidationError.MissingRequiredOneOf(_, group) if group.exists(presentNames.contains)   => true
      case StepValidationError.UnknownParam(_, _)             if paramElems.nonEmpty                   => true
      case _                                                                                           => false
    }
  }

  /*@XPathFunction*/
  def isActionStepValid(stepElem: NodeInfo, formContextElem: NodeInfo): Boolean =
    computeDialogStepErrors(stepElem, Option(formContextElem)).isEmpty

  /*@XPathFunction*/
  def actionStepAlertMessage(stepElem: NodeInfo, resourcesElem: NodeInfo, formContextElem: NodeInfo): String =
    computeDialogStepErrors(stepElem, Option(formContextElem)).headOption match {
      case Some(err) => err.details
      case None      =>
        Option(resourcesElem)
          .flatMap(res => (res / "dialog-process-editor" / "process-definition" / "alert").headOption)
          .flatMap(_.stringValue.trimAllToOpt)
          .getOrElse("Invalid action configuration")
    }

  // AST ExprNode to XML String
  def exprNodeToXmlString(expr: ExprNode, combinator: String): String = expr match {
    case ActionNode(name, params) =>
      val actionDefOpt = ActionDefsByName.get(name)
      val canonicalIndex: Map[String, Int] =
        actionDefOpt.map(_.params.map(_.name).zipWithIndex.toMap).getOrElse(Map.empty)

      val resolvedEntries =
        params.toList.zipWithIndex.map {
          case ((Some(pName), pVal), idx) =>
            val isCustom = actionDefOpt.exists(ad => ad.allowsCustomParams && ! ad.paramsByName.contains(pName))
            val avtMode  = shouldStartInAvtMode(name, pName, pVal)
            val sortKey  = canonicalIndex.getOrElse(pName, 1000 + idx)
            (sortKey, pName, false, isCustom, avtMode, pVal)

          case ((None, pVal), idx) =>
            val defaultName = resolveUnnamedParamName(name, pVal)
            val avtMode     = shouldStartInAvtMode(name, defaultName, pVal)
            val sortKey     = canonicalIndex.getOrElse(defaultName, idx)
            (sortKey, defaultName, true, false, avtMode, pVal)
        }.sortBy(_._1)

      val paramsXml = resolvedEntries.map {
        case (_, pName, unnamed, isCustom, avtMode, pVal) =>
          val unnamedAttr = if (unnamed) """ unnamed="true"""" else ""
          s"""<param name="${pName.escapeXmlForAttribute}" avt-mode="$avtMode" custom="$isCustom"$unnamedAttr>${pVal.escapeXmlMinimal}</param>"""
      }.mkString

      s"""<step type="action" combinator="${combinator.escapeXmlForAttribute}" name="${name.escapeXmlForAttribute}" _add-param="">$paramsXml</step>"""

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
      """<step type="action" combinator="then" name="save" _add-param=""/>"""
    } else {
      Try(ProcessParser.parse(processString)) match {
        case Success(rootGroup) =>
          val steps = ("then", rootGroup.expr) :: rootGroup.rest.map { case (c, e) => (c.name, e) }
          steps.map { case (comb, expr) => exprNodeToXmlString(expr, comb) }.mkString
        case Failure(_) =>
          // Fallback if parsing fails: create a single save action step
          """<step type="action" combinator="then" name="save" _add-param=""/>"""
      }
    }
  }

  /*@XPathFunction*/
  def processStringToXml(processString: String): NodeInfo =
    xmlStringToMutableNodeInfoRootElem(s"""<process $ProcessNamespaceDeclsXml>${processStringToXmlString(processString)}</process>""")

  private val IndentSpaces = "    "

  private def serializeAction(step: NodeInfo): String = {
    val name   = step attValueOpt "name" getOrElse ""
    val params = (step child "param").toList
    if (params.isEmpty) {
      name
    } else {
      val multiParam = params.size > 1
      val paramsStr = params.map { p =>
        val pName          = p attValueOpt "name" getOrElse ""
        val unnamed        = (p attValueOpt "unnamed").contains("true")
        val pVal           = p.stringValue
        val canKeepUnnamed =
          unnamed &&
            ! multiParam &&
            ! containsAvtBraces(pVal) &&
            resolveUnnamedParamName(name, pVal) == pName
        if (pName.nonEmpty && ! canKeepUnnamed)
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
    val processes = Option(configNode).toList.flatMap(c => (c / "process").toList)
    val processesXml =
      if (processes.isEmpty) {
        """<process name=""><step type="action" combinator="then" name="save" _add-param=""/></process>"""
      } else {
        processes.map { p =>
          val name     = p attValueOpt "name" getOrElse ""
          val source   = p.stringValue
          val stepsXml = processStringToXmlString(source)
          s"""<process name="${name.escapeXmlForAttribute}">$stepsXml</process>"""
        }.mkString
      }
    xmlStringToMutableNodeInfoRootElem(s"""<processes $ProcessNamespaceDeclsXml>$processesXml</processes>""")
  }

  /*@XPathFunction*/
  def serializeProcesses(processesNode: NodeInfo): NodeInfo = {
    val processes = (processesNode / "process").toList
    val serializedProcessesXml = processes.map { p =>
      val name       = p attValueOpt "name" getOrElse ""
      val serialized = xmlToProcessString(p)
      s"""<process name="${name.escapeXmlForAttribute}">${serialized.escapeXmlMinimal}</process>"""
    }.mkString
    xmlStringToMutableNodeInfoRootElem(s"""<processes>$serializedProcessesXml</processes>""")
  }
}
