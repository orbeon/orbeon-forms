/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms


object XFormsProperties {

  // Document properties
  val FunctionLibraryProperty                   = "function-library"
  val XblSupportProperty                        = "xbl-support"

  val StateHandlingProperty                     = "state-handling"
  val StateHandlingServerValue                  = "server"
  val StateHandlingClientValue                  = "client"

  val NoscriptProperty                          = "noscript"

  val ReadonlyAppearanceProperty                = "readonly-appearance"
  val ReadonlyAppearanceStaticValue             = "static"
  val ReadonlyAppearanceDynamicValue            = "dynamic"
  val ReadonlyAppearanceStaticSelectProperty    = "readonly-appearance.static.select"
  val ReadonlyAppearanceStaticSelect1Property   = "readonly-appearance.static.select1"

  val OrderProperty                             = "order"
  val DefaultOrderProperty                      = "label control help alert hint"

  val HostLanguage                              = "host-language"

  val LabelElementNameProperty                  = "label-element"
  val HintElementNameProperty                   = "hint-element"
  val HelpElementNameProperty                   = "help-element"
  val AlertElementNameProperty                  = "alert-element"

  val LabelAppearanceProperty                   = "label.appearance"
  val HintAppearanceProperty                    = "hint.appearance"
  val HelpAppearanceProperty                    = "help.appearance"

  val StaticReadonlyHintProperty                = "static-readonly-hint"
  val StaticReadonlyAlertProperty               = "static-readonly-alert"

  val UploadMaxSizeProperty                     = "upload.max-size"
  val UploadMaxSizeAggregateProperty            = "upload.max-size-aggregate"
  val UploadMaxSizeAggregateExpressionProperty  = "upload.max-size-aggregate-expression"
  val UploadMediatypesProperty                  = "upload.mediatypes"

  val ExternalEventsProperty                    = "external-events"

  val OptimizeGetAllProperty                    = "optimize-get-all"
  val OptimizeLocalSubmissionReplaceAllProperty = "optimize-local-submission"
  val LocalSubmissionForwardProperty            = "local-submission-forward"

  val ExposeXpathTypesProperty                  = "expose-xpath-types"
  val AjaxUpdateFullThreshold                   = "ajax.update.full.threshold"
  val NoUpdates                                 = "no-updates"

  val TypeOutputFormatPropertyPrefix            = "format.output."
  val TypeInputFormatPropertyPrefix             = "format.input."

  val DateFormatInputProperty                   = TypeInputFormatPropertyPrefix + "date"

  val ShowRecoverableErrorsProperty             = "show-recoverable-errors"

  private val LoginPageDetectionRegexp          = "login-page-detection-regexp"

  val SessionHeartbeatProperty                  = "session-heartbeat"
  val SessionHeartbeatDelayProperty             = "session-heartbeat-delay"

  // Could be `upload.delay-before-progress-refresh` for consistency with new properties
  val DelayBeforeUploadProgressRefreshProperty  = "delay-before-upload-progress-refresh"

  val RevisitHandlingProperty                   = "revisit-handling"
  val RevisitHandlingRestoreValue               = "restore"

  val HelpHandlerProperty                       = "help-handler"
  private val HelpTooltipProperty               = "help-tooltip"

  val AsyncSubmissionPollDelay                  = "submission-poll-delay"

  private val UseAria                           = "use-aria"

  val Xforms11SwitchProperty                    = "xforms11-switch"

  val EncryptItemValuesProperty                 = "encrypt-item-values"
  val XpathAnalysisProperty                     = "xpath-analysis"
  val CalculateAnalysisProperty                 = "analysis.calculate"

  val SanitizeProperty                          = "sanitize"

  val AssetsBaselineExcludesProperty            = "assets.baseline.excludes"
  val AssetsBaselineUpdatesProperty             = "assets.baseline.updates"
  val InlineResourcesProperty                   = "inline-resources"

  sealed trait PropertyParser[T] {
    def parse(s: String): T
  }

