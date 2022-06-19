<!--
  Copyright (C) 2011 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<xsl:stylesheet version="2.0"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
        xmlns:map="http://www.w3.org/2005/xpath-functions/map"
        xmlns:Wizard="java:org.orbeon.xbl.Wizard">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
    <xsl:import href="actions.xsl"/>
    <xsl:import href="actions-20182.xsl"/>

    <!-- Import components -->
    <xsl:import href="view.xsl"/>
    <xsl:import href="controls.xsl"/>
    <xsl:import href="buttons.xsl"/>
    <xsl:import href="section-and-grid.xsl"/> <!-- pass properties -->
    <xsl:import href="legacy-repeat.xsl"/>    <!-- convert legacy fr:repeat to fr:grid -->

    <!-- Global variables -->
    <xsl:variable name="app"  select="doc('input:instance')/*/app" as="xs:string"/>
    <xsl:variable name="form" select="doc('input:instance')/*/form" as="xs:string"/>
    <xsl:variable name="mode" select="doc('input:instance')/*/mode" as="xs:string?"/>

    <!-- Same logic as in `fr:is-background()` -->
    <xsl:variable
        name="is-background"
        select="starts-with(doc('input:request')/*/request-path, '/fr/service/') and $mode = ('new', 'edit', 'export')"
        as="xs:boolean"/>

    <!-- Either the model with id fr-form-model, or the first model -->
    <xsl:variable name="fr-form-model"    select="/xh:html/xh:head/(xf:model[@id = 'fr-form-model'], xf:model[1])[1]"/>
    <xsl:variable name="fr-form-model-id" select="generate-id($fr-form-model)"/>

    <xsl:variable name="fr-form-metadata" select="($fr-form-model/xf:instance[@id = 'fr-form-metadata']/*)[1]"/>

    <xsl:variable name="is-detail"           select="not($mode = ('summary', 'home', ''))"          as="xs:boolean"/>
    <xsl:variable name="is-summary"          select="$mode = 'summary'"                             as="xs:boolean"/>
    <xsl:variable name="is-form-builder"     select="$app = 'orbeon' and $form = 'builder'"         as="xs:boolean"/>

    <xsl:variable name="input-data" select="/*" as="element(xh:html)"/>

    <!-- MIP filtering -->
    <xsl:variable
        name="disable-relevant-param-opt"
        as="xs:boolean?"
        select="
            doc('input:request')/*/parameters/parameter[
                name  = 'disable-relevant' and
                value = ('true', 'false')
            ]/value/xs:boolean(.)"/>

    <xsl:variable
        name="disable-default-param-opt"
        as="xs:boolean?"
        select="
            doc('input:request')/*/parameters/parameter[
                name  = 'disable-default' and
                value = ('true', 'false')
            ]/value/xs:boolean(.)"/>

    <xsl:variable
        name="disable-calculate-in-readonly-modes"
        as="xs:boolean"
        select="
            $fr-form-metadata/readonly-disable-calculate = 'true' or (
                not($fr-form-metadata/readonly-disable-calculate = 'false') and
                p:property(string-join(('oxf.fr.detail.readonly.disable-calculate', $app, $form), '.')) = true()
            )"/>

    <xsl:variable
        name="disable-relevant"
        select="($is-background or $mode = 'test-pdf') and $disable-relevant-param-opt"
        as="xs:boolean"/>

    <xsl:variable
        name="disable-default"
        select="($is-background or $mode = 'test-pdf') and $disable-default-param-opt"
        as="xs:boolean"/>

    <xsl:variable
        name="disable-calculate-param-opt"
        as="xs:boolean?"
        select="
            doc('input:request')/*/parameters/parameter[
                name  = ('disable-calculations', 'disable-calculate') and
                value = ('true', 'false')
            ]/value/xs:boolean(.)"/>

    <xsl:variable
        name="disable-calculate"
        select="
            (: The parameter takes precedence :)
            if (($is-background or $mode = 'test-pdf') and exists($disable-calculate-param-opt)) then
                $disable-calculate-param-opt
            else
                $mode = ('view', 'pdf', 'test-pdf', 'email', 'controls') (: fr:is-readonly-mode() :) and
                $disable-calculate-in-readonly-modes"
        as="xs:boolean"/>

    <!-- Properties -->
    <xsl:variable
        name="version"
        as="xs:string?"
        select="version:versionStringIfAllowedOrEmpty()"
        xmlns:version="java:org.orbeon.oxf.common.Version"/>

    <xsl:variable name="min-toc"              select="(p:property(string-join(('oxf.fr.detail.toc', $app, $form), '.')), -1)[1]"                       as="xs:integer"/>
    <xsl:variable name="has-toc"              select="$min-toc ge 0"                                                                                   as="xs:boolean"/>
    <xsl:variable name="error-summary"        select="p:property(string-join(('oxf.fr.detail.error-summary', $app, $form), '.'))"                      as="xs:string?"/>
    <xsl:variable name="default-logo-uri"     select="p:trim(p:property(string-join(('oxf.fr.default-logo.uri', $app, $form), '.')))[p:non-blank()]"   as="xs:string?"/>
    <xsl:variable name="hide-logo"            select="p:property(string-join(('oxf.fr.detail.hide-logo', $app, $form), '.'))"                          as="xs:boolean?"/>
    <xsl:variable name="hide-footer"          select="p:property(string-join(('oxf.fr.detail.hide-footer', $app, $form), '.'))"                        as="xs:boolean?"/>
    <xsl:variable name="hide-buttons-bar"     select="p:property(string-join(('oxf.fr.detail.hide-buttons-bar', $app, $form), '.'))"                   as="xs:boolean?"/>

    <xsl:variable name="inner-buttons"        select="p:split(p:property(string-join(('oxf.fr.detail.buttons.inner', $app, $form), '.')))"             as="xs:string*"/>

    <xsl:variable name="error-summary-top"    select="normalize-space($error-summary) = ('top', 'both')"                                               as="xs:boolean"/>
    <xsl:variable name="error-summary-bottom" select="normalize-space($error-summary) = ('', 'bottom', 'both')"                                        as="xs:boolean"/>

    <xsl:function name="fr:get-uris-from-properties" as="xs:string*">
        <xsl:param name="name" as="xs:string"/>

        <xsl:sequence
            select="
                p:split(
                    normalize-space(
                        p:property(string-join(('oxf.fr', $name, $app, $form), '.'))
                    )
                )"/>

        <xsl:if test="$is-detail or $is-summary">
            <xsl:sequence
                select="
                    p:split(
                        normalize-space(
                            p:property(string-join(('oxf.fr', if ($is-detail) then 'detail' else 'summary', $name, $app, $form), '.'))
                        )
                    )"/>
        </xsl:if>
    </xsl:function>

    <xsl:variable
        name="css-uri"
        as="xs:string*"
        select="fr:get-uris-from-properties('css.uri'), fr:get-uris-from-properties('css.custom.uri')"/>
    <xsl:variable
        name="js-uri"
        as="xs:string*"
        select="fr:get-uris-from-properties('js.uri'), fr:get-uris-from-properties('js.custom.uri')"/>
    <xsl:variable
        name="assets-baseline-updates"
        as="xs:string"
        select="
            let $updates :=
                for $update in ('fr', 'fb'[$is-form-builder and $is-detail])
                return p:property(concat('oxf.xforms.assets.baseline.updates.', $update))
            return string-join($updates, ' ')
        "/>
    <xsl:variable
        name="label-appearance"
        as="xs:string"
        select="
            (
                p:property(string-join(('oxf.fr.detail.label.appearance', $app, $form), '.'))[. = ('full', 'minimal')],
                'full'
            )[1]"/>

    <xsl:variable
        name="hint-appearance"
        as="xs:string"
        select="
            (
                (: Deprecated property for backward compatibility only :)
                'full'   [p:property(string-join(('oxf.fr.detail.hints.inline', $app, $form), '.')) = true()],
                'tooltip'[p:property(string-join(('oxf.fr.detail.hints.inline', $app, $form), '.')) = false()],
                (: New property :)
                p:property(string-join(('oxf.fr.detail.hint.appearance', $app, $form), '.'))[. = ('full', 'minimal', 'tooltip')],
                (: Default :)
                'full'
            )[1]"/>

    <xsl:variable
        name="valid-attachment-max-size-or-empty"
        as="xs:string?"
        select="
            p:property(string-join(('oxf.fr.detail.attachment.max-size', $app, $form), '.'))[
                (: Allow -1 to mean 'unlimited' :)
                . castable as xs:integer and xs:integer(.) ge -1
            ]"/>

    <xsl:variable
        name="valid-attachment-max-size-aggregate-or-empty"
        as="xs:string?"
        select="
            p:property(string-join(('oxf.fr.detail.attachment.max-size-aggregate', $app, $form), '.'))[
                (: Allow -1 to mean 'unlimited' :)
                . castable as xs:integer and xs:integer(.) ge -1
            ]"/>

    <xsl:variable
        name="attachment-mediatypes"
        as="xs:string"
        select="p:property(string-join(('oxf.fr.detail.attachment.mediatypes', $app, $form), '.'))"/>

    <xsl:variable
        name="view-appearance"
        as="xs:string"
        select="
            (
                'formula-debugger'[$fr-form-metadata/formula-debugger = 'true'],
                'wizard'[$fr-form-metadata/wizard = 'true' or $mode = 'import'],
                p:property(string-join(('oxf.fr.detail.view.appearance', $app, $form), '.'))[
                    normalize-space() and not($fr-form-metadata/wizard = 'false')
                ],
                'full'
            )[1]"/>

    <xsl:variable
        name="use-view-appearance"
        as="xs:boolean"
        select="
            $mode = 'import' or
            not(
                not($mode = ('edit', 'new', 'test', 'compile')) or (: intentionally no test on 'test-pdf' :)
                $is-form-builder                                or
                $view-appearance = 'full'
            )"/>

    <xsl:variable
        name="wizard-mode"
        as="xs:string"
        select="
            (
                $fr-form-metadata/wizard-mode[
                    . = ('free', 'lax', 'strict')
                ],
                p:property(string-join(('oxf.xforms.xbl.fr.wizard.validate', $app, $form), '.'))[
                    . = (
                        'free', 'lax', 'strict',
                        'true' (: for backward compatibility :)
                    )
                ],
                'free'
            )[1]"/>

    <xsl:variable
        name="wizard-subsections-nav"
        as="xs:string"
        select="
            (
                $fr-form-metadata/wizard-subsections-nav[
                    . = ('true', 'false')
                ],
                p:property(string-join(('oxf.xforms.xbl.fr.wizard.subsections-nav', $app, $form), '.'))[
                    . = ('true', 'false')
                ],
                'false'
            )[1]"/>

    <xsl:variable
        name="wizard-subsections-toc"
        as="xs:string"
        select="
            (
                $fr-form-metadata/wizard-subsections-toc[
                    . = ('active', 'all', 'none')
                ],
                p:property(string-join(('oxf.xforms.xbl.fr.wizard.subsections-toc', $app, $form), '.'))[
                    . = ('active', 'all', 'none')
                ],
                'active'
            )[1]"/>

    <xsl:variable
        name="wizard-separate-toc"
        as="xs:string"
        select="
            (
                $fr-form-metadata/wizard-separate-toc[
                    . = ('true', 'false')
                ],
                p:property(string-join(('oxf.xforms.xbl.fr.wizard.separate-toc', $app, $form), '.'))[
                    . = ('true', 'false')
                ],
                'false'
            )[1]"/>

    <xsl:variable
        name="wizard-section-status"
        as="xs:string"
        select="
            (
                $fr-form-metadata/wizard-section-status[
                    . = ('true', 'false')
                ],
                p:property(string-join(('oxf.xforms.xbl.fr.wizard.section-status', $app, $form), '.'))[
                    . = ('true', 'false')
                ],
                'false'
            )[1]"/>

    <xsl:variable
        name="validation-mode"
        as="xs:string"
        select="
            (
                p:property(string-join(('oxf.fr.detail.validation-mode', $app, $form), '.'))[
                    . = ('incremental', 'explicit')
                ],
                'incremental'
            )[1]"/>

    <xsl:variable
        name="is-full-update"
        as="xs:boolean"
        select="p:property(string-join(('oxf.fr.detail.view.full-update', $app, $form), '.'))"/>

    <xsl:variable
        name="custom-model"
        as="xs:anyURI?"
        select="p:property(string-join(('oxf.fr.detail.model.custom', $app, $form), '.'))"/>

    <xsl:variable
        name="enable-initial-focus"
        as="xs:boolean"
        select="p:property(string-join(('oxf.fr.detail.initial-focus', $app, $form), '.'))"/>

    <!-- fr:section and fr:grid configuration -->
    <xsl:variable
        name="is-ajax-section-animate"
        select="not(p:property(string-join(('oxf.fr.detail.ajax.section.animate', $app, $form), '.')) = false())"
        as="xs:boolean"/>

    <xsl:variable
        name="is-fr-section-animate"
        as="xs:boolean"
        select="not(p:property(string-join(('oxf.xforms.xbl.fr.section.animate', $app, $form), '.')) = false())"/>

    <xsl:variable
        name="is-animate-sections"
        as="xs:boolean"
        select="$is-ajax-section-animate and $is-fr-section-animate"/>

    <xsl:variable
        name="is-ajax-section-collapse"
        as="xs:boolean"
        select="not(p:property(string-join(('oxf.fr.detail.ajax.section.collapse', $app, $form), '.')) = false())"/>

    <xsl:variable
        name="is-fr-section-collapsible"
        as="xs:boolean"
        select="not(p:property(string-join(('oxf.xforms.xbl.fr.section.collapsible', $app, $form), '.')) = false())"/>

    <xsl:variable
        name="is-section-collapsible"
        as="xs:boolean"
        select="$is-ajax-section-collapse and $is-fr-section-collapsible"/>

    <xsl:variable
        name="section-appearance"
        as="xs:string?"
        select="p:property(string-join(('oxf.xforms.xbl.fr.section.appearance', $app, $form), '.'))[normalize-space()]"/>

    <xsl:variable
        name="grid-appearance"
        as="xs:string?"
        select="p:property(string-join(('oxf.xforms.xbl.fr.grid.appearance', $app, $form), '.'))[normalize-space()]"/>

    <xsl:variable
        xmlns:version="java:org.orbeon.oxf.common.Version"
        name="calculated-value-appearance"
        as="xs:string"
        select="
            (
                (: Consider this a pseudo-XBL component called `fr:calculated-value` :)
                $fr-form-metadata/xbl/fr:calculated-value/@appearance[normalize-space()],
                p:property(string-join(('oxf.xforms.xbl.fr.calculated-value.appearance', $app, $form), '.'))[normalize-space()],
                for $created-version in $fr-form-metadata/created-with-version[normalize-space()]/normalize-space()
                    return
                        if (version:compare($created-version, '2018.2') ge 0) then
                            'full'
                        else
                            'minimal',
                'minimal'
            )[1]"/>

    <!-- Map of XBL direct names to form-level `pdf-appearance` values if any -->
    <xsl:variable
        name="select1-pdf-appearances"
        select="
            if ($mode = ('pdf', 'test-pdf')) then
                map:merge(
                    for $name in (
                        (: Use direct names :)
                        'dropdown-select1',
                        'dropdown-select1-search'
                    ) return
                        map:entry(
                            $name,
                            (
                                (: From form metadata :)
                                $fr-form-metadata/xbl/fr:*[local-name() = $name]/@fr:pdf-appearance[normalize-space()]/string(),
                                (: From properties :)
                                p:property(
                                    string-join(
                                        (
                                            'oxf.xforms.xbl.fr',
                                            $name,
                                            'pdf-appearance',
                                            $app,
                                            $form
                                        ),
                                        '.'
                                    )
                                )[normalize-space()]
                            )[1]
                        )
                )
            else
                map:merge(())"/>

    <xsl:variable
        name="section-insert"
        as="xs:string?"
        select="p:property(string-join(('oxf.xforms.xbl.fr.section.insert', $app, $form), '.'))[normalize-space()]"/>

    <xsl:variable
        name="grid-insert"
        as="xs:string?"
        select="p:property(string-join(('oxf.xforms.xbl.fr.grid.insert', $app, $form), '.'))[normalize-space()]"/>

    <xsl:template match="/xh:html">
        <!-- Handle document language -->
        <xh:html lang="{{xxf:instance('fr-language-instance')}}"
                 xml:lang="{{xxf:instance('fr-language-instance')}}">
            <xsl:apply-templates select="@*"/>

            <!-- Title from the current page's xf:output under HTML title -->
            <xsl:choose>
                <xsl:when test="xh:head/xh:title/xf:output">
                    <xf:var
                        name="title-from-output"
                        model="fr-form-model"
                        value="string()">
                        <xsl:apply-templates select="(xh:head/xh:title/xf:output)[1]/(@bind | @model | @context | @ref | @value)"/>
                    </xf:var>
                </xsl:when>
                <xsl:otherwise>
                    <xf:var name="title-from-output"/>
                </xsl:otherwise>
            </xsl:choose>

            <!-- Form title based on metadata or HTML title -->
            <xf:var
                name="title"
                model="fr-form-model"
                value="
                    string(
                        (
                            fr:form-title(),
                            $title-from-output,
                            '{replace(xh:head/xh:title, '''', '''''')}',
                            xxf:r('untitled-form', 'fr-fr-resources')
                        )[
                            normalize-space()
                        ][1]
                    )"/>

            <xsl:apply-templates select="node()"/>
        </xh:html>
    </xsl:template>

    <!-- Insert stylesheets -->
    <xsl:template match="xh:head">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- Set XHTML title -->
            <xh:title>
                <xsl:apply-templates select="xh:title/@*"/>

                <!-- Display localized errors count and form title -->
                <xf:output
                    model="fr-form-model"
                    value="xxf:format-message(
                               xxf:r('errors.form-title', 'fr-fr-resources'),
                               (
                                   xxf:instance('fr-error-summary-instance')/visible-counts/(if (count((@error, @warning, @info)[. gt 0]) gt 1) then 3 else if (@error gt 0) then 0 else if (@warning gt 0) then 1 else if (@info gt 0) then 2 else 4),
                                   xxf:instance('fr-error-summary-instance')/visible-counts/xs:integer(@alert),
                                   $title
                               )
                           )"/>
            </xh:title>

            <!-- Form Runner CSS (standard and custom) -->
            <xsl:for-each select="$css-uri">
                <xh:link rel="stylesheet" href="{.}" type="text/css" media="all"/>
            </xsl:for-each>

            <!-- Handle existing stylesheets -->
            <xsl:for-each select="xh:link | xh:style">
                <xsl:element name="xh:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:apply-templates select="@*|node()"/>
                </xsl:element>
            </xsl:for-each>

            <!-- Process the rest -->
            <xsl:apply-templates select="node() except (xh:title | xh:link | xh:style)"/>

            <!-- Form Runner JavaScript (standard and custom) -->
            <xsl:for-each select="$js-uri">
                <xh:script type="text/javascript" src="{.}"/>
            </xsl:for-each>
        </xsl:copy>
    </xsl:template>

    <!-- Add Form Runner models and scripts before the main model -->
    <xsl:template match="/xh:html/xh:head/xf:model[generate-id() = $fr-form-model-id]">

        <xsl:variable
            name="copy-custom-model"
            select="$is-detail and normalize-space($custom-model)"
            as="xs:boolean"/>

        <!-- Model receiving input parameters -->
        <xf:model
            id="fr-parameters-model"
            xxf:readonly-appearance="{{
                for $mode in fr:mode()
                return
                    if (
                        $mode = 'view' or (
                            $mode = ('pdf', 'test-pdf', 'email') and (
                                (: TODO: Consider `fr:use-pdf-template()` function :)
                                xxf:get-request-parameter('fr-use-pdf-template') = 'false' or
                                not(xxf:instance('fr-form-attachments')/pdf/xxf:trim() != '')
                            )
                        )
                    ) then
                        'static'
                    else
                        'dynamic'
            }}"
            xxf:encrypt-item-values="{{
                for $mode in fr:mode()
                return not(
                    $mode = ('pdf', 'test-pdf', 'email') and
                    (: TODO: Consider `fr:use-pdf-template()` function :)
                    xxf:instance('fr-form-attachments')/pdf/xxf:trim() != '' and
                    not(xxf:get-request-parameter('fr-use-pdf-template') = 'false')
                )
            }}"
            xxf:order="{{
                xxf:property(
                    string-join(
                        (
                            'oxf.fr.detail.lhha-order',
                            fr:app-name(),
                            fr:form-name()
                        ),
                        '.'
                    )
                )
            }}"
            xxf:host-language="{{
                for $mode in fr:mode()
                return
                    if ($mode = 'controls') then
                        'xml'
                    else
                        'xhtml'
            }}"
            xxf:no-updates="{{
                (:
                    This covers at least: 'email', 'pdf', 'test-pdf', 'tiff', 'controls', 'validate', 'import',
                    'schema', 'duplicate', 'attachments', 'publish', but also 'new' and 'edit' when
                    used in background mode, see https://github.com/orbeon/orbeon-forms/issues/3318.
                    The idea is that all services are non-interactive.
                :)
                starts-with(xxf:get-request-path(), '/fr/service/')
            }}"
            xxf:external-events="{
                string-join(
                    (
                        @xxf:external-events,
                        if ($copy-custom-model) then doc($custom-model)/*/@xxf:external-events else (),
                        'fb-test-pdf-prepare-data'
                    ),
                    ' '
                )
            }"
            xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary"
            xxf:xbl-support="org.orbeon.oxf.fr.xbl.FormRunnerXblSupport"
            xxf:xforms11-switch="false"
            xxf:xpath-analysis="true"
            xxf:label.appearance="{$label-appearance}"
            xxf:hint.appearance="{$hint-appearance}"
            xxf:assets.baseline.updates="{$assets-baseline-updates}"
            xxf:upload.max-size-aggregate-expression="
                sum(
                    xxf:instance('fr-form-instance')//*[
                        @filename and @mediatype and @size
                    ]/@size[
                        . castable as xs:integer
                    ]/xs:integer(.),
                    0
                )
            "
            xxf:static-readonly-hint="{
                if ($mode = 'test-pdf') then
                    'false'
                else
                    '{xxf:property(
                        string-join(
                            (
                                ''oxf.fr.detail.static-readonly-hint'',
                                fr:app-name(),
                                fr:form-name()
                            ),
                            ''.''
                        )
                    )}'}"
            xxf:static-readonly-alert="{
                if ($mode = 'test-pdf') then
                    'false'
                else
                    '{xxf:property(
                        string-join(
                            (
                                ''oxf.fr.detail.static-readonly-alert'',
                                fr:app-name(),
                                fr:form-name()
                            ),
                            ''.''
                        )
                    )}'}"
        >

            <!-- Override if specified -->
            <xsl:copy-of select="@xxf:xpath-analysis"/>
            <xsl:copy-of select="@xxf:no-updates"/><!-- for unit tests -->
            <xsl:copy-of select="@xxf:encrypt-item-values"/>
            <xsl:copy-of select="@xxf:label.appearance"/>
            <xsl:copy-of select="@xxf:hint.appearance"/>

            <xsl:choose>
                <xsl:when test="exists(@xxf:upload.max-size)">
                    <!-- Use if explicitly specified -->
                    <xsl:copy-of select="@xxf:upload.max-size"/>
                </xsl:when>
                <xsl:when test="exists($valid-attachment-max-size-or-empty)">
                    <!-- Else use Form Runner property if specified and valid -->
                    <xsl:attribute name="xxf:upload.max-size" select="$valid-attachment-max-size-or-empty"/>
                </xsl:when>
            </xsl:choose>

            <xsl:choose>
                <xsl:when test="exists(@xxf:upload.max-size-aggregate)">
                    <!-- Use if explicitly specified -->
                    <xsl:copy-of select="@xxf:upload.max-size-aggregate"/>
                </xsl:when>
                <xsl:when test="exists($valid-attachment-max-size-aggregate-or-empty)">
                    <!-- Else use Form Runner property if specified and valid -->
                    <xsl:attribute name="xxf:upload.max-size-aggregate" select="$valid-attachment-max-size-aggregate-or-empty"/>
                </xsl:when>
            </xsl:choose>

            <xsl:choose>
                <xsl:when test="exists(@xxf:upload.mediatypes)">
                    <!-- Use if explicitly specified -->
                    <xsl:copy-of select="@xxf:upload.mediatypes"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Else use Form Runner property (blank is the same as `*/*`) -->
                    <xsl:attribute name="xxf:upload.mediatypes" select="$attachment-mediatypes"/>
                </xsl:otherwise>
            </xsl:choose>

            <!-- Parameters passed to this page -->
            <!-- NOTE: the <document> element may be modified, so we don't set this as read-only -->
            <xf:instance id="fr-parameters-instance" src="input:instance"/>
            <!-- Internally, at the XForms level, reduce modes so as to avoid checking everywhere for a new mode -->
            <xf:bind
                ref="instance('fr-parameters-instance')/mode"
                xxf:default="
                    if (. = ('tiff', 'test-pdf')) then
                        'pdf'
                    else if (. = ('export') and xxf:is-blank(../document)) then
                        'new'
                    else if (. = ('export') and xxf:non-blank(../document)) then
                        'edit'
                    else
                        ."/>

        </xf:model>

        <!-- This model handles global actions on form sections -->
        <xf:model id="fr-sections-model">

            <xsl:variable name="section-ids" select="$input-data//fr:section/@id" as="xs:string*"/>
            <xsl:variable name="section-ids-sequence" select="concat('(', string-join(for $s in $section-ids return concat('''', $s, ''''), ','), ')')" as="xs:string*"/>

            <!-- Collapse or expand all sections -->
            <xf:dispatch ev:event="fr-collapse-all" iterate="{$section-ids-sequence}" name="fr-collapse" targetid="{{.}}"/>
            <xf:dispatch ev:event="fr-expand-all"   iterate="{$section-ids-sequence}" name="fr-expand"   targetid="{{.}}"/>
        </xf:model>
        <!-- This model handles document persistence -->
        <xi:include href="oxf:/apps/fr/includes/persistence/persistence-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles i18n resources -->
        <!-- Placed:
               - After  fr-persistence-model,    as it needs the list of available form languages
               - Before fr-form-model,           as that model needs the language set.
               - Before fr-error-summary-model,  as on language change resources need to update
                                                 before the error summary, which uses the resources,
                                                 see https://github.com/orbeon/orbeon-forms/issues/2505-->
        <xi:include href="oxf:/apps/fr/i18n/resources-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles global error summary information -->
        <xf:model id="fr-error-summary-model">
            <xf:instance id="fr-error-summary-instance" xxf:expose-xpath-types="true">
                <error-summary>
                    <!-- For form builder we disable the error summary and say that the form is always valid -->
                    <valid><xsl:value-of select="$is-form-builder"/></valid>
                    <counts         alert="0" error="0" warning="0" info="0"/>
                    <visible-counts alert="0" error="0" warning="0" info="0"/>
                </error-summary>
            </xf:instance>

            <xf:bind ref="valid" type="xs:boolean"/>
            <xf:bind ref="counts/@* | visible-counts/@*" type="xs:integer"/>

            <!-- Mark all controls as unvisited -->
            <xf:action ev:event="fr-unvisit-all">
                <xf:dispatch name="fr-unvisit-all" targetid="error-summary-control-bottom"/>
            </xf:action>

            <!-- Mark all controls as visited -->
            <xf:action ev:event="fr-visit-all fr-show-relevant-errors">

                <!-- When the wizard is in use, we don't want to visit *all* controls. -->
                <!-- See https://github.com/orbeon/orbeon-forms/issues/3178 -->
                <xxf:setvisited
                    control="fr-captcha"
                    visited="true"
                    recurse="true"/>

                <xf:dispatch
                    name="fr-show-relevant-errors"
                    targetid="fr-view-component"/>
            </xf:action>

            <!-- The error summary must be notified when the language changes so that alerts/errors can be updated -->
            <xf:action observer="fr-language-selector" event="xforms-value-changed">
                <!-- Dispatch to the appropriate error summaries -->
                <!-- Don't dispatch to top error-summary if not present; but always dispatch to bottom error summary as it is always included -->
                <xsl:if test="$error-summary-top">
                    <xf:dispatch name="fr-update-lang" targetid="error-summary-control-top"/>
                </xsl:if>
                <xf:dispatch name="fr-update-lang" targetid="error-summary-control-bottom"/>
            </xf:action>

            <xf:action observer="fr-view-component" event="fr-toc-shown" if="event('separate-toc')">
                <xsl:if test="$error-summary-top">
                    <xf:dispatch name="fr-hide" targetid="error-summary-control-top"/>
                </xsl:if>
                <xf:dispatch name="fr-hide" targetid="error-summary-control-bottom"/>
            </xf:action>

            <xf:action observer="fr-view-component" event="fr-section-shown">
                <xsl:if test="$error-summary-top">
                    <xf:dispatch name="fr-show" targetid="error-summary-control-top">
                        <xf:property name="section-name" value="event('section-name')[event('separate-toc')]"/>
                    </xf:dispatch>
                </xsl:if>
                <xf:dispatch name="fr-show" targetid="error-summary-control-bottom">
                    <xf:property name="section-name" value="event('section-name')[event('separate-toc')]"/>
                </xf:dispatch>
            </xf:action>

        </xf:model>

        <!-- Common actions implementation -->
        <xsl:if test="$fr-form-model-id = $models-with-actions-model-ids">
            <xf:model id="fr-actions-model">
                <xsl:call-template name="action-common-impl">
                    <xsl:with-param name="model" select="."/>
                </xsl:call-template>
            </xf:model>
        </xsl:if>

        <!-- Copy and annotate existing main model -->
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- If the first model doesn't have an id, add fr-form-model -->
            <xsl:if test="not(@id)">
                <xsl:attribute name="id" select="'fr-form-model'"/>
            </xsl:if>

            <!-- Scope variables for resources -->
            <xf:var
                name="fr-resources"
                value="xxf:get-variable('fr-resources-model', 'fr-fr-resources')"
                as="element(resource)?"/>
            <xf:var
                name="form-resources"
                value="xxf:get-variable('fr-resources-model', 'fr-form-resources')"
                as="element(resource)?"/>

            <!-- Focus on the first control supporting input on load. Place this before custom model content. Form
                 Builder for example can open a dialog upon load. Another possible fix would be to fix setfocus to
                 understand that if a modal dialog is currently visible, setting focus to a control outside that dialog
                 should not have any effect. See https://github.com/orbeon/orbeon-forms/issues/2010  -->
            <xsl:if test="$enable-initial-focus and $wizard-separate-toc = 'false'">
                <xf:setfocus
                    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
                    event="xforms-ready"
                    control="fr-view-component"
                    includes="{{frf:xpathFormRunnerStringProperty('oxf.fr.detail.focus.includes')}}"
                    excludes="{{frf:xpathFormRunnerStringProperty('oxf.fr.detail.focus.excludes')}}"/>
            </xsl:if>

            <!-- Custom model content -->
            <xsl:apply-templates select="node()"/>

            <!-- Variable exposing all the user roles -->

            <xf:var name="fr-roles" value="frf:xpathOrbeonRolesFromCurrentRequest()" xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"/>

            <!-- Variable exposing the form app/form/mode -->
            <!-- NOTE: This is no longer the preferred way now that we have `fr:` functions. -->
            <xf:var name="fr-app"  value="fr:app-name()"/>
            <xf:var name="fr-form" value="fr:form-name()"/>
            <xf:var name="fr-mode" value="fr:mode()"/>

            <!-- Bind to set the form instance read-only when necessary -->
            <xf:bind ref="instance('fr-form-instance')" readonly="fr:is-readonly-mode()"/>

            <!-- Custom XForms model content to include -->
            <xsl:if test="$copy-custom-model">
                <xsl:copy-of select="doc($custom-model)/*/node()"/>
            </xsl:if>

            <!-- "Universal" submission. We scope this in fr-form-model so that variables are accessible with
                 xxf:evaluate-avt(). See https://github.com/orbeon/orbeon-forms/issues/1300 -->
            <xsl:copy-of select="doc('universal-submission.xml')/*/node()"/>

            <!-- Common actions implementation -->
            <xsl:if test="$fr-form-model-id = $models-with-itemset-actions-models-ids">
                <xsl:copy-of select="fr:itemset-action-common-impl('fr-form-model')"/>
            </xsl:if>
            <xsl:copy-of select="fr:common-dataset-actions-impl(.)"/>
            <xsl:copy-of select="fr:common-service-actions-impl(.)"/>

            <!-- Helper for Form Builder test only -->
            <xf:action
                event="fb-test-pdf-prepare-data"
                if="fr:mode() = 'test'"
                type="javascript">
                <xf:param
                    name="data"
                    value="if (event('use-form-data') = 'true') then frf:encodeFormDataToSubmit(instance('fr-form-instance')) else ''"/>
                <xf:param
                    name="lang"
                    value="fr:lang()"/>
                <xf:body>
                    window.parent.ORBEON.xforms.Document.dispatchEvent(
                        {
                            targetId:   'fr-form-model',
                            eventName:  'fb-test-pdf-with-data',
                            properties: { 'fr-form-data': data, 'language': lang }
                        }
                    );
                </xf:body>
            </xf:action>

        </xsl:copy>

    </xsl:template>

    <xsl:template match="/xh:html/xh:head/xf:model[generate-id() = $fr-form-model-id]/xf:instance[1]">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- If there is no instance with fr-form-instance consider the first one is it -->
            <xsl:if test="not(exists(../xf:instance[@id = 'fr-form-instance']))">
                <xsl:attribute name="id" select="'fr-form-instance'"/>
            </xsl:if>

            <xsl:apply-templates select="node()"/>

        </xsl:copy>
    </xsl:template>

    <!-- Remove built-in XBL components which older versions of Orbeon Forms might inline by mistake (pre-4.0?). When
         that happened, only simple bindings were supported. See https://github.com/orbeon/orbeon-forms/issues/2395 -->
    <xsl:template match="/xh:html/xh:head/xbl:xbl/xbl:binding[starts-with(@element, 'fr|')]"/>

    <!-- Handle transformations of section template controls -->
    <xsl:template match="/xh:html/xh:head/xbl:xbl/xbl:binding[p:has-class('fr-section-component')]/xbl:template">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="node()" mode="within-controls">
                <xsl:with-param
                    name="library-name"
                    select="frf:findAppFromSectionTemplateUri(namespace-uri-for-prefix('component', ..))"
                    tunnel="yes"/>
            </xsl:apply-templates>
        </xsl:copy>
    </xsl:template>

    <!-- MIP filtering -->

    <xsl:template
        match="xf:bind/@relevant[$disable-relevant and not(. = ('false()', 'true()'))]"
        mode="filter-mips"/>

    <xsl:template
        match="xf:bind/@xxf:default[$disable-default]"
        mode="filter-mips"/>

    <xsl:template
        match="xf:bind/@calculate[$disable-calculate]"
        mode="filter-mips"/>

    <xsl:template
        match="
            /xh:html/xh:head//
                xf:model[
                    generate-id() = $candidate-action-models-ids
                ]/xf:bind[1]">

        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:choose>
                <xsl:when test="$disable-relevant or $disable-default or $disable-calculate">
                    <xsl:apply-templates select="node()" mode="filter-mips"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates select="node()"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:copy>

    </xsl:template>

</xsl:stylesheet>
