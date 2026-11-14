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

import enumeratum.EnumEntry.{Hyphencase, Lowercase}
import enumeratum.*
import org.orbeon.oxf.fr.definitions.FormRunnerDetailMode
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.submission.SubmissionUtils
import org.orbeon.oxf.xml.SaxonUtils

import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.util.Try


object ProcessParams {

  sealed trait ParamTier extends EnumEntry with Lowercase
  object ParamTier extends Enum[ParamTier] {
    val values: immutable.IndexedSeq[ParamTier] = findValues

    case object Required extends ParamTier
    case object Common   extends ParamTier
    case object Advanced extends ParamTier
  }

  sealed trait ValueMode extends EnumEntry with Lowercase
  object ValueMode extends Enum[ValueMode] {
    val values: immutable.IndexedSeq[ValueMode] = findValues

    case object Literal extends ValueMode
    case object Avt     extends ValueMode
    case object XPath   extends ValueMode
  }

  sealed trait ActionCategory extends EnumEntry with Hyphencase
  object ActionCategory extends Enum[ActionCategory] {
    val values: immutable.IndexedSeq[ActionCategory] = findValues

    case object FormRunner  extends ActionCategory
    case object SubProcess  extends ActionCategory
    case object FlowControl extends ActionCategory
    case object XForms      extends ActionCategory {
      override val entryName: String = "xforms"
    }
  }

  sealed trait UnnamedDefaultRule
  object UnnamedDefaultRule {
    case object NoneAllowed                   extends UnnamedDefaultRule
    case class  FixedParam(paramName: String) extends UnnamedDefaultRule
    case object NavigateHeuristic             extends UnnamedDefaultRule
  }

  sealed trait ParamControlKind {
    def entryName            : String
    def choices              : List[String] = Nil
    def hasConstrainedWidget : Boolean      = false
    def caseInsensitive      : Boolean      = false
  }

  object ParamControlKind {
    case object Text extends ParamControlKind {
      val entryName = "text"
    }
    case object BooleanSelect extends ParamControlKind {
      val entryName                   = "boolean"
      override val choices            = List("true", "false")
      override val hasConstrainedWidget = true
    }
    case class EnumSelect(override val choices: List[String], override val caseInsensitive: Boolean = false) extends ParamControlKind {
      val entryName                   = "enum"
      override val hasConstrainedWidget = true
    }
    case class TokenMultiSelect(override val choices: List[String]) extends ParamControlKind {
      val entryName                   = "tokens"
      override val hasConstrainedWidget = true
    }
    case object DurationInput extends ParamControlKind {
      val entryName = "duration"
    }
    case object RepeatAtInput extends ParamControlKind {
      val entryName = "repeat-at"
    }
    case object DetailModeSelect extends ParamControlKind {
      val entryName                   = "detail-mode"
      override val choices            = List("edit", "view", "new")
      override val hasConstrainedWidget = true
    }
    case object NCNameInput extends ParamControlKind {
      val entryName = "ncname"
    }
    case object ControlNameSelect extends ParamControlKind {
      val entryName                   = "control-name"
      override val hasConstrainedWidget = true
    }
    case object SectionNameSelect extends ParamControlKind {
      val entryName                   = "section-name"
      override val hasConstrainedWidget = true
    }
    case object EmailTemplateSelect extends ParamControlKind {
      val entryName                   = "email-template"
      override val hasConstrainedWidget = true
    }
    case object PdfTemplateSelect extends ParamControlKind {
      val entryName                   = "pdf-template"
      override val hasConstrainedWidget = true
    }
    case object ProcessNameSelect extends ParamControlKind {
      val entryName                   = "process-name"
      override val hasConstrainedWidget = true
    }
  }

  case class ActionParamDef(
    name        : String,
    tier        : ParamTier        = ParamTier.Advanced,
    valueMode   : ValueMode        = ValueMode.Avt,
    kind        : ParamControlKind = ParamControlKind.Text,
    initialValue: String           = "",
    allowBlank  : Boolean          = false,
    deprecated  : Boolean          = false,
    placeholder : String           = ""
  ) {
    def shownByDefault: Boolean = tier == ParamTier.Required || tier == ParamTier.Common
  }

  case class ActionDef(
    name                    : String,
    label                   : String,
    category                : ActionCategory     = ActionCategory.FormRunner,
    params                  : List[ActionParamDef] = Nil,
    unnamedRule             : UnnamedDefaultRule = UnnamedDefaultRule.NoneAllowed,
    requiredOneOfGroups     : List[List[String]] = Nil,
    mutuallyExclusiveGroups : List[List[String]] = Nil,
    allowsCustomParams      : Boolean            = false,
    deprecated              : Boolean            = false
  ) {
    val paramsByName: Map[String, ActionParamDef] =
      params.map(p => p.name -> p).toMap
  }

  private val DataFormatVersionChoices: List[String] =
    List("edge", "2019.1.0", "4.8.0", "4.0.0")

  private val SendContentTokens: List[String] =
    List(
      "xml",
      "json",
      "metadata",
      "attachments",
      "pdf",
      "pdf-url",
      "tiff",
      "tiff-url",
      "excel-with-named-ranges",
      "excel-with-named-ranges-url",
      "xml-form-structure-and-data",
      "xml-form-structure-and-data-url"
    )

  private val SendUriParametersTokens: List[String] =
    List(
      "app",
      "form",
      "form-version",
      "document",
      "valid",
      "language",
      "process",
      "data-format-version",
      "workflow-stage"
    )

