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
<xsl:stylesheet
        version="2.0"
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
        xmlns:formRunner="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:variable name="view"           select="(/xh:html/xh:body/fr:view)[1]" as="element(fr:view)?"/>
    <xsl:variable name="body"           select="($view/fr:body, $view)[1]"           as="element()?"/>
    <xsl:variable name="custom-buttons" select="$view/fr:buttons"                    as="element()*"/>
    <xsl:variable name="custom-layout"  select="empty($body)"/>

    <!-- Template for the default layout of a form -->
    <xsl:variable name="default-page-template" as="element(*)*">
        <fr:navbar/>

        <fr:description/>

        <!-- Error summary (if at top) -->
        <xsl:if test="$error-summary-top">
            <fr:error-summary position="top"/>
        </xsl:if>

        <fr:row>
            <fr:toc/>
        </fr:row>
        <fr:row>
            <fr:body/>
        </fr:row>
        <fr:row>
            <fr:captcha/>
        </fr:row>

        <!-- Error summary (if at bottom) -->
        <!-- If we configuration tells us the bottom error summary should not be shown, still include it but hide it with 'display: none'.
             This is necessary because the persistence model relies on the error summary to know if the data is valid. -->
        <xh:div>
            <xsl:if test="not($error-summary-bottom)">
                <xsl:attribute name="style">display: none</xsl:attribute>
            </xsl:if>
            <fr:error-summary position="bottom"/>
        </xh:div>

        <fr:row>
            <fr:noscript-help/>
        </fr:row>
        <fr:row>
            <fr:messages/>
        </fr:row>
        <fr:row>
            <fr:bottom-bar/>
        </fr:row>
        <fr:row>
            <fr:version/>
        </fr:row>
    </xsl:variable>

    <!-- Template for the default layout of the bottom bar -->
    <xsl:variable name="default-bottom-template" as="element(*)*">
        <fr:status-icons/>
        <fr:buttons-bar/>
    </xsl:variable>

    <xsl:template match="fr:row">
        <xh:div class="row">
            <xh:div class="span12">
                <xsl:apply-templates select="@* | node()"/>
            </xh:div>
        </xh:div>
    </xsl:template>

    <xsl:template match="fr:body">
        <!-- Form content. Set context on form instance and define this group as #fr-form-group as observers will refer to it. -->
        <xf:group id="fr-form-group" class="fr-body{if ($is-detail) then ' fr-border' else ''}" model="fr-form-model" ref="instance('fr-form-instance')">
            <xh:a name="fr-form"/>
            <xsl:choose>
                <xsl:when test="not($mode = ('edit', 'new', 'test')) or $is-form-builder or $view-appearance = ('full', '')">
                    <!-- Just place the content as is -->
                    <xsl:apply-templates select="if ($body) then $body/(node() except fr:buttons) else node()"/>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Insert appropriate XBL component -->
                    <!-- NOTE: Once we support XBL matching on @appearance, use instead
                         <fr:view appearance="{$view-appearance}">. -->
                    <xsl:element name="fr:{$view-appearance}">
                        <xsl:apply-templates select="if ($body) then $body/(node() except fr:buttons) else node()"/>
                    </xsl:element>
                </xsl:otherwise>
            </xsl:choose>
            <!--<fr:xforms-inspector/>-->
        </xf:group>
    </xsl:template>

    <!-- Main entry point -->
    <xsl:template match="xh:body">
        <xsl:copy>
            <!-- .orbeon is here to scope all Orbeon CSS rules -->
            <xsl:attribute name="class" select="string-join(('orbeon', concat('xforms-', if ($is-inline-hints) then 'disable' else 'enable', '-hint-as-tooltip'), 'xforms-disable-alert-as-tooltip', @class), ' ')"/>
            <xsl:apply-templates select="@* except @class"/>
            <xf:group model="fr-form-model" id="fr-view" class="container fr-view{concat(' fr-mode-', $mode)}" xxf:element="div">
                <xsl:apply-templates select="if ($custom-layout) then node() else $default-page-template"/>
                <xsl:call-template name="fr-hidden-controls"/>
                <xsl:call-template name="fr-dialogs"/>
            </xf:group>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="fr:navbar" name="fr-navbar">
        <xsl:variable name="header-classes" as="xs:string*" select="('navbar', 'navbar-inverse')"/>
        <xh:div class="{string-join($header-classes, ' ')}">
            <xh:div class="navbar-inner">
                <xh:div class="container">
                    <!-- Copy width attribute on view if specified -->
                    <xsl:copy-of select="$view/@width"/>

                    <xsl:if test="not($mode = ('email')) and not($hide-header)">
                        <xsl:choose>
                            <xsl:when test="$mode = 'view'">
                                <!-- View header -->
                                <xsl:variable name="default-objects" as="element()+">
                                    <fr:logo/>
                                    <fr:title/>
                                </xsl:variable>

                                <xsl:apply-templates select="$default-objects"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Standard header -->
                                <xsl:variable name="default-objects" as="element()+">
                                    <!-- These are typically to the right, but to help with IE7 we put them first. See:
                                         https://github.com/orbeon/orbeon-forms/issues/721 -->
                                    <fr:language-selector/>
                                    <fr:noscript-selector/>
                                    <fr:goto-content/>
                                    <!-- These are typically to the left -->
                                    <fr:logo/>
                                    <fr:title/>
                                </xsl:variable>

                                <xsl:apply-templates select="$default-objects"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:if>
                </xh:div>
            </xh:div>
        </xh:div>
    </xsl:template>

    <xsl:template match="fr:hidden-controls" name="fr-hidden-controls">
        <xh:span class="xforms-hidden">
            <!-- Hidden field to communicate to the client whether the data is safe -->
            <xf:input
                    model="fr-persistence-model"
                    ref="instance('fr-persistence-instance')/data-safe"
                    id="fr-data-safe-input"
                    class="xforms-disabled"/>

            <!-- Expose document id to JavaScript -->
            <xf:output id="fr-parameters-instance-document" ref="xxf:instance('fr-parameters-instance')/document" style="display: none"/>

            <!-- When the mode changes to "edit" after a save from /new, attempt to change the URL -->
            <xf:var name="mode-for-save" value="xxf:instance('fr-parameters-instance')/mode/string()">
                <!-- If URI is /new (it should be), change it to /edit/id -->
                <!-- If browser supporting the HTML5 history API (http://goo.gl/Ootqu) -->
                <xxf:script ev:event="xforms-value-changed" if="$mode-for-save = 'edit'">
                    if (history &amp;&amp; history.replaceState) {
                        if (location.href.lastIndexOf("/new") == location.href.length - 4)
                            history.replaceState(null, "", "edit/" + ORBEON.xforms.Document.getValue("fr-parameters-instance-document"));
                    }
                </xxf:script>
            </xf:var>

            <!-- This is a HACK for Form Builder only: place non-relevant instances of all toolbox controls so that
                 xxf:dynamic will have all the JavaScript and CSS resources available on the client.
                 See: https://github.com/orbeon/orbeon-forms/issues/31 -->
            <xsl:if test="$is-form-builder and $is-detail" xmlns:p="http://www.orbeon.com/oxf/pipeline" xmlns:fb="http://orbeon.org/oxf/xml/form-builder">

                <xsl:variable name="property-names"
                              select="p:properties-start-with('oxf.fb.toolbox.group')" as="xs:string*" />
                <xsl:variable name="resources-names"
                              select="distinct-values(for $n in $property-names return tokenize(p:property($n), '\s+'))" as="xs:string*"/>

                <xsl:variable name="resources"
                              select="for $uri in $resources-names return doc($uri)" as="document-node()*"/>

                <xsl:if test="$resources">
                    <!-- Non-relevant group -->
                    <xf:group ref="()">
                        <xsl:apply-templates select="$resources/*/xbl:binding/fb:metadata/(fb:template | fb:templates/fb:view)/*" mode="filter-fb-template"/>
                    </xf:group>
                </xsl:if>
            </xsl:if>
        </xh:span>
    </xsl:template>

    <xsl:template match="fr:captcha" name="fr-captcha">
        <!-- Captcha -->
        <xsl:if test="$has-captcha">
            <xf:group model="fr-persistence-model" appearance="xxf:internal">
                <xf:var name="captcha" value="instance('fr-persistence-instance')/captcha"/>
                <xf:var name="mode"    value="xxf:instance('fr-parameters-instance')/mode"/>
                <xf:group id="fr-captcha-group" ref=".[$mode = ('new', 'edit') and not(property('xxf:noscript')) and $captcha = 'false']" class="fr-captcha">
                    <!-- Success: remember the captcha passed, which also influences validity -->
                    <xf:action ev:event="fr-verify-done">
                        <xf:setvalue ref="$captcha">true</xf:setvalue>
                        <xf:revalidate model="fr-persistence-model"/>
                        <xf:refresh/>
                    </xf:action>
                    <!-- Failure: load another challenge (supported by reCAPTCHA; SimpleCaptcha won't do anything) -->
                    <xf:dispatch ev:event="fr-verify-error" if="event('fr-error-code') != 'empty'" targetid="captcha" name="fr-reload"/>
                    <!-- Captcha component: either reCAPTCHA or SimpleCaptcha -->
                    <xsl:if test="$captcha = 'reCAPTCHA'">
                        <fr:recaptcha id="captcha" theme="clean" ref="$captcha">
                            <xf:label ref="$fr-resources/detail/labels/captcha-label"/>
                            <xf:alert ref="$fr-resources/detail/labels/captcha-alert"/>
                        </fr:recaptcha>
                    </xsl:if>
                    <xsl:if test="$captcha = 'SimpleCaptcha'">
                        <fr:simple-captcha id="captcha" ref="$captcha">
                            <xf:label ref="$fr-resources/detail/labels/captcha-label"/>
                            <xf:alert ref="$fr-resources/detail/labels/captcha-alert"/>
                        </fr:simple-captcha>
                    </xsl:if>
                </xf:group>
            </xf:group>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:noscript-help" name="fr-noscript-help">
        <!-- Noscript help section (shown only in edit mode) -->
        <xsl:if test="$is-noscript and $mode = ('edit', 'new')">
            <!-- Only display this section if there is at least one non-empty nested help text -->
            <xf:group
                ref=".[normalize-space(string-join(({string-join(($body//(fr:section | xf:*)[@id]/xf:help/@ref), ',')}), ''))]">
                <xh:div class="xforms-help-panel">
                    <xh:h2>
                        <xf:output value="$fr-resources/summary/titles/help"/>
                    </xh:h2>
                    <xh:ul>
                        <xsl:apply-templates select="$body/*" mode="noscript-help"/>
                    </xh:ul>
                </xh:div>
            </xf:group>
        </xsl:if>
    </xsl:template>

    <!-- Remove id elements on Form Builder templates -->
    <xsl:template match="@id | @bind" mode="filter-fb-template"/>
    <xsl:template match="@ref[not(normalize-space())] | @nodeset[not(normalize-space())]" mode="filter-fb-template">
        <xsl:attribute name="{name()}" select="'()'"/>
    </xsl:template>

    <xsl:template match="fr:title" name="fr-title">
        <!-- Q: Why do we need @ref here? -->
        <xh:h1><xf:output value="{if (exists(@ref)) then @ref else '$title'}"/></xh:h1>
    </xsl:template>

    <!-- Description in chosen language or first one if not found -->
    <xsl:template match="fr:description" name="fr-description">
        <xf:var
            name="description"
            value="({if (@paths) then concat(@paths, ', ') else ''}xxf:instance('fr-form-metadata')/description[@xml:lang = xxf:instance('fr-language-instance')],
                        xxf:instance('fr-form-metadata')/description)[normalize-space()][1]"/>

        <xh:p>
            <xf:output
                class="fr-form-description"
                model="fr-form-model"
                value="$description"/>
        </xh:p>
    </xsl:template>

    <xsl:template match="fr:logo">
        <xsl:if test="not($hide-logo) and normalize-space($default-logo-uri)">
            <xh:img src="{$default-logo-uri}" alt=""/>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:language-selector">
        <!-- Switch language -->
        <xh:div class="fr-language-choice">
            <!-- Put default language first, then other languages -->
            <xf:var
                name="available-languages"
                model="fr-resources-model"
                value="formRunner:getFormLangSelection($app, $form, xxf:instance('fr-form-resources')/resource/@xml:lang/string())"/>

            <!-- Don't display language selector if there is only one language -->
            <!-- NOTE: Resolve model here, as for now model within XBL component won't resolve -->
            <xf:group id="fr-language-selector" model="fr-resources-model" ref=".[count($available-languages) gt 1]">
                <fr:link-select1 ref="instance('fr-language-instance')">
                    <xf:itemset ref="$available-languages">
                        <xf:label ref="(xxf:instance('fr-languages-instance')/language[@code = context()]/@native-name, context())[1]"/>
                        <xf:value ref="context()"/>
                    </xf:itemset>
                </fr:link-select1>
            </xf:group>
        </xh:div>
    </xsl:template>

    <xsl:template match="fr:noscript-selector">
        <!-- Switch script/noscript -->
        <xsl:if test="$mode = ('edit', 'new') and not($has-noscript-link = false()) and not($is-form-builder) and $is-noscript-support">
            <!-- NOTE: without xml:space="preserve", XSLT strip spaces. This causes a CSS  bug with IE 7:
                 https://github.com/orbeon/orbeon-forms/issues/733 -->
            <xh:div class="fr-noscript-choice" xml:space="preserve">
                <xf:group appearance="xxf:internal">
                    <xf:group ref=".[not(property('xxf:noscript'))]">
                        <xf:trigger appearance="minimal">
                            <xf:label xml:space="preserve">
                                <xf:output value="$fr-resources/summary/labels/noscript"/>
                            </xf:label>
                        </xf:trigger>
                    </xf:group>
                    <xf:group ref=".[property('xxf:noscript')]">
                        <xf:trigger appearance="minimal">
                            <xf:label xml:space="preserve">
                                <xf:output value="$fr-resources/summary/labels/script"/>
                            </xf:label>
                        </xf:trigger>
                    </xf:group>
                    <!-- React to activation of both triggers -->
                    <xf:action ev:event="DOMActivate">
                        <!-- Send submission -->
                        <xsl:choose>
                            <xsl:when test="$mode = 'summary'">
                                <!-- Submission for summary mode -->
                                <xf:send submission="fr-edit-switch-script-summary-submission"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Submission for other modes -->
                                <xf:send submission="fr-edit-switch-script-submission"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xf:action>
                </xf:group>
            </xh:div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:goto-content">
        <xsl:if test="$is-noscript">
            <!-- Group to scope variables -->
            <xf:group appearance="xxf:internal" model="fr-error-summary-model">
                <!-- Link to form content or to errors if any -->
                <xh:a class="fr-goto-content" href="#{{if (errors-count castable as xs:integer and xs:integer(errors-count) > 0) then 'fr-errors' else 'fr-form'}}">
                    <xf:output value="$fr-resources/summary/labels/goto-content"/>
                </xh:a>
            </xf:group>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:version">
        <xsl:if test="not($has-version = false())">
            <fr:row>
                <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version"
                    name="orbeon-forms-version"
                    select="version:getVersionString()"
                    as="xs:string"/>
                <xh:div class="fr-orbeon-version">
                    <xsl:value-of select="$orbeon-forms-version"/>
                </xh:div>
            </fr:row>
        </xsl:if>
    </xsl:template>

    <!-- Content handled separately -->
    <xsl:template match="fr:dialogs"/>

    <xsl:template name="fr-dialogs">
        <!-- Copy custom dialogs under fr:dialogs only (other dialogs will be left in place) -->
        <xsl:apply-templates select=".//fr:dialogs//xxf:dialog"/>

        <!-- This model handles import/export -->
        <xsl:if test="$buttons = ('save-locally')">
            <xi:include href="oxf:/apps/fr/save-locally/save-locally-dialog.xml" xxi:omit-xml-base="true"/>
        </xsl:if>

        <!-- Misc standard dialogs -->
        <xi:include href="oxf:/apps/fr/includes/clear-dialog.xhtml" xxi:omit-xml-base="true"/>
        <xi:include href="oxf:/apps/fr/includes/submission-dialog.xhtml" xxi:omit-xml-base="true"/>

        <!-- Error dialog -->
        <fr:alert-dialog id="fr-error-dialog">
            <fr:label ref="$fr-resources/detail/messages/error-dialog-title"/>
            <fr:neutral-choice/>
        </fr:alert-dialog>
    </xsl:template>

    <!-- Noscript section help entry -->
    <xsl:template match="fr:section[@id]" mode="noscript-help">
        <!-- Assumption: help referred to by xf:help/@ref and XPath expression is independent on the control's context (case of Form Builder-generated forms) -->

        <!-- Only display this <li> if there is at least one non-empty nested help text -->
        <xf:group
            ref=".[normalize-space(string-join(({string-join(((descendant-or-self::fr:section | .//xf:*)[@id]/xf:help/@ref), ',')}), ''))]">
            <xh:li class="xforms-help-group">
                <xf:var name="section-has-help" value="normalize-space({xf:help/@ref}) != ''" as="xs:boolean"/>
                <!-- Case where current section has help -->
                <xf:group ref=".[$section-has-help]">
                    <!-- Linked reachable from help icon -->
                    <xh:a name="{@id}$$p"/>
                    <!-- Label and help text -->
                    <xf:output class="xforms-label" value="{xf:label/@ref}"/>: <xf:output value="{xf:help/@ref}"/>
                    <!-- Link back to control -->
                    <xh:a href="#{@id}" class="fr-help-back"><xf:output value="$fr-resources/summary/labels/help-back"/></xh:a>
                </xf:group>
                <!-- Case where current section doesn't have help -->
                <xf:group ref=".[not($section-has-help)]">
                    <!-- Label -->
                    <xf:output class="xforms-label" value="{xf:label/@ref}"/>
                </xf:group>
                <!-- Recurse into nested controls if there is at least one non-empty nested help text -->
                <xf:group ref=".[normalize-space(string-join(({string-join((.//(fr:section | xf:*)[@id]/xf:help/@ref), ',')}), ''))]">
                    <xh:ul>
                        <xsl:apply-templates mode="#current"/>
                    </xh:ul>
                </xf:group>
            </xh:li>
        </xf:group>
    </xsl:template>

    <!-- Noscript control help entry -->
    <xsl:template match="xf:*[@id and xf:help]" mode="noscript-help">
        <xf:group ref=".[normalize-space(({xf:help/@ref})[1])]">
            <!-- (...)[1] protects against incorrect form where more than one node is returned -->
            <xh:li class="xforms-help-group">
                <!-- Linked reachable from help icon -->
                <xh:a name="{@id}$$p"/>
                <!-- Label and help text -->
                <xf:output class="xforms-label" value="{xf:label/@ref}"/>: <xf:output value="{xf:help/@ref}"/>
                <!-- Link back to the control -->
                <xh:a href="#{@id}" class="fr-help-back"><xf:output value="$fr-resources/summary/labels/help-back"/></xh:a>
            </xh:li>
        </xf:group>
    </xsl:template>

    <xsl:template match="node()" mode="noscript-help">
        <xsl:apply-templates mode="#current"/>
    </xsl:template>

    <!-- Error summary UI -->
    <xsl:template match="fr:error-summary" name="fr-error-summary">
        <xsl:param name="position" select="@position" as="xs:string"/>

        <!-- NOTE: We used to only handle events coming from controls bound to "fr-form-instance" instance, but this
             doesn't work with "section templates". We now use the observer mechanism of fr:error-summary -->

        <!-- For form builder we disable the error summary and say that the form is always valid -->
        <xsl:if test="not($is-form-builder)">
            <fr:row>
                <fr:error-summary id="error-summary-control-{$position}" observer="fr-form-group fr-captcha-group" model="fr-error-summary-model"
                    errors-count-ref="errors-count" visible-errors-count-ref="visible-errors-count" valid-ref="valid">
                    <fr:label>
                        <xf:output value="$fr-resources/errors/summary-title"/>
                    </fr:label>
                    <xsl:if test="$position = 'bottom'">
                        <fr:header/>
                    </xsl:if>
                    <xsl:if test="$position = 'top'">
                        <fr:footer/>
                    </xsl:if>
                </fr:error-summary>
            </fr:row>
        </xsl:if>

    </xsl:template>

    <!-- Optional explanation message for view mode -->
    <xsl:template name="fr-explanation">
        <xsl:if test="$is-show-explanation and $mode = ('view')">
            <xh:div class="fr-explanation">
                <xf:output value="$fr-resources/detail/view/explanation"/>
            </xh:div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:status-icons" name="fr-status-icons">
        <!-- Status icons for detail page -->
        <xsl:if test="$is-detail">
            <xh:span class="fr-status-icons">
                <xf:group model="fr-error-summary-model" ref=".[valid = 'false']">
                    <!-- Form is invalid -->

                    <!-- Display localized errors count -->
                    <!--<xf:var name="message" as="xs:string" model="fr-error-summary-model"-->
                        <!--select="for $c in errors-count return-->
                                      <!--if ($c castable as xs:integer and xs:integer($c) > 0)-->
                                      <!--then concat($c, ' ', $fr-resources/summary/titles/(if (xs:integer($c) = 1) then error-count else errors-count))-->
                                      <!--else ''"/>-->

                    <xh:span class="badge badge-important"><xf:output value="visible-errors-count"/></xh:span>
                </xf:group>
                <xf:group model="fr-error-summary-model" ref=".[valid = 'true']" class="fr-validity-icon">
                    <!-- Form is valid -->
                    <xh:i class="icon-ok" title="{{$fr-resources/errors/none}}"/>
                </xf:group>
                <xf:group model="fr-persistence-model" ref="instance('fr-persistence-instance')[data-status = 'dirty']" class="fr-data-icon">
                    <!-- Data is dirty -->
                    <xh:i class="icon-hdd" title="{{$fr-resources/errors/unsaved}}"/>
                </xf:group>
            </xh:span>
        </xsl:if>
    </xsl:template>

    <!-- Success messages -->
    <xsl:template match="fr:messages" name="fr-messages">
        <xf:switch class="fr-messages" model="fr-persistence-model" ref=".[instance('fr-persistence-instance')/message != '']">
            <xf:case id="fr-message-none">
                <xh:span/>
            </xf:case>
            <xf:case id="fr-message-success">
                <xf:output value="instance('fr-persistence-instance')/message" class="fr-message-success alert alert-success"/>
            </xf:case>
        </xf:switch>
    </xsl:template>

    <xsl:template match="fr:bottom-bar">
        <xsl:apply-templates select="$default-bottom-template"/>
    </xsl:template>

    <xsl:template match="fr:buttons-bar" name="fr-buttons-bar">
        <!-- Buttons -->
        <xsl:if test="not($hide-buttons-bar)">
            <xh:span class="fr-buttons">
                <xsl:choose>
                    <!-- Use user-provided buttons -->
                    <xsl:when test="exists($custom-buttons)">
                        <!-- Copy all the content -->
                        <xsl:apply-templates select="$custom-buttons/node()"/>
                    </xsl:when>
                    <!-- Test mode -->
                    <xsl:when test="$mode = ('test')">
                        <xh:div class="fr-buttons-placeholder">
                            <xh:div>
                                <xf:output value="$fr-resources/detail/messages/buttons-placeholder"/>
                            </xh:div>
                        </xh:div>
                    </xsl:when>
                    <!-- In PDF mode, don't include anything -->
                    <xsl:when test="$mode = ('pdf')"/>
                    <!-- Use configured buttons -->
                    <xsl:otherwise>
                        <!-- Message shown next to the buttons -->
                        <xh:span class="fr-buttons-message">
                            <xf:output mediatype="text/html" ref="$fr-resources/detail/messages/buttons-message"/>
                        </xh:span>
                        <!-- List of buttons we include based on property -->
                        <xsl:variable name="configured-buttons" as="node()*">
                            <xsl:for-each select="$buttons">
                                <xsl:variable name="is-primary" select="position() = last()"/>
                                <xsl:element name="fr:{.}-button">
                                    <xsl:if test="$is-primary">
                                        <xsl:attribute name="appearance">xxf:primary</xsl:attribute>
                                    </xsl:if>
                                </xsl:element>
                                <xsl:text> </xsl:text>
                            </xsl:for-each>
                        </xsl:variable>
                        <xsl:apply-templates select="$configured-buttons"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xh:span>
        </xsl:if>
    </xsl:template>

    <!-- TOC: Top-level -->
    <xsl:template match="fr:toc" name="fr-toc">
        <!-- This is statically built in XSLT instead of using XForms -->
        <xsl:if test="$has-toc and $is-detail and not($is-form-builder) and count($body//fr:section) ge $min-toc">
            <xh:div class="fr-toc well sidebar-nav">
                <xh:ul class="nav nav-list">
                    <xh:li class="nav-header"><xf:output ref="$fr-resources/summary/titles/toc"/></xh:li>
                    <xsl:apply-templates select="$body" mode="fr-toc-sections"/>
                </xh:ul>
            </xh:div>
        </xsl:if>
    </xsl:template>

    <!-- TOC: Swallow unneeded nodes -->
    <xsl:template match="text()" mode="fr-toc-sections"/>

    <xsl:template match="*" mode="fr-toc-sections">
        <xsl:apply-templates mode="fr-toc-sections"/>
    </xsl:template>

    <!-- TOC: handle section -->
    <xsl:template match="fr:section" mode="fr-toc-sections">
        <xh:li xxf:control="xf:group">
            <!-- Propagate binding so that entry for section disappears if the section is non-relevant -->
            <xsl:copy-of select="@model | @context | @bind | @ref"/>
            <!-- Clicking sets the focus -->
            <xf:trigger appearance="minimal">
                <xf:label value="xxf:label('{@id}')"/>
                <xf:setfocus ev:event="DOMActivate" control="{@id}" input-only="true"/>
            </xf:trigger>
            <!-- Sub-sections if any -->
            <xsl:if test="exists(fr:section)">
                <xh:ol>
                    <xsl:apply-templates mode="fr-toc-sections"/>
                </xh:ol>
            </xsl:if>
        </xh:li>
    </xsl:template>

    <!-- Add a default xf:alert for those fields which don't have one. Only do this within grids and dialogs. -->
    <!-- Q: Do we really need this? -->
    <xsl:template
        match="xh:body//fr:grid//xf:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload') and not(xf:alert)]
                       | xh:body//xxf:dialog//xf:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload') and not(xf:alert)]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xf:alert ref="$fr-resources/detail/labels/alert"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
