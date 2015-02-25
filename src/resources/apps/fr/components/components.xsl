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
        xmlns:exf="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:p="http://www.orbeon.com/oxf/pipeline">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
    <xsl:import href="actions.xsl"/>

    <!-- Import components -->
    <xsl:import href="view.xsl"/>
    <xsl:import href="buttons.xsl"/>
    <xsl:import href="section.xsl"/><!-- pass global section properties to fr:section -->
    <xsl:import href="legacy-repeat.xsl"/> <!-- convert legacy fr:repeat to fr:grid -->

    <!-- Global variables -->
    <xsl:variable name="app" select="doc('input:instance')/*/app" as="xs:string"/>
    <xsl:variable name="form" select="doc('input:instance')/*/form" as="xs:string"/>
    <xsl:variable name="mode" select="doc('input:instance')/*/mode" as="xs:string?"/>

    <!-- Either the model with id fr-form-model, or the first model -->
    <xsl:variable name="fr-form-model"    select="/xh:html/xh:head/(xf:model[@id = 'fr-form-model'], xf:model[1])[1]"/>
    <xsl:variable name="fr-form-model-id" select="generate-id($fr-form-model)"/>

    <xsl:variable name="is-detail" select="not($mode = ('summary', 'home', ''))" as="xs:boolean"/>
    <xsl:variable name="is-form-builder" select="$app = 'orbeon' and $form = 'builder'" as="xs:boolean"/>
    <xsl:variable name="is-noscript-support" select="$fr-form-model/@xxf:noscript-support = 'true'" as="xs:boolean"/>
    <xsl:variable name="is-noscript" select="doc('input:request')/request/parameters/parameter[name = 'fr-noscript']/value = 'true'
                                                and $is-noscript-support" as="xs:boolean"/>
    <xsl:variable name="input-data" select="/*" as="element(xh:html)"/>

    <!-- Properties -->
    <xsl:variable
        name="version"
        as="xs:string?"
        select="version:versionStringIfAllowedOrEmpty()"
        xmlns:version="java:org.orbeon.oxf.common.Version"/>

    <xsl:variable name="has-noscript-link" select="p:property(string-join(('oxf.fr.noscript-link', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="is-noscript-table" select="not(not(p:property(string-join(('oxf.fr.detail.noscript.table', $app, $form), '.'))) = false())" as="xs:boolean"/>
    <xsl:variable name="is-noscript-section-collapse" select="not(p:property(string-join(('oxf.fr.detail.noscript.section.collapse', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="min-toc" select="(p:property(string-join(('oxf.fr.detail.toc', $app, $form), '.')), -1)[1]" as="xs:integer"/>
    <xsl:variable name="has-toc" select="$min-toc ge 0" as="xs:boolean"/>
    <xsl:variable name="error-summary" select="p:property(string-join(('oxf.fr.detail.error-summary', $app, $form), '.'))" as="xs:string?"/>
    <xsl:variable name="is-ajax-section-collapse" select="not(p:property(string-join(('oxf.fr.detail.ajax.section.collapse', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="default-logo-uri" select="p:property(string-join(('oxf.fr.default-logo.uri', $app, $form), '.'))" as="xs:string?"/>
    <xsl:variable name="hide-logo" select="p:property(string-join(('oxf.fr.detail.hide-logo', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="hide-footer" select="p:property(string-join(('oxf.fr.detail.hide-footer', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="hide-top" select="p:property(string-join(('oxf.fr.detail.hide-top', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="hide-buttons-bar" select="p:property(string-join(('oxf.fr.detail.hide-buttons-bar', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="css-uri" select="p:split(normalize-space(p:property(string-join(('oxf.fr.css.uri', $app, $form), '.'))))" as="xs:string*"/>
    <xsl:variable name="custom-css-uri" select="p:split(normalize-space(p:property(string-join(('oxf.fr.css.custom.uri', $app, $form), '.'))))" as="xs:string*"/>
    <xsl:variable name="js-uri" select="p:split(normalize-space(p:property(string-join(('oxf.fr.js.uri', $app, $form), '.'))))" as="xs:string*"/>
    <xsl:variable name="custom-js-uri" select="p:split(normalize-space(p:property(string-join(('oxf.fr.js.custom.uri', $app, $form), '.'))))" as="xs:string*"/>
    <xsl:variable name="inner-buttons" select="p:split(p:property(string-join(('oxf.fr.detail.buttons.inner', $app, $form), '.')))" as="xs:string*"/>
    <xsl:variable name="is-inline-hints" select="not(p:property(string-join(('oxf.fr.detail.hints.inline', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="is-animate-sections" select="not($is-noscript) and not(p:property(string-join(('oxf.fr.detail.ajax.section.animate', $app, $form), '.')) = false())" as="xs:boolean"/>
    <xsl:variable name="captcha-type" as="xs:string"  select="p:property(string-join(('oxf.fr.detail.captcha', $app, $form), '.'))"/>
    <xsl:variable name="has-captcha"  as="xs:boolean" select="$captcha-type = ('reCAPTCHA', 'SimpleCaptcha')"/>

    <xsl:variable name="is-section-collapse" select="(not($is-noscript) and $is-ajax-section-collapse) or $is-noscript-section-collapse" as="xs:boolean"/>

    <xsl:variable name="error-summary-top"    select="normalize-space($error-summary) = ('top', 'both')"        as="xs:boolean"/>
    <xsl:variable name="error-summary-bottom" select="normalize-space($error-summary) = ('', 'bottom', 'both')" as="xs:boolean"/>

    <xsl:variable name="view-appearance" as="xs:string" select="(p:property(string-join(('oxf.fr.detail.view.appearance', $app, $form), '.')), 'full')[1]"/>
    <xsl:variable name="custom-model"    as="xs:anyURI?" select="p:property(string-join(('oxf.fr.detail.model.custom', $app, $form), '.'))"/>

    <xsl:template match="/xh:html">
        <!-- Handle document language -->
        <xh:html lang="{{xxf:instance('fr-language-instance')}}"
                 xml:lang="{{xxf:instance('fr-language-instance')}}">
            <xsl:apply-templates select="@*"/>

            <!-- Scope variable with Form Runner resources -->
            <xf:var name="fr-resources" model="fr-resources-model" value="$fr-fr-resources" as="element(resource)?"/>
            <!-- Scope form resources -->
            <xf:var name="form-resources" model="fr-resources-model" value="$fr-form-resources" as="element(resource)?"/>

            <!-- Title from the current form's metadata -->
            <xf:var
                name="title-from-metadata"
                value="xxf:instance('fr-form-metadata')/title[@xml:lang = xxf:instance('fr-language-instance')], xxf:instance('fr-form-metadata')/title"/>

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
                value="string(($title-from-metadata, $title-from-output, '{replace(xh:head/xh:title, '''', '''''')}', $fr-resources/untitled-form)[normalize-space()][1])"/>

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
                    value="xxf:format-message(
                               $fr-resources/errors/form-title,
                               (
                                   xxf:instance('fr-error-summary-instance')/visible-counts/(if (count((@error, @warning, @info)[. gt 0]) gt 1) then 3 else if (@error gt 0) then 0 else if (@warning gt 0) then 1 else if (@info gt 0) then 2 else 4),
                                   xxf:instance('fr-error-summary-instance')/visible-counts/xs:integer(@alert),
                                   $title
                               )
                           )"/>
            </xh:title>

            <!-- Form Runner CSS (standard and custom) -->
            <xsl:for-each select="$css-uri, $custom-css-uri">
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
            <xsl:if test="not($is-noscript)">
                <xsl:for-each select="$js-uri, $custom-js-uri">
                    <xh:script type="text/javascript" src="{.}"/>
                </xsl:for-each>
            </xsl:if>
        </xsl:copy>
    </xsl:template>

    <!-- Add Form Runner models and scripts before the main model -->
    <xsl:template match="/xh:html/xh:head/xf:model[generate-id() = $fr-form-model-id]">

        <!-- Model receiving input parameters -->
        <xf:model
                id="fr-parameters-model"
                xxf:readonly-appearance="{{for $mode in xxf:instance('fr-parameters-instance')/mode/string()
                                           return
                                               if ($mode = ('view', 'email') or ($mode = 'pdf' and normalize-space(xxf:instance('fr-form-attachments')/pdf) = ''))
                                               then 'static'
                                               else 'dynamic'}}"
                xxf:encrypt-item-values="{{for $mode in xxf:instance('fr-parameters-instance')/mode/string()
                                           return not($mode = 'pdf' and normalize-space(xxf:instance('fr-form-attachments')/pdf) != '')}}"
                xxf:noscript="{{xxf:get-request-parameter('fr-noscript') = 'true'}}"
                xxf:order="{{if (property('xxf:noscript')) then 'label control alert hint help' else 'help label control alert hint'}}"

                xxf:host-language="{{
                    for $mode in xxf:instance('fr-parameters-instance')/mode/string()
                    return if ($mode = 'controls') then 'xml' else 'xhtml'}}"
                xxf:no-updates="{{
                    for $mode in xxf:instance('fr-parameters-instance')/mode/string()
                    return if ($mode = ('controls', 'pdf')) then 'true' else 'false'}}"

                xxf:noscript-support="{$is-noscript-support}"
                xxf:external-events="{@xxf:external-events} fr-open-pdf"
                xxf:xforms11-switch="false"
                xxf:xpath-analysis="true">

            <!-- Don't enable client events filtering for FB -->
            <xsl:if test="$is-form-builder">
                <xsl:attribute name="xxf:client.events.filter"/>
            </xsl:if>
            <!-- Override if specified -->
            <xsl:copy-of select="@xxf:xpath-analysis"/>
            <xsl:copy-of select="@xxf:no-updates"/><!-- for unit tests, import, validate -->

            <!-- Parameters passed to this page -->
            <!-- NOTE: the <document> element may be modified, so we don't set this as read-only -->
            <xf:instance id="fr-parameters-instance" src="input:instance"/>

        </xf:model>

        <!-- This model handles global actions on form sections -->
        <xf:model id="fr-sections-model">

            <xsl:variable name="section-ids" select="$input-data//fr:section/@id" as="xs:string*"/>
            <xsl:variable name="section-ids-sequence" select="concat('(', string-join(for $s in $section-ids return concat('''', $s, ''''), ','), ')')" as="xs:string*"/>

            <!-- Collapse or expand all sections -->
            <xf:dispatch ev:event="fr-collapse-all" iterate="{$section-ids-sequence}" name="fr-collapse" targetid="{{.}}"/>
            <xf:dispatch ev:event="fr-expand-all"   iterate="{$section-ids-sequence}" name="fr-expand"   targetid="{{.}}"/>
        </xf:model>
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
                <!-- Dispatch to the appropriate error summaries -->
                <!-- Don't dispatch to top error-summary if not present; but always dispatch to bottom error summary as it is always included -->
                <xsl:if test="$error-summary-top">
                    <xf:dispatch name="fr-unvisit-all" targetid="error-summary-control-top"/>
                </xsl:if>
                <xf:dispatch name="fr-unvisit-all" targetid="error-summary-control-bottom"/>
            </xf:action>
            <!-- Mark all controls as visited -->
            <xf:action ev:event="fr-visit-all">
                <!-- Dispatch to the appropriate error summaries -->
                <!-- Don't dispatch to top error-summary if not present; but always dispatch to bottom error summary as it is always included -->
                <xsl:if test="$error-summary-top">
                    <xf:dispatch name="fr-visit-all" targetid="error-summary-control-top"/>
                </xsl:if>
                <xf:dispatch name="fr-visit-all" targetid="error-summary-control-bottom"/>
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
        </xf:model>
        <!-- This model handles document persistence -->
        <xi:include href="oxf:/apps/fr/includes/persistence/persistence-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles i18n resources -->
        <!-- NOTE: Place after fr-persistence-model, as it needs the list of available form languages, but before
             fr-form-model, as that model needs the language set. -->
        <xi:include href="oxf:/apps/fr/i18n/resources-model.xml" xxi:omit-xml-base="true"/>

        <xf:model id="fr-pdf-model">
            <!-- Open PDF for the current form data (dispatch of the event done from pdf-instant-view.xpl) -->
            <xf:action event="fr-open-pdf" type="xpath" xmlns:process="java:org.orbeon.oxf.fr.process.SimpleProcess">
                xxf:instance('fr-form-instance')/process:runProcess(
                    'oxf.fr.detail.process',
                    concat('open-pdf(lang = "', event('fr-language')[not(contains(., '"'))], '")')
                )
            </xf:action>
        </xf:model>

        <!-- Copy and annotate existing main model -->
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- If the first model doesn't have an id, add fr-form-model -->
            <xsl:if test="not(@id)">
                <xsl:attribute name="id" select="'fr-form-model'"/>
            </xsl:if>

            <!-- Focus on the first control supporting input on load. Place this before custom model content. Form
                 Builder for example can open a dialog upon load. Another possible fix would be to fix setfocus to
                 understand that if a modal dialog is currently visible, setting focus to a control outside that dialog
                 should not have any effect. See https://github.com/orbeon/orbeon-forms/issues/2010  -->
            <xf:setfocus ev:event="xforms-ready" control="fr-form-group" input-only="true"/>

            <!-- Custom model content -->
            <xsl:apply-templates select="node()"/>

            <!-- Variable exposing all the user roles -->
            <xf:var name="fr-roles" value="frf:orbeonRolesSequence()" xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"/>

            <!-- Variable exposing the form app/form/mode -->
            <xf:var name="fr-app"  value="xxf:instance('fr-parameters-instance')/app/string()"/>
            <xf:var name="fr-form" value="xxf:instance('fr-parameters-instance')/form/string()"/>
            <xf:var name="fr-mode" value="xxf:instance('fr-parameters-instance')/mode/string()"/>

            <!-- Bind to set the form instance read-only when necessary -->
            <xf:bind ref="instance('fr-form-instance')" readonly="$fr-mode = ('view', 'pdf', 'email')"/>

            <!-- Custom XForms model content to include -->
            <xsl:if test="normalize-space($custom-model)">
                <xsl:copy-of select="doc($custom-model)/*/node()"/>
            </xsl:if>

            <!-- "Universal" submission. We scope this in fr-form-model so that variables are accessible with
                 xxf:evaluate-avt(). See https://github.com/orbeon/orbeon-forms/issues/1300 -->
            <xsl:copy-of select="doc('universal-submission.xml')/*/node()"/>

        </xsl:copy>

        <xsl:if test="not($is-noscript)">
            <!-- Handle checking dirty status -->
            <xi:include href="oxf:/apps/fr/includes/check-dirty-script.xhtml" xxi:omit-xml-base="true"/>
        </xsl:if>

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

</xsl:stylesheet>