  private val SendAnnotateTokens: List[String] =
    List("error", "warning", "info")

  private val HttpMethodChoices: List[String] =
    List("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH")

  private val RenderedPdfParams: List[ActionParamDef] = List(
    ActionParamDef("use-pdf-template",  ParamTier.Advanced, ValueMode.Avt, ParamControlKind.BooleanSelect,     initialValue = "true"),
    ActionParamDef("pdf-template-name", ParamTier.Advanced, ValueMode.Avt, ParamControlKind.PdfTemplateSelect, initialValue = ""),
    ActionParamDef("pdf-template-lang", ParamTier.Advanced, ValueMode.Avt, ParamControlKind.Text,              initialValue = "", placeholder = "en"),
    ActionParamDef("lang",              ParamTier.Advanced, ValueMode.Avt, ParamControlKind.Text,              initialValue = "", placeholder = "en"),
    ActionParamDef("show-hints",        ParamTier.Advanced, ValueMode.Avt, ParamControlKind.BooleanSelect,     initialValue = "true"),
    ActionParamDef("show-alerts",       ParamTier.Advanced, ValueMode.Avt, ParamControlKind.BooleanSelect,     initialValue = "true"),
    ActionParamDef("show-required",     ParamTier.Advanced, ValueMode.Avt, ParamControlKind.BooleanSelect,     initialValue = "true")
  )

  import ActionCategory.*
  import ParamControlKind.*
  import ParamTier.*
  import UnnamedDefaultRule.*
  import ValueMode.*