  implicit object IntPropertyParser extends PropertyParser[Int] {
    def parse(s: String): Int = s.toInt
  }

  implicit object BooleanPropertyParser extends PropertyParser[Boolean] {
    def parse(s: String): Boolean = s.toBoolean
  }

  implicit object StringPropertyParser extends PropertyParser[String] {
    def parse(s: String): String = s
  }

  case class PropertyDefinition[T : PropertyParser](name: String, defaultValue: T, propagateToClient: Boolean) {
    def parseProperty(s: String): T =
      implicitly[PropertyParser[T]].parse(s)
  }

  private val SupportedDocumentPropertiesDefaults =
    Array(
      PropertyDefinition(StateHandlingProperty,      StateHandlingServerValue,       propagateToClient = false),
      PropertyDefinition(ReadonlyAppearanceProperty, ReadonlyAppearanceDynamicValue, propagateToClient = false),
      PropertyDefinition(
        TypeOutputFormatPropertyPrefix + "date",
        "if (. castable as xs:date) then format-date(xs:date(.), '[FNn] [MNn] [D], [Y] [ZN]', 'en', (), ()) else .",
        propagateToClient = false
      ),
      PropertyDefinition(
        TypeOutputFormatPropertyPrefix + "dateTime",
        "if (. castable as xs:dateTime) then format-dateTime(xs:dateTime(.), '[FNn] [MNn] [D], [Y] [H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .",
        propagateToClient = false
      ),
      PropertyDefinition(
        TypeOutputFormatPropertyPrefix + "time",
        "if (. castable as xs:time) then format-time(xs:time(.), '[H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .",
        propagateToClient = false
      ),
      PropertyDefinition(
        TypeOutputFormatPropertyPrefix + "decimal",
        "if (. castable as xs:decimal) then format-number(xs:decimal(.),'###,###,###,##0.00') else .",
        propagateToClient = false
      ),
      PropertyDefinition(
        TypeOutputFormatPropertyPrefix + "integer",
        "if (. castable as xs:integer) then format-number(xs:integer(.),'###,###,###,##0') else .",
        propagateToClient = false
      ),
      PropertyDefinition(
        TypeOutputFormatPropertyPrefix + "float",
        "if (. castable as xs:float) then format-number(xs:float(.),'#,##0.000') else .",
        propagateToClient = false
      ),
      PropertyDefinition(
        TypeOutputFormatPropertyPrefix + "double",
        "if (. castable as xs:double) then format-number(xs:double(.),'#,##0.000') else .",
        propagateToClient = false
      ),
      PropertyDefinition(FunctionLibraryProperty,                   "",                          propagateToClient = false),
      PropertyDefinition(XblSupportProperty,                        "",                          propagateToClient = false),
      PropertyDefinition(ReadonlyAppearanceStaticSelectProperty,    "full",                      propagateToClient = false),
      PropertyDefinition(ReadonlyAppearanceStaticSelect1Property,   "full",                      propagateToClient = false),
      PropertyDefinition(OrderProperty,                             DefaultOrderProperty,        propagateToClient = false),
      PropertyDefinition(HostLanguage,                              "xhtml",                     propagateToClient = false),
      PropertyDefinition(LabelElementNameProperty,                  "label",                     propagateToClient = false),
      PropertyDefinition(HintElementNameProperty,                   "span",                      propagateToClient = false),
      PropertyDefinition(HelpElementNameProperty,                   "span",                      propagateToClient = false),
      PropertyDefinition(AlertElementNameProperty,                  "span",                      propagateToClient = false),
      PropertyDefinition(LabelAppearanceProperty,                   "full",                      propagateToClient = false),
      PropertyDefinition(HintAppearanceProperty,                    "full",                      propagateToClient = false),
      PropertyDefinition(HelpAppearanceProperty,                    "dialog",                    propagateToClient = false),
      PropertyDefinition(StaticReadonlyHintProperty,                false,                       propagateToClient = false),
      PropertyDefinition(StaticReadonlyAlertProperty,               false,                       propagateToClient = false),
      PropertyDefinition(UploadMaxSizeProperty,                     "",                          propagateToClient = false), // blank default (see #2956)
      PropertyDefinition(UploadMaxSizeAggregateProperty,            "",                          propagateToClient = false),
      PropertyDefinition(UploadMaxSizeAggregateExpressionProperty,  "",                          propagateToClient = false),
      PropertyDefinition(UploadMediatypesProperty,                  "*/*",                       propagateToClient = false),
      PropertyDefinition(ExternalEventsProperty,                    "",                          propagateToClient = false),
      PropertyDefinition(OptimizeGetAllProperty,                    true,                        propagateToClient = false),
      PropertyDefinition(OptimizeLocalSubmissionReplaceAllProperty, true,                        propagateToClient = false),
      PropertyDefinition(LocalSubmissionForwardProperty,            true,                        propagateToClient = false),
      PropertyDefinition(ExposeXpathTypesProperty,                  false,                       propagateToClient = false),
      PropertyDefinition(ShowRecoverableErrorsProperty,             10,                          propagateToClient = false),
      PropertyDefinition(EncryptItemValuesProperty,                 true,                        propagateToClient = false),
      PropertyDefinition(AsyncSubmissionPollDelay,                  10 * 1000,                   propagateToClient = false),
      PropertyDefinition(AjaxUpdateFullThreshold,                   20,                          propagateToClient = false),
      PropertyDefinition(NoUpdates,                                 false,                       propagateToClient = false),
      PropertyDefinition(Xforms11SwitchProperty,                    false,                       propagateToClient = false),
      PropertyDefinition(XpathAnalysisProperty,                     false,                       propagateToClient = false),
      PropertyDefinition(CalculateAnalysisProperty,                 false,                       propagateToClient = false),
      PropertyDefinition(SanitizeProperty,                          "",                          propagateToClient = false),
      PropertyDefinition(AssetsBaselineExcludesProperty,            "",                          propagateToClient = false),
      PropertyDefinition(AssetsBaselineUpdatesProperty,             "",                          propagateToClient = false),
      PropertyDefinition(InlineResourcesProperty,                   false,                       propagateToClient = false), // properties to propagate to the client

      PropertyDefinition(UseAria,                                   false,                       propagateToClient = true),
      PropertyDefinition(SessionHeartbeatProperty,                  true,                        propagateToClient = true),
      PropertyDefinition("delay-before-incremental-request",        500,                         propagateToClient = true),
      PropertyDefinition("internal-short-delay",                    100,                         propagateToClient = true),
      PropertyDefinition("delay-before-display-loading",            500,                         propagateToClient = true),
      PropertyDefinition(DelayBeforeUploadProgressRefreshProperty,  2000,                        propagateToClient = true),
      PropertyDefinition(RevisitHandlingProperty,                   RevisitHandlingRestoreValue, propagateToClient = true),
      PropertyDefinition(HelpTooltipProperty,                       false,                       propagateToClient = true),
      PropertyDefinition(DateFormatInputProperty,                   "[M]/[D]/[Y]",               propagateToClient = true),
      PropertyDefinition(TypeInputFormatPropertyPrefix + "time",    "[h]:[m]:[s] [P]",           propagateToClient = true),
      PropertyDefinition("datepicker.navigator",                    true,                        propagateToClient = true),
      PropertyDefinition("datepicker.two-months",                   false,                       propagateToClient = true),
      PropertyDefinition("show-error-dialog",                       true,                        propagateToClient = true),
      PropertyDefinition(LoginPageDetectionRegexp,                  "",                          propagateToClient = true)
  )

  val SupportedDocumentProperties: Map[String, PropertyDefinition[_ >: String with Boolean with Int]] =
    SupportedDocumentPropertiesDefaults map (p => p.name -> p) toMap
}