  val ActionDefinitions: List[ActionDef] = List(
    // --- Form Runner actions ---
    ActionDef(
      name   = "save",
      label  = "Save",
      params = List(
        ActionParamDef("draft",          Advanced, Avt, BooleanSelect, initialValue = "true"),
        ActionParamDef("overwrite",      Advanced, Avt, BooleanSelect, initialValue = "true"),
        ActionParamDef("prune-metadata", Advanced, Avt, BooleanSelect, initialValue = "true"),
        ActionParamDef("query",          Advanced, Avt, Text,          initialValue = "", placeholder = "param=value")
      )
    ),
    ActionDef(
      name        = "validate",
      label       = "Validate",
      unnamedRule = FixedParam("level"),
      params      = List(
        ActionParamDef("level",   Common,   Avt, EnumSelect(List("error", "warning", "info")), initialValue = "error"),
        ActionParamDef("control", Advanced, Avt, Text, initialValue = "fr-view-component")
      )
    ),
    ActionDef(
      name                = "success-message",
      label               = "Success Message",
      unnamedRule         = FixedParam("resource"),
      requiredOneOfGroups = List(List("message", "resource")),
      params              = List(
        ActionParamDef("message",  Common,   Avt, Text,          initialValue = "Data saved successfully"),
        ActionParamDef("resource", Advanced, Avt, Text,          initialValue = "save-success", placeholder = "save-success"),
        ActionParamDef("html",     Advanced, Avt, BooleanSelect, initialValue = "true")
      )
    ),
    ActionDef(
      name                = "error-message",
      label               = "Error Message",
      unnamedRule         = FixedParam("resource"),
      requiredOneOfGroups = List(List("message", "resource")),
      params              = List(
        ActionParamDef("message",    Common,   Avt, Text,          initialValue = "An error occurred"),
        ActionParamDef("resource",   Advanced, Avt, Text,          initialValue = "database-error", placeholder = "database-error"),
        ActionParamDef("appearance", Advanced, Avt, EnumSelect(List("dialog", "ephemeral")), initialValue = "dialog"),
        ActionParamDef("html",       Advanced, Avt, BooleanSelect, initialValue = "true")
      )
    ),
    ActionDef(
      name        = "confirm",
      label       = "Confirmation Dialog",
      unnamedRule = FixedParam("resource"),
      params      = List(
        ActionParamDef("message",  Common,   Avt, Text,          initialValue = "Are you sure?"),
        ActionParamDef("resource", Advanced, Avt, Text,          initialValue = "confirmation-dialog-message"),
        ActionParamDef("html",     Advanced, Avt, BooleanSelect, initialValue = "true")
      )
    ),
    ActionDef(
      name        = "navigate",
      label       = "Navigate",
      unnamedRule = NavigateHeuristic,
      params      = List(
        ActionParamDef("uri",           Common,   Avt,     Text,          initialValue = "/", placeholder = "https://... or /..."),
        ActionParamDef("property",      Advanced, Avt,     Text,          initialValue = "oxf.fr.detail.close.uri"),
        ActionParamDef("target",        Advanced, Literal, Text,          initialValue = "_blank"),
        ActionParamDef("show-progress", Advanced, Avt,     BooleanSelect, initialValue = "true")
      )
    ),
    ActionDef(
      name                = "send",
      label               = "Send HTTP",
      unnamedRule         = FixedParam("property"),
      requiredOneOfGroups = List(List("uri", "property")),
      params              = List(
        ActionParamDef("uri",                        Common,   Avt, Text,                                    initialValue = "", placeholder = "https://..."),
        ActionParamDef("method",                     Common,   Avt, EnumSelect(HttpMethodChoices, caseInsensitive = true), initialValue = "POST"),
        ActionParamDef("property",                   Advanced, Avt, Text,                                    initialValue = "", placeholder = "oxf.fr.detail.send.success"),
        ActionParamDef("content",                    Advanced, Avt, TokenMultiSelect(SendContentTokens),     initialValue = "xml", allowBlank = true),
        ActionParamDef("replace",                    Advanced, Avt, EnumSelect(List("none", "all", "instance", "text")), initialValue = "none"),
        ActionParamDef("parameters",                 Advanced, Avt, TokenMultiSelect(SendUriParametersTokens), initialValue = "app form form-version document valid language process data-format-version", allowBlank = true),
        ActionParamDef("headers",                    Advanced, Avt, Text,                                    initialValue = "", placeholder = "X-Header: value"),
        ActionParamDef("serialization",              Advanced, Avt, Text,                                    initialValue = "application/xml"),
        ActionParamDef("content-type",               Advanced, Avt, Text,                                    initialValue = "application/xml"),
        ActionParamDef("data-format-version",        Advanced, Avt, EnumSelect(DataFormatVersionChoices),    initialValue = "4.0.0"),
        ActionParamDef("prune-metadata",             Advanced, Avt, BooleanSelect,                           initialValue = "true"),
        ActionParamDef("prune",                      Advanced, Avt, BooleanSelect,                           initialValue = "true", deprecated = true),
        ActionParamDef("nonrelevant",                Advanced, Avt, EnumSelect(List("remove", "keep", "empty"), caseInsensitive = true), initialValue = "remove"),
        ActionParamDef("annotate",                   Advanced, Avt, TokenMultiSelect(SendAnnotateTokens),    initialValue = "error warning info", allowBlank = true),
        ActionParamDef("keep-non-persisted-values",  Advanced, Avt, BooleanSelect,                           initialValue = "true"),
        ActionParamDef("show-progress",              Advanced, Avt, BooleanSelect,                           initialValue = "false"),
        ActionParamDef("formtarget",                 Advanced, Avt, Text,                                    initialValue = "_blank")
      ) ++ RenderedPdfParams
    ),
    ActionDef(
      name   = "send-s3",
      label  = "Send to S3",
      params = List(
        ActionParamDef("s3-path",             Common,   XPath, Text,                                 initialValue = "", placeholder = "XPath expression"),
        ActionParamDef("s3-config",           Advanced, Avt,   Text,                                 initialValue = "default"),
        ActionParamDef("content",             Advanced, Avt,   EnumSelect(List("xml")),              initialValue = "xml"),
        ActionParamDef("data-format-version", Advanced, Avt,   EnumSelect(DataFormatVersionChoices), initialValue = "4.0.0"),
        ActionParamDef("prune-metadata",      Advanced, Avt,   BooleanSelect,                        initialValue = "true")
      )
    ),
    ActionDef(
      name   = "email",
      label  = "Send Email",
      params = List(
        ActionParamDef("template",            Advanced, Avt,   EmailTemplateSelect,                    initialValue = ""),
        ActionParamDef("match",               Advanced, Avt,   EnumSelect(List("first", "all")),       initialValue = "first"),
        ActionParamDef("lang",                Advanced, Avt,   Text,                                   initialValue = "", placeholder = "en"),
        ActionParamDef("use-pdf-template",    Advanced, Avt,   BooleanSelect,                          initialValue = "true"),
        ActionParamDef("pdf-template-name",   Advanced, Avt,   PdfTemplateSelect,                      initialValue = ""),
        ActionParamDef("pdf-template-names",  Advanced, Avt,   Text,                                   initialValue = ""),
        ActionParamDef("pdf-template-lang",   Advanced, Avt,   Text,                                   initialValue = "", placeholder = "en"),
        ActionParamDef("show-hints",          Advanced, Avt,   BooleanSelect,                          initialValue = "true"),
        ActionParamDef("show-alerts",         Advanced, Avt,   BooleanSelect,                          initialValue = "true"),
        ActionParamDef("show-required",       Advanced, Avt,   BooleanSelect,                          initialValue = "true"),
        ActionParamDef("data-format-version", Advanced, Avt,   EnumSelect(DataFormatVersionChoices),   initialValue = "4.0.0"),
        ActionParamDef("s3-store",            Advanced, Avt,   BooleanSelect,                          initialValue = "true"),
        ActionParamDef("s3-config",           Advanced, Avt,   Text,                                   initialValue = "default"),
        ActionParamDef("s3-path",             Advanced, XPath, Text,                                   initialValue = "", placeholder = "XPath expression"),
        ActionParamDef("send-email",          Advanced, Avt,   BooleanSelect,                          initialValue = "false", deprecated = true)
      )
    ),
    ActionDef(
      name   = "open-rendered-format",
      label  = "Open Rendered Format",
      params =
        ActionParamDef(
          "format",
          Common,
          Avt,
          EnumSelect(List("pdf", "tiff", "excel-with-named-ranges", "xml-form-structure-and-data")),
          initialValue = "pdf"
        ) :: RenderedPdfParams
    ),
    ActionDef(
      name                    = "control-setvalue",
      label                   = "Set Control Value",
      mutuallyExclusiveGroups = List(List("at", "section-name")),
      params                  = List(
        ActionParamDef("control-name", Required, Avt,   ControlNameSelect, initialValue = ""),
        ActionParamDef("value",        Required, XPath, Text,              initialValue = "''", placeholder = "XPath expression (e.g. 'value' or $other + 1)"),
        ActionParamDef("at",           Advanced, Avt,   RepeatAtInput,     initialValue = "start", placeholder = "start, end, all, or 1-based index"),
        ActionParamDef("section-name", Advanced, Avt,   SectionNameSelect, initialValue = "")
      )
    ),
    ActionDef(
      name        = "set-workflow-stage",
      label       = "Set Workflow Stage",
      unnamedRule = FixedParam("name"),
      params      = List(
        ActionParamDef("name", Common, Avt, Text, initialValue = "", placeholder = "Stage name")
      )
    ),
    ActionDef(
      name   = "set-data-status",
      label  = "Set Data Status",
      params = List(
        ActionParamDef("status", Common,   Avt, EnumSelect(List("safe", "dirty")), initialValue = "safe"),
        ActionParamDef("draft",  Advanced, Avt, BooleanSelect,                     initialValue = "true")
      )
    ),
    ActionDef(
      name   = "change-mode",
      label  = "Change Mode",
      params = List(
        ActionParamDef("mode",                      Required, Avt, DetailModeSelect, initialValue = "edit"),
        ActionParamDef("preserve-rendered-formats", Advanced, Avt, BooleanSelect,    initialValue = "true")
      )
    ),
    ActionDef(
      name        = "log",
      label       = "Log Message",
      unnamedRule = FixedParam("message"),
      params      = List(
        ActionParamDef("message", Common,   Avt, Text, initialValue = "", allowBlank = true, placeholder = "Message or AVT"),
        ActionParamDef("level",   Advanced, Avt, EnumSelect(List("info", "debug", "warn", "error")), initialValue = "info")
      )
    ),
    ActionDef(
      name   = "sleep",
      label  = "Sleep",
      params = List(
        ActionParamDef("duration", Required, Avt, DurationInput, initialValue = "1000ms", placeholder = "e.g. 1000ms or 5s")
      )
    ),
    ActionDef(
      name   = "callback",
      label  = "Run Callback",
      params = List(
        ActionParamDef("name", Required, Literal, Text, initialValue = "", placeholder = "Callback name")
      )
    ),
    ActionDef("result-dialog",          "Result Dialog"),
    ActionDef("pending-uploads",        "Check Pending Uploads"),
    ActionDef("relinquish-lease",       "Relinquish Lease"),
    ActionDef("visit-all",              "Show All Errors"),
    ActionDef("show-relevant-errors",   "Show Relevant Errors"),
    ActionDef("unvisit-all",            "Clear Error Highlights"),
    ActionDef("expand-all",             "Expand All Sections"),
    ActionDef("expand-invalid",         "Expand Invalid Sections"),
    ActionDef("collapse-all",           "Collapse All Sections"),
    ActionDef("wizard-update-validity", "Wizard Update Validity"),
    ActionDef("wizard-prev",            "Wizard Previous"),
    ActionDef("wizard-next",            "Wizard Next"),
    ActionDef("captcha",                "Verify CAPTCHA"),
    ActionDef("captcha-reset",          "Reset CAPTCHA"),
    ActionDef("new-to-edit",            "Switch New to Edit"),
    ActionDef("edit-to-new",            "Switch Edit to New"),
    ActionDef("review",                 "Go to Review"),
    ActionDef("edit",                   "Go to Edit"),

    // --- Sub-process actions ---
    ActionDef(
      name        = "process",
      label       = "Call Process",
      category    = SubProcess,
      unnamedRule = FixedParam("name"),
      params      = List(
        ActionParamDef("name", Required, Avt, ProcessNameSelect, initialValue = "")
      )
    ),
    ActionDef("require-uploads",  "Sub-process: Require Uploads",  category = SubProcess),
    ActionDef("require-valid",    "Sub-process: Require Valid",    category = SubProcess),
    ActionDef("validate-all",     "Sub-process: Validate All",     category = SubProcess),
    ActionDef("review-messages",  "Sub-process: Review Messages",  category = SubProcess),
    ActionDef("save-final",       "Sub-process: Save Final",       category = SubProcess),
    ActionDef("save-progress",    "Sub-process: Save Progress",    category = SubProcess),
    ActionDef("save-overwrite",   "Sub-process: Save Overwrite",   category = SubProcess),
    ActionDef("save-import",      "Sub-process: Save Import",      category = SubProcess),
    ActionDef("autosave",         "Sub-process: Autosave",         category = SubProcess),
    ActionDef("clear",            "Sub-process: Clear Data",       category = SubProcess),
    ActionDef("reset-form-data",  "Sub-process: Reset Form Data",  category = SubProcess),
    ActionDef("close",            "Sub-process: Close",            category = SubProcess),
    ActionDef("summary",          "Sub-process: Summary Page",     category = SubProcess),
    ActionDef("home",             "Sub-process: Home",             category = SubProcess),
    ActionDef("form-runner-home", "Sub-process: Form Runner Home", category = SubProcess),
    ActionDef("orbeon-home",      "Sub-process: Orbeon Home",      category = SubProcess),
    ActionDef("submit",           "Sub-process: Submit",           category = SubProcess),
    ActionDef("workflow-send",    "Sub-process: Workflow Send",    category = SubProcess),
    ActionDef("pdf",              "Sub-process: Open PDF",         category = SubProcess),
    ActionDef("tiff",             "Sub-process: Open TIFF",        category = SubProcess),
    ActionDef("excel-export",     "Sub-process: Export Excel",     category = SubProcess),
    ActionDef("xml-export",       "Sub-process: Export XML",       category = SubProcess),
    ActionDef("reupload",         "Sub-process: Re-upload",        category = SubProcess),
    ActionDef("wizard-toc",       "Sub-process: Wizard TOC",       category = SubProcess),

    // Additional internal/deprecated built-in sub-processes (preserved when encountered)
    ActionDef("save-draft",                 "Sub-process: Save Draft",                 category = SubProcess, deprecated = true),
    ActionDef("lease-view",                 "Sub-process: Lease View",                 category = SubProcess, deprecated = true),
    ActionDef("workflow-review",            "Sub-process: Workflow Review",            category = SubProcess, deprecated = true),
    ActionDef("workflow-edit",              "Sub-process: Workflow Edit",              category = SubProcess, deprecated = true),
    ActionDef("validation-dialog-review",   "Sub-process: Validation Dialog Review",   category = SubProcess, deprecated = true),
    ActionDef("validation-dialog-continue", "Sub-process: Validation Dialog Continue", category = SubProcess, deprecated = true),
    ActionDef("action-service-error",       "Sub-process: Service Error",              category = SubProcess, deprecated = true),
    ActionDef("action-action-error",        "Sub-process: Action Error",               category = SubProcess, deprecated = true),
    ActionDef("before-publish",             "Sub-process: Before Publish",             category = SubProcess, deprecated = true),

    // --- Process flow control actions ---
    ActionDef("continue-with-success", "Continue with Success", category = FlowControl),
    ActionDef("continue-with-failure", "Continue with Failure", category = FlowControl),
    ActionDef(
      name     = "terminate-with-success",
      label    = "Terminate with Success",
      category = FlowControl,
      params   = List(
        ActionParamDef("message", Advanced, Avt, Text, initialValue = "", placeholder = "Optional debug message")
      )
    ),
    ActionDef(
      name     = "terminate-with-failure",
      label    = "Terminate with Failure",
      category = FlowControl,
      params   = List(
        ActionParamDef("message", Advanced, Avt, Text, initialValue = "", placeholder = "Optional debug message")
      )
    ),
    ActionDef("suspend",             "Suspend Process",     category = FlowControl),
    ActionDef("resume-with-success", "Resume with Success", category = FlowControl),
    ActionDef("resume-with-failure", "Resume with Failure", category = FlowControl),
    ActionDef("abort",               "Abort Process",       category = FlowControl),
    ActionDef(
      name        = "rollback",
      label       = "Rollback Data",
      category    = FlowControl,
      unnamedRule = FixedParam("changes"),
      params      = List(
        ActionParamDef("changes", Required, Avt, EnumSelect(List("in-memory-form-data")), initialValue = "in-memory-form-data")
      )
    ),

    // Deprecated flow-control aliases
    ActionDef("nop",    "Continue with Success (nop)",    category = FlowControl, deprecated = true),
    ActionDef("resume", "Resume with Success (resume)",   category = FlowControl, deprecated = true),
    ActionDef(
      name       = "success",
      label      = "Terminate with Success (success)",
      category   = FlowControl,
      deprecated = true,
      params     = List(ActionParamDef("message", Advanced, Avt, Text))
    ),
    ActionDef(
      name       = "failure",
      label      = "Terminate with Failure (failure)",
      category   = FlowControl,
      deprecated = true,
      params     = List(ActionParamDef("message", Advanced, Avt, Text))
    ),

    // --- XForms actions ---
    ActionDef(
      name     = "xf:setvalue",
      label    = "XForms: Set Value",
      category = XForms,
      params   = List(
        ActionParamDef("ref",   Required, XPath,   Text,          initialValue = "",   placeholder = "Target node XPath"),
        ActionParamDef("value", Common,   XPath,   Text,          initialValue = "''", placeholder = "Value XPath"),
        ActionParamDef("all",   Advanced, Literal, BooleanSelect, initialValue = "true")
      )
    ),
    ActionDef(
      name     = "xf:insert",
      label    = "XForms: Insert",
      category = XForms,
      params   = List(
        ActionParamDef("origin", Required, XPath, Text, initialValue = "", placeholder = "Origin nodes XPath"),
        ActionParamDef("into",   Advanced, XPath, Text, initialValue = "", placeholder = "Context nodes XPath"),
        ActionParamDef("before", Advanced, XPath, Text, initialValue = "", placeholder = "Before nodes XPath"),
        ActionParamDef("after",  Advanced, XPath, Text, initialValue = "", placeholder = "After nodes XPath")
      )
    ),
    ActionDef(
      name     = "xf:delete",
      label    = "XForms: Delete",
      category = XForms,
      params   = List(
        ActionParamDef("ref", Required, XPath, Text, initialValue = "", placeholder = "Nodes to delete XPath")
      )
    ),
    ActionDef(
      name        = "xf:send",
      label       = "XForms: Send Submission",
      category    = XForms,
      unnamedRule = FixedParam("submission"),
      params      = List(
        ActionParamDef("submission", Required, Literal, NCNameInput, initialValue = "", placeholder = "Submission ID")
      )
    ),
    ActionDef(
      name               = "xf:dispatch",
      label              = "XForms: Dispatch Event",
      category           = XForms,
      unnamedRule        = FixedParam("name"),
      allowsCustomParams = true,
      params             = List(
        ActionParamDef("name",     Required, Literal, Text,        initialValue = "",              placeholder = "Event name"),
        ActionParamDef("targetid", Advanced, Literal, NCNameInput, initialValue = "fr-form-model", placeholder = "Target ID")
      )
    ),
    ActionDef(
      name               = "xf:show",
      label              = "XForms: Show Dialog",
      category           = XForms,
      unnamedRule        = FixedParam("dialog"),
      allowsCustomParams = true,
      params             = List(
        ActionParamDef("dialog", Required, Literal, NCNameInput, initialValue = "", placeholder = "Dialog ID")
      )
    ),
    ActionDef(
      name               = "xf:hide",
      label              = "XForms: Hide Dialog",
      category           = XForms,
      unnamedRule        = FixedParam("dialog"),
      allowsCustomParams = true,
      params             = List(
        ActionParamDef("dialog", Required, Literal, NCNameInput, initialValue = "", placeholder = "Dialog ID")
      )
    ),
    ActionDef(
      name       = "xf:callback",
      label      = "XForms: Run Callback (xf:callback)",
      category   = XForms,
      deprecated = true,
      params     = List(
        ActionParamDef("name", Required, Literal, Text, initialValue = "", placeholder = "Callback name")
      )
    )
  )

  val ActionDefsByName: Map[String, ActionDef] =
    ActionDefinitions.map(a => a.name -> a).toMap

  val BuiltinSubProcessNames: Set[String] =
    ActionDefinitions.filter(_.category == SubProcess).map(_.name).toSet - "process"

  private val DisallowedChangeModes: Set[String] =
    Set("pdf") ++
      FormRunnerDetailMode.SecondaryModes.map(_.publicName) ++
      FormRunnerDetailMode.SupportedNonDetailModes

  private val CustomModeNameRegex = """^[A-Za-z_][A-Za-z0-9\-_:]*$""".r
  private val ParamNameRegex      = """^[A-Za-z_\-][A-Za-z0-9_\-:]*$""".r

  def resolveUnnamedParamName(actionName: String, paramValue: String): String =
    ActionDefsByName.get(actionName).map(_.unnamedRule) match {
      case Some(FixedParam(paramName)) =>
        paramName
      case Some(NavigateHeuristic) =>
        if (paramValue.startsWith("/") || PathUtils.urlHasProtocol(paramValue)) "uri" else "property"
      case _ =>
        ""
    }

  def containsAvtBraces(value: String): Boolean =
    value.indexOf('{') != -1 || value.indexOf('}') != -1

  def isValidParamIdentifier(name: String): Boolean =
    name.trimAllToOpt.exists(ParamNameRegex.matches)

  def isPropertyDefinedSubProcess(name: String): Boolean =
    name.trimAllToOpt.exists { trimmed =>
      Try(
        CoreCrossPlatformSupport.properties
          .propertiesStartsWith(s"oxf.fr.detail.process.$trimmed", matchWildcards = false)
          .nonEmpty
      ).getOrElse(false)
    }

  def isKnownSubProcessName(name: String, formProcessNames: Set[String]): Boolean =
    BuiltinSubProcessNames(name) || formProcessNames(name) || isPropertyDefinedSubProcess(name)

  // Context of available form artifacts for static reference checking
  case class FormProcessContext(
    controlNames     : Option[Set[String]] = None,
    sectionNames     : Option[Set[String]] = None,
    emailTemplates   : Set[String]         = Set.empty,
    pdfTemplates     : Set[String]         = Set.empty,
    customModes      : Set[String]         = Set.empty,
    formProcessNames : Set[String]         = Set.empty
  )

  sealed trait ParamValidationError {
    def alertKey: String

    def describe(actionName: String, paramName: String, rawValue: String): String
  }
  object ParamValidationError {
    case object RequiredValue extends ParamValidationError {
      val alertKey = "required"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Parameter "$paramName" of action "$actionName" requires a non-empty value"""
    }
    case object InvalidBoolean extends ParamValidationError {
      val alertKey = "invalid-boolean"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Parameter "$paramName" of action "$actionName" must be "true", "false", or an AVT expression"""
    }
    case object InvalidChoice extends ParamValidationError {
      val alertKey = "invalid-choice"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Invalid value "$rawValue" for parameter "$paramName" of action "$actionName""""
    }
    case object InvalidToken extends ParamValidationError {
      val alertKey = "invalid-token"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Unsupported value in parameter "$paramName" of action "$actionName": "$rawValue""""
    }
    case object InvalidDuration extends ParamValidationError {
      val alertKey = "invalid-duration"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Invalid duration "$rawValue" for parameter "$paramName" of action "$actionName""""
    }
    case object InvalidRepeatAt extends ParamValidationError {
      val alertKey = "invalid-at"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Invalid "at" value "$rawValue" for action "$actionName""""
    }
    case object InvalidDetailMode extends ParamValidationError {
      val alertKey = "invalid-mode"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Unsupported mode "$rawValue" for parameter "$paramName" of action "$actionName""""
    }
    case object InvalidNCName extends ParamValidationError {
      val alertKey = "invalid-ncname"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Invalid identifier "$rawValue" for parameter "$paramName" of action "$actionName""""
    }
    case object UnknownControl extends ParamValidationError {
      val alertKey = "unknown-control"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Unknown control "$rawValue" in parameter "$paramName" of action "$actionName""""
    }
    case object UnknownSection extends ParamValidationError {
      val alertKey = "unknown-section"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Unknown section "$rawValue" in parameter "$paramName" of action "$actionName""""
    }
    case object UnknownProcess extends ParamValidationError {
      val alertKey = "unknown-process"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Unknown sub-process "$rawValue" in parameter "$paramName" of action "$actionName""""
    }
    case object SelfProcessCall extends ParamValidationError {
      val alertKey = "self-process"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Process "$rawValue" cannot call itself"""
    }
    case object UnknownParameter extends ParamValidationError {
      val alertKey = "unknown-param"
      def describe(actionName: String, paramName: String, rawValue: String): String =
        s"""Action "$actionName" does not support parameter "$paramName""""
    }
  }

  sealed trait StepValidationError {
    def alertKey: String
    def details : String
  }
  object StepValidationError {
    case class UnknownAction(actionName: String) extends StepValidationError {
      val alertKey = "unknown-action"
      val details  = s"""Unknown action or sub-process "$actionName""""
    }
    case class SelfProcessCall(processName: String) extends StepValidationError {
      val alertKey = "self-process"
      val details  = s"""Process "$processName" cannot call itself"""
    }
    case class SubProcessWithParams(actionName: String) extends StepValidationError {
      val alertKey = "subprocess-params"
      val details  = s"""Sub-process "$actionName" does not accept parameters"""
    }
    case class MissingRequiredParam(actionName: String, paramName: String) extends StepValidationError {
      val alertKey = "missing-required"
      val details  = s"""Action "$actionName" requires parameter "$paramName""""
    }
    case class MissingRequiredOneOf(actionName: String, group: List[String]) extends StepValidationError {
      val alertKey = "missing-one-of"
      val details  = s"""Action "$actionName" requires at least one of: ${group.map(p => s""""$p"""").mkString(", ")}"""
    }
    case class MutuallyExclusiveParams(actionName: String, group: List[String]) extends StepValidationError {
      val alertKey = "mutually-exclusive"
      val details  = s"""Action "$actionName" cannot specify both ${group.map(p => s""""$p"""").mkString(" and ")}"""
    }
    case class DuplicateParams(actionName: String, paramName: String) extends StepValidationError {
      val alertKey = "duplicate-param"
      val details  = s"""Action "$actionName" has duplicate parameter "$paramName""""
    }
    case class UnknownParam(actionName: String, paramName: String) extends StepValidationError {
      val alertKey = "unknown-param"
      val details  = s"""Action "$actionName" does not support parameter "$paramName""""
    }
  }

  def isValidDurationLiteral(value: String): Boolean =
    value.trimAllToOpt.exists { trimmed =>
      Try(Duration(SubmissionUtils.normalizeDurationString(trimmed))).isSuccess
    }

  def isValidRepeatAtLiteral(value: String): Boolean =
    value.trimAllToOpt.exists {
      case "start" | "end" | "all" => true
      case other                   => other.toIntOption.exists(_ >= 1)
    }

  def isValidDetailModeLiteral(value: String): Boolean =
    value.trimAllToOpt.exists { trimmed =>
      ! DisallowedChangeModes(trimmed) &&
        (ParamControlKind.DetailModeSelect.choices.contains(trimmed) || CustomModeNameRegex.matches(trimmed))
    }

  // Validate a single parameter's static/domain value (XPath/AVT syntax is checked separately by XForms binds or XPath compiler)
  def validateParamStaticValue(
    actionName        : String,
    paramName         : String,
    rawValue          : String,
    siblingParamNames : Set[String],
    enclosingProcess  : Option[String],
    ctx               : FormProcessContext
  ): Option[ParamValidationError] = {

    import ParamValidationError.*

    ActionDefsByName.get(actionName) match {
      case None =>
        // Bare sub-process steps don't accept parameters
        if (isKnownSubProcessName(actionName, ctx.formProcessNames))
          Some(UnknownParameter)
        else
          None

      case Some(actionDef) =>
        actionDef.paramsByName.get(paramName) match {
          case None =>
            if (actionDef.allowsCustomParams && paramName.nonEmpty)
              None // Custom AVT property: any string/AVT value (including blank) is allowed; AVT syntax checked separately
            else
              Some(UnknownParameter)

          case Some(paramDef) =>
            val trimmedOpt = rawValue.trimAllToOpt

            trimmedOpt match {
              case None =>
                if (paramDef.allowBlank) None else Some(RequiredValue)

              case Some(trimmed) =>
                val isAvtExpr = paramDef.valueMode == Avt && containsAvtBraces(rawValue)

                if (isAvtExpr || paramDef.valueMode == XPath) {
                  // Dynamic XPath / AVT expression: syntax is validated by XPath compiler / xxf:XPath2* bind
                  None
                } else {
                  paramDef.kind match {
                    case Text | EmailTemplateSelect | PdfTemplateSelect =>
                      None

                    case BooleanSelect =>
                      if (trimmed == "true" || trimmed == "false") None else Some(InvalidBoolean)

                    case EnumSelect(choices, caseInsensitive) =>
                      val ok =
                        if (caseInsensitive) choices.exists(_.equalsIgnoreCase(trimmed))
                        else choices.contains(trimmed)
                      if (ok) None else Some(InvalidChoice)

                    case TokenMultiSelect(choices) =>
                      val tokens = trimmed.splitTo[List]()
                      if (tokens.forall(choices.contains)) None else Some(InvalidToken)

                    case DurationInput =>
                      if (isValidDurationLiteral(trimmed)) None else Some(InvalidDuration)

                    case RepeatAtInput =>
                      if (isValidRepeatAtLiteral(trimmed)) None else Some(InvalidRepeatAt)

                    case DetailModeSelect =>
                      if (isValidDetailModeLiteral(trimmed)) None else Some(InvalidDetailMode)

                    case NCNameInput =>
                      if (SaxonUtils.isValidNCName(trimmed)) None else Some(InvalidNCName)

                    case ControlNameSelect =>
                      // When `section-name` is also specified on `control-setvalue`, the target control lives inside
                      // a section template and may not be part of the outer form's control set.
                      if (siblingParamNames.contains("section-name"))
                        None
                      else
                        ctx.controlNames match {
                          case Some(validControls) if ! validControls.contains(trimmed) => Some(UnknownControl)
                          case _                                                        => None
                        }

                    case SectionNameSelect =>
                      ctx.sectionNames match {
                        case Some(validSections) if ! validSections.contains(trimmed) => Some(UnknownSection)
                        case _                                                        => None
                      }

                    case ProcessNameSelect =>
                      if (enclosingProcess.contains(trimmed))
                        Some(SelfProcessCall)
                      else if (! isKnownSubProcessName(trimmed, ctx.formProcessNames))
                        Some(UnknownProcess)
                      else
                        None
                  }
                }
            }
        }
    }
  }

  // Validate action-step-level constraints (action existence, required params, required-one-of, mutual exclusion, duplicates)
  def validateActionStep(
    actionName       : String,
    params           : List[(Option[String], String)],
    enclosingProcess : Option[String],
    ctx              : FormProcessContext
  ): List[StepValidationError] = {

    import StepValidationError.*

    val resolvedParams: List[(String, String)] =
      params.map {
        case (Some(pName), pVal) => (pName, pVal)
        case (None, pVal)        => (resolveUnnamedParamName(actionName, pVal), pVal)
      }

    val nonEmptyNames = resolvedParams.map(_._1).filter(_.nonEmpty)
    val duplicateErrors =
      nonEmptyNames
        .groupBy(identity)
        .collect { case (pName, occurrences) if occurrences.size > 1 => DuplicateParams(actionName, pName) }
        .toList

    ActionDefsByName.get(actionName) match {
      case None =>
        if (enclosingProcess.contains(actionName)) {
          List(SelfProcessCall(actionName))
        } else if (isKnownSubProcessName(actionName, ctx.formProcessNames)) {
          if (params.nonEmpty) List(SubProcessWithParams(actionName)) else Nil
        } else {
          List(UnknownAction(actionName))
        }

      case Some(actionDef) =>
        val selfCallErrors =
          if (actionDef.category == SubProcess && actionName != "process" && enclosingProcess.contains(actionName))
            List(SelfProcessCall(actionName))
          else
            Nil

        val paramMap: Map[String, String] = resolvedParams.toMap
        val presentParamNames: Set[String] = resolvedParams.map(_._1).filter(_.nonEmpty).toSet

        val unknownParamErrors =
          if (actionDef.allowsCustomParams)
            resolvedParams.collect { case ("", _) => UnknownParam(actionName, "(unnamed)") }
          else
            resolvedParams.collect {
              case (pName, _) if pName.isEmpty || ! actionDef.paramsByName.contains(pName) =>
                UnknownParam(actionName, if (pName.isEmpty) "(unnamed)" else pName)
            }

        val missingRequiredErrors =
          actionDef.params
            .filter(_.tier == Required)
            .filterNot(p => paramMap.get(p.name).exists(v => p.allowBlank || v.nonAllBlank))
            .map(p => MissingRequiredParam(actionName, p.name))

        val missingOneOfErrors =
          actionDef.requiredOneOfGroups.flatMap { group =>
            val satisfied = group.exists(pName => paramMap.get(pName).exists(_.nonAllBlank))
            if (satisfied) None else Some(MissingRequiredOneOf(actionName, group))
          }

        val mutuallyExclusiveErrors =
          actionDef.mutuallyExclusiveGroups.flatMap { group =>
            val activeInGroup = group.filter(presentParamNames.contains)
            if (activeInGroup.size > 1) Some(MutuallyExclusiveParams(actionName, activeInGroup)) else None
          }

        selfCallErrors ++
          duplicateErrors ++
          unknownParamErrors ++
          missingRequiredErrors ++
          missingOneOfErrors ++
          mutuallyExclusiveErrors
    }
  }

  // Decide whether a parsed parameter should initially open in free-form AVT input mode in the UI
  def shouldStartInAvtMode(
    actionName : String,
    paramName  : String,
    rawValue   : String
  ): Boolean =
    ActionDefsByName.get(actionName).flatMap(_.paramsByName.get(paramName)) match {
      case Some(paramDef) if paramDef.valueMode == Avt && paramDef.kind.hasConstrainedWidget =>
        if (containsAvtBraces(rawValue)) {
          true
        } else {
          paramDef.kind match {
            case BooleanSelect =>
              rawValue.nonAllBlank && rawValue != "true" && rawValue != "false"
            case EnumSelect(_, _) =>
              false // Keep dropdown so invalid static value (e.g. `level = ""`) is visibly flagged on the select
            case TokenMultiSelect(choices) =>
              rawValue.splitTo[List]().exists(t => ! choices.contains(t))
            case DetailModeSelect =>
              rawValue.nonAllBlank && ! DetailModeSelect.choices.contains(rawValue) && ! DisallowedChangeModes(rawValue)
            case _ =>
              false
          }
        }
      case _ =>
        false
    }
}
