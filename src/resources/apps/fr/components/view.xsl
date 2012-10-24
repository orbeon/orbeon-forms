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
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:exforms="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:formRunner="java:org.orbeon.oxf.fr.FormRunner">

    <xsl:variable name="view"           select="(/xhtml:html/xhtml:body/fr:view)[1]" as="element(fr:view)?"/>
    <xsl:variable name="body"           select="($view/fr:body, $view)[1]"           as="element()?"/>
    <xsl:variable name="custom-buttons" select="$view/fr:buttons"                    as="element()*"/>
    <xsl:variable name="custom-layout"  select="empty($body)"/>

    <!-- Template for the default layout of a form -->
    <xsl:variable name="default-page-template" as="element(*)*">
        <fr:navbar/>
        <xhtml:div class="container">

            <xhtml:p class="lead"><fr:description/></xhtml:p>

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

            <!-- Error summary (if at bottom) -->
            <!-- If we configuration tells us the bottom error summary should not be shown, still include it but hide it with 'display: none'.
                 This is necessary because the persistence model relies on the error summary to know if the data is valid. -->
            <xhtml:div>
                <xsl:if test="not($error-summary-bottom)">
                    <xsl:attribute name="style">display: none</xsl:attribute>
                </xsl:if>
                <fr:error-summary position="bottom"/>
            </xhtml:div>

            <fr:row>
                <fr:noscript-help/>
            </fr:row>
            <fr:row>
                <fr:messages/>
            </fr:row>
            <fr:bottom-bar/>
            <fr:row>
                <fr:version/>
            </fr:row>
        </xhtml:div>
    </xsl:variable>

    <!-- Template for the default layout of the bottom bar -->
    <xsl:variable name="default-bottom-template" as="element(*)*">
        <fr:status-icons/>
        <fr:buttons-bar/>
    </xsl:variable>

    <xsl:template match="fr:row">
        <xhtml:div class="row">
            <xhtml:div class="span12">
                <xsl:apply-templates/>
            </xhtml:div>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="fr:body">
        <!-- Form content. Set context on form instance and define this group as #fr-form-group as observers will refer to it. -->
        <xforms:group id="fr-form-group" class="fr-body{if ($is-detail) then ' fr-border' else ''}" model="fr-form-model" ref="instance('fr-form-instance')">
            <xhtml:a name="fr-form"/>
            <xsl:apply-templates select="if ($body) then $body/(node() except fr:buttons) else node()"/>
            <fr:captcha/>
        </xforms:group>
    </xsl:template>

    <!-- Main entry point -->
    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:attribute name="class" select="string-join((if ($is-inline-hints) then 'xforms-disable-hint-as-tooltip' else (), 'xforms-disable-alert-as-tooltip', @class), ' ')"/>
            <xsl:apply-templates select="@* except @class"/>
            <xforms:group model="fr-form-model" id="fr-view" class="fr-view{concat(' fr-mode-', $mode)}">
                <xsl:apply-templates select="if ($custom-layout) then node() else $default-page-template"/>
                <xsl:call-template name="fr-hidden-controls"/>
                <xsl:call-template name="fr-dialogs"/>
            </xforms:group>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="fr:navbar" name="fr-navbar">
        <xsl:variable name="header-classes" as="xs:string*" select="('navbar', 'navbar-inverse', 'navbar-fixed-top')"/>
        <xhtml:div class="{string-join($header-classes, ' ')}">
            <xhtml:div class="navbar-inner">
                <xhtml:div class="container">
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
                                    <fr:logo/>
                                    <fr:title/>
                                    <fr:language-selector/>
                                    <fr:noscript-selector/>
                                    <fr:goto-content/>
                                </xsl:variable>

                                <xsl:apply-templates select="$default-objects"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:if>
                </xhtml:div>
            </xhtml:div>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="fr:hidden-controls" name="fr-hidden-controls">
        <xhtml:span class="fr-hidden">
            <!-- Hidden field to communicate to the client whether the data is safe -->
            <xforms:input
                    model="fr-persistence-model"
                    ref="instance('fr-persistence-instance')/data-safe"
                    id="fr-data-safe-input"
                    class="xforms-disabled"/>

            <!-- Expose document id to JavaScript -->
            <xforms:output id="fr-parameters-instance-document" ref="xxforms:instance('fr-parameters-instance')/document" style="display: none"/>

            <!-- When the mode changes to "edit" after a save from /new, attempt to change the URL -->
            <xxforms:variable name="mode-for-save" select="xxforms:instance('fr-parameters-instance')/mode/string()">
                <!-- If URI is /new (it should be), change it to /edit/id -->
                <!-- If browser supporting the HTML5 history API (http://goo.gl/Ootqu) -->
                <xxforms:script ev:event="xforms-value-changed" if="$mode-for-save = 'edit'">
                    if (history &amp;&amp; history.replaceState) {
                        if (location.href.lastIndexOf("/new") == location.href.length - 4)
                            history.replaceState(null, "", "edit/" + ORBEON.xforms.Document.getValue("fr-parameters-instance-document"));
                    }
                </xxforms:script>
            </xxforms:variable>

            <!-- This is aHACK for Form Builder only: place non-relevant instances of all toolbox controls so that
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
                    <xforms:group ref="()">
                        <xsl:apply-templates select="$resources/*/xbl:binding/fb:metadata/(fb:template | fb:templates/fb:view)/*" mode="filter-fb-template"/>
                    </xforms:group>
                </xsl:if>
            </xsl:if>
        </xhtml:span>
    </xsl:template>

    <xsl:template match="fr:captcha" name="fr-captcha">
        <!-- Captcha -->
        <xforms:group appearance="xxforms:internal"  model="fr-persistence-model">
            <xxforms:variable name="captcha" model="fr-persistence-model" select="instance('fr-persistence-instance')/captcha"/>
            <xxforms:variable name="mode" select="xxforms:instance('fr-parameters-instance')/mode"/>
            <xsl:if test="$has-captcha">
                <xforms:group ref=".[$mode = ('new', 'edit') and not(property('xxforms:noscript')) and $captcha = 'false']" class="fr-captcha">
                    <!-- Captcha component: either reCAPTCHA or SimpleCaptcha -->
                    <xforms:group appearance="xxforms:internal">
                        <!-- Success: remember the captcha passed, which also influences validity -->
                        <xforms:action ev:event="fr-verify-done">
                            <xforms:setvalue ref="$captcha">true</xforms:setvalue>
                            <xforms:revalidate model="fr-persistence-model"/>
                            <xforms:refresh/>
                        </xforms:action>
                        <!-- Failure: load another challenge (supported by reCAPTCHA; SimpleCaptcha won't do anything) -->
                        <xforms:dispatch ev:event="fr-verify-error" if="event('fr-error-code') != 'empty'" target="captcha" name="fr-reload"/>
                        <xsl:if test="$captcha = 'reCAPTCHA'">
                            <fr:recaptcha id="captcha" theme="clean"/>
                        </xsl:if>
                        <xsl:if test="$captcha = 'SimpleCaptcha'">
                            <fr:simple-captcha id="captcha"/>
                        </xsl:if>
                    </xforms:group>
                    <!-- Non-visible output bound to captcha node to influence form validity -->
                    <xhtml:span style="display: none">
                        <xforms:output ref="$captcha">
                            <!-- Focus from error summary proxies to captcha -->
                            <xforms:setfocus ev:event="xforms-focus" control="captcha"/>
                            <xforms:label ref="$fr-resources/detail/labels/captcha-label"/>
                            <xforms:alert ref="$fr-resources/detail/labels/captcha-help"/>
                        </xforms:output>
                    </xhtml:span>
                </xforms:group>
            </xsl:if>
        </xforms:group>
    </xsl:template>

    <xsl:template match="fr:noscript-help" name="fr-noscript-help">
        <!-- Noscript help section (shown only in edit mode) -->
        <xsl:if test="$is-noscript and $mode = ('edit', 'new')">
            <!-- Only display this section if there is at least one non-empty nested help text -->
            <xforms:group
                ref=".[normalize-space(string-join(({string-join(($body//(fr:section | xforms:*)[@id]/xforms:help/@ref), ',')}), ''))]">
                <xhtml:div class="xforms-help-panel">
                    <xhtml:h2>
                        <xforms:output value="$fr-resources/summary/titles/help"/>
                    </xhtml:h2>
                    <xhtml:ul>
                        <xsl:apply-templates select="$body/*" mode="noscript-help"/>
                    </xhtml:ul>
                </xhtml:div>
            </xforms:group>
        </xsl:if>
    </xsl:template>

    <!-- Remove id elements on Form Builder templates -->
    <xsl:template match="@id | @bind" mode="filter-fb-template"/>
    <xsl:template match="@ref[not(normalize-space())] | @nodeset[not(normalize-space())]" mode="filter-fb-template">
        <xsl:attribute name="{name()}" select="'()'"/>
    </xsl:template>

    <xsl:template match="fr:title" name="fr-title">
        <!-- Q: Why do we need @ref here? -->
        <xforms:output value="{if (exists(@ref)) then @ref else '$title'}" class="brand"/>
    </xsl:template>

    <!-- Description in chosen language or first one if not found -->
    <xsl:template match="fr:description" name="fr-description">
        <xforms:var
            name="description"
            value="({if (@paths) then concat(@paths, ', ') else ''}xxforms:instance('fr-form-metadata')/description[@xml:lang = xxforms:instance('fr-language-instance')],
                        xxforms:instance('fr-form-metadata')/description)[normalize-space()][1]"/>

        <xhtml:p>
            <xforms:output
                class="fr-form-description"
                model="fr-form-model"
                value="$description"/>
        </xhtml:p>
    </xsl:template>

    <xsl:template match="fr:logo">
        <xsl:if test="not($hide-logo)">

            <xxforms:variable name="logo-uri"
                select="({if (@paths) then concat(@paths, ', ') else ''}xxforms:instance('fr-form-metadata')/logo,
                         '{$default-logo-uri}')[normalize-space()][1]"/>

            <!-- If image comes from resources, use an img tag so we can serve GIF for IE6 -->
            <xxforms:variable name="is-logo-in-resources" select="starts-with($logo-uri, 'oxf:')"/>
            <xhtml:span class="fr-logo">
                <xforms:group ref=".[$is-logo-in-resources]">
                    <xhtml:img src="{{substring-after($logo-uri, 'oxf:')}}" alt=""/>
                </xforms:group>
                <xforms:group ref=".[exists($logo-uri) and not($is-logo-in-resources)]">
                    <xforms:output value="$logo-uri" mediatype="image/*"/>
                </xforms:group>
            </xhtml:span>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:language-selector">
        <!-- Switch language -->
        <xhtml:div class="fr-language-choice">
            <!-- Put default language first, then other languages -->
            <xxforms:variable
                name="available-languages"
                model="fr-resources-model"
                value="formRunner:getFormLangSelection($app, $form, xxforms:instance('fr-form-resources')/resource/@xml:lang/string())"/>

            <!-- Don't display language selector if there is only one language -->
            <!-- NOTE: Resolve model here, as for now model within XBL component won't resolve -->
            <xforms:group id="fr-language-selector" model="fr-resources-model" ref=".[count($available-languages) gt 1]">
                <fr:link-select1 ref="instance('fr-language-instance')">
                    <xforms:itemset nodeset="$available-languages">
                        <xforms:label ref="(xxforms:instance('fr-languages-instance')/language[@code = context()]/@native-name, context())[1]"/>
                        <xforms:value ref="context()"/>
                    </xforms:itemset>
                </fr:link-select1>
            </xforms:group>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="fr:noscript-selector">
        <!-- Switch script/noscript -->
        <xsl:if test="$mode = ('edit', 'new') and not($has-noscript-link = false()) and not($is-form-builder) and $is-noscript-support">
            <xhtml:div class="fr-noscript-choice">
                <xforms:group appearance="xxforms:internal">
                    <xforms:group ref=".[not(property('xxforms:noscript'))]">
                        <xforms:trigger appearance="minimal">
                            <xforms:label>
                                <xforms:output value="$fr-resources/summary/labels/noscript"/>
                            </xforms:label>
                        </xforms:trigger>
                        <!--<xhtml:img class="fr-noscript-icon" width="16" height="16" src="/apps/fr/style/images/silk/script_delete.png" alt="Noscript Mode" title="Noscript Mode"/>-->
                    </xforms:group>
                    <xforms:group ref=".[property('xxforms:noscript')]">
                        <xforms:trigger appearance="minimal">
                            <xforms:label>
                                <xforms:output value="$fr-resources/summary/labels/script"/>
                            </xforms:label>
                        </xforms:trigger>
                    </xforms:group>
                    <!-- React to activation of both triggers -->
                    <xforms:action ev:event="DOMActivate">
                        <!-- Send submission -->
                        <xsl:choose>
                            <xsl:when test="$mode = 'summary'">
                                <!-- Submission for summary mode -->
                                <xforms:send submission="fr-edit-switch-script-summary-submission"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Submission for other modes -->
                                <xforms:send submission="fr-edit-switch-script-submission"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xforms:action>
                </xforms:group>
            </xhtml:div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:goto-content">
        <!-- Go to content link in noscript mode -->
        <xsl:if test="$is-noscript">
            <xhtml:div class="fr-goto-content">
                <!-- Group to scope variables -->
                <xforms:group appearance="xxforms:internal" model="fr-error-summary-model">
                    <!-- Link to form content or to errors if any -->
                    <xhtml:a href="#{{if (errors-count castable as xs:integer and xs:integer(errors-count) > 0) then 'fr-errors' else 'fr-form'}}">
                        <xforms:output value="$fr-resources/summary/labels/goto-content"/>
                    </xhtml:a>
                </xforms:group>
            </xhtml:div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:version">
        <xsl:if test="not($has-version = false())">
            <fr:row>
                <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version"
                    name="orbeon-forms-version"
                    select="version:getVersionString()"
                    as="xs:string"/>
                <xhtml:div class="fr-orbeon-version">
                    <xsl:value-of select="$orbeon-forms-version"/>
                </xhtml:div>
            </fr:row>
        </xsl:if>
    </xsl:template>

    <!-- Content handled separately -->
    <xsl:template match="fr:dialogs"/>

    <xsl:template name="fr-dialogs">
        <!-- Copy custom dialogs under fr:dialogs only (other dialogs will be left in place) -->
        <xsl:apply-templates select=".//fr:dialogs//xxforms:dialog"/>

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
        <!-- Assumption: help referred to by xforms:help/@ref and XPath expression is independent on the control's context (case of Form Builder-generated forms) -->

        <!-- Only display this <li> if there is at least one non-empty nested help text -->
        <xforms:group
            ref=".[normalize-space(string-join(({string-join(((descendant-or-self::fr:section | .//xforms:*)[@id]/xforms:help/@ref), ',')}), ''))]">
            <xhtml:li class="xforms-help-group">
                <xxforms:variable name="section-has-help" select="normalize-space({xforms:help/@ref}) != ''" as="xs:boolean"/>
                <!-- Case where current section has help -->
                <xforms:group ref=".[$section-has-help]">
                    <!-- Linked reachable from help icon -->
                    <xhtml:a name="{@id}$$p"/>
                    <!-- Label and help text -->
                    <xforms:output class="xforms-label" value="{xforms:label/@ref}"/>: <xforms:output value="{xforms:help/@ref}"/>
                    <!-- Link back to control -->
                    <xhtml:a href="#{@id}" class="fr-help-back"><xforms:output value="$fr-resources/summary/labels/help-back"/></xhtml:a>
                </xforms:group>
                <!-- Case where current section doesn't have help -->
                <xforms:group ref=".[not($section-has-help)]">
                    <!-- Label -->
                    <xforms:output class="xforms-label" value="{xforms:label/@ref}"/>
                </xforms:group>
                <!-- Recurse into nested controls if there is at least one non-empty nested help text -->
                <xforms:group ref=".[normalize-space(string-join(({string-join((.//(fr:section | xforms:*)[@id]/xforms:help/@ref), ',')}), ''))]">
                    <xhtml:ul>
                        <xsl:apply-templates mode="#current"/>
                    </xhtml:ul>
                </xforms:group>
            </xhtml:li>
        </xforms:group>
    </xsl:template>

    <!-- Noscript control help entry -->
    <xsl:template match="xforms:*[@id and xforms:help]" mode="noscript-help">
        <xforms:group ref=".[normalize-space(({xforms:help/@ref})[1])]">
            <!-- (...)[1] protects against incorrect form where more than one node is returned -->
            <xhtml:li class="xforms-help-group">
                <!-- Linked reachable from help icon -->
                <xhtml:a name="{@id}$$p"/>
                <!-- Label and help text -->
                <xforms:output class="xforms-label" value="{xforms:label/@ref}"/>: <xforms:output value="{xforms:help/@ref}"/>
                <!-- Link back to the control -->
                <xhtml:a href="#{@id}" class="fr-help-back"><xforms:output value="$fr-resources/summary/labels/help-back"/></xhtml:a>
            </xhtml:li>
        </xforms:group>
    </xsl:template>

    <xsl:template match="node()" mode="noscript-help">
        <xsl:apply-templates mode="#current"/>
    </xsl:template>

    <!-- Error summary UI -->
    <xsl:template match="fr:error-summary" name="fr-error-summary">
        <xsl:param name="position" select="@position" as="xs:string"/>

        <!-- Handle "control visited" events on #fr-form-group -->
        <!-- NOTE: We used to only handle events coming from controls bound to "fr-form-instance" instance, but this
             doesn't work with "section templates". We now use the observer mechanism of fr:error-summary -->
        <xsl:if test="not($is-form-builder)">
            <fr:row>
                <!-- For form builder we disable the error summary and say that the form is always valid -->
                <fr:error-summary id="error-summary-control-{$position}" observer="fr-form-group" model="fr-error-summary-model"
                    errors-count-ref="errors-count" visible-errors-count-ref="visible-errors-count" valid-ref="valid">
                    <fr:label>
                        <xforms:output value="$fr-resources/errors/summary-title"/>
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
            <xhtml:div class="fr-explanation">
                <xforms:output value="$fr-resources/detail/view/explanation"/>
            </xhtml:div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:status-icons" name="fr-status-icons">
        <!-- Status icons for detail page -->
        <xsl:if test="$is-detail">
            <xhtml:div class="fr-status-icons">
                <xforms:group model="fr-error-summary-model" ref=".[valid = 'false']">
                    <!-- Form is invalid -->

                    <!-- Display localized errors count -->
                    <xxforms:variable name="message" as="xs:string" model="fr-error-summary-model"
                        select="for $c in errors-count return
                                      if ($c castable as xs:integer and xs:integer($c) > 0)
                                      then concat($c, ' ', $fr-resources/summary/titles/(if (xs:integer($c) = 1) then error-count else errors-count))
                                      else ''"/>

                    <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/exclamation.png" alt="{{$fr-resources/errors/some}}" title="{{$fr-resources/errors/some}}"/>-->
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/warning_16.png" alt="{{$message}}" title="{{$message}}"/>
                </xforms:group>
                <xforms:group model="fr-error-summary-model" ref=".[valid = 'true']" class="fr-validity-icon">
                    <!-- Form is valid -->
                    <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/tick.png" alt="{{$fr-resources/errors/none}}" title="{{$fr-resources/errors/none}}"/>-->
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/tick_16.png" alt="{{$fr-resources/errors/none}}"
                        title="{{$fr-resources/errors/none}}"/>
                </xforms:group>
                <xforms:group model="fr-persistence-model" ref="instance('fr-persistence-instance')[data-status = 'dirty']" class="fr-data-icon">
                    <!-- Data is dirty -->
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/disk.png" alt="{{$fr-resources/errors/unsaved}}"
                        title="{{$fr-resources/errors/unsaved}}"/>
                    <!--<xhtml:img width="16" height="16" src="/apps/fr/style/images/pixelmixer/save_16.png" alt="{{$fr-resources/errors/unsaved}}" title="{{$fr-resources/errors/unsaved}}"/>-->
                </xforms:group>
            </xhtml:div>
        </xsl:if>
    </xsl:template>

    <!-- Success messages -->
    <xsl:template match="fr:messages" name="fr-messages">
        <xforms:switch class="fr-messages" model="fr-persistence-model" ref=".[instance('fr-persistence-instance')/message != '']">
            <xforms:case id="fr-message-none">
                <xhtml:span/>
            </xforms:case>
            <xforms:case id="fr-message-success">
                <xforms:output value="instance('fr-persistence-instance')/message" class="fr-message-success alert alert-success"/>
            </xforms:case>
        </xforms:switch>
    </xsl:template>

    <xsl:template match="fr:bottom-bar">
        <fr:row>
            <xsl:apply-templates select="$default-bottom-template"/>
        </fr:row>
    </xsl:template>

    <xsl:template match="fr:buttons-bar" name="fr-buttons-bar">
        <!-- Buttons -->
        <xsl:if test="not($hide-buttons-bar)">
            <xhtml:div class="fr-buttons">
                <xsl:choose>
                    <!-- Use user-provided buttons -->
                    <xsl:when test="exists($custom-buttons)">
                        <!-- Copy all the content -->
                        <xsl:apply-templates select="$custom-buttons/node()"/>
                    </xsl:when>
                    <!-- Test mode -->
                    <xsl:when test="$mode = ('test')">
                        <xhtml:div class="fr-buttons-placeholder">
                            <xhtml:div>
                                <xforms:output value="$fr-resources/detail/messages/buttons-placeholder"/>
                            </xhtml:div>
                        </xhtml:div>
                    </xsl:when>
                    <!-- In PDF mode, don't include anything -->
                    <xsl:when test="$mode = ('pdf')"/>
                    <!-- Use default buttons -->
                    <xsl:otherwise>
                        <!-- Message shown next to the buttons -->
                        <xhtml:div class="fr-buttons-message">
                            <xforms:output mediatype="text/html" ref="$fr-resources/detail/messages/buttons-message"/>
                        </xhtml:div>
                        <!-- List of buttons we include based on property -->
                        <xsl:variable name="default-buttons" as="node()*">
                            <xsl:for-each select="$buttons">
                                <xsl:variable name="is-primary" select="position() = last()"/>
                                <xsl:element name="fr:{.}-button">
                                    <xsl:if test="$is-primary">
                                        <xsl:attribute name="appearance">xxforms:primary</xsl:attribute>
                                    </xsl:if>
                                </xsl:element>
                                <xsl:text> </xsl:text>
                            </xsl:for-each>
                        </xsl:variable>
                        <xsl:apply-templates select="$default-buttons"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xhtml:div>
        </xsl:if>
    </xsl:template>

    <!-- TOC: Top-level -->
    <xsl:template match="fr:toc" name="fr-toc">
        <!-- This is statically built in XSLT instead of using XForms -->
        <xsl:if test="$has-toc and $is-detail and not($is-form-builder) and count($body//fr:section) ge $min-toc">
            <xhtml:div class="fr-toc well sidebar-nav">
                <xhtml:ul class="nav nav-list">
                    <xhtml:li class="nav-header"><xforms:output ref="$fr-resources/summary/titles/toc"/></xhtml:li>
                    <xsl:apply-templates select="$body" mode="fr-toc-sections"/>
                </xhtml:ul>
            </xhtml:div>
        </xsl:if>
    </xsl:template>

    <!-- TOC: Swallow unneeded nodes -->
    <xsl:template match="text()" mode="fr-toc-sections"/>

    <xsl:template match="*" mode="fr-toc-sections">
        <xsl:apply-templates mode="fr-toc-sections"/>
    </xsl:template>

    <!-- TOC: handle section -->
    <xsl:template match="fr:section" mode="fr-toc-sections">
        <xhtml:li xxforms:control="xforms:group">
            <!-- Propagate binding so that entry for section disappears if the section is non-relevant -->
            <xsl:copy-of select="@model | @context | @bind | @ref"/>
            <!-- Clicking sets the focus -->
            <xforms:trigger appearance="minimal">
                <xforms:label value="xxforms:label('{@id}')"/>
                <xforms:setfocus ev:event="DOMActivate" control="{@id}" input-only="true"/>
            </xforms:trigger>
            <!-- Sub-sections if any -->
            <xsl:if test="exists(fr:section)">
                <xhtml:ol>
                    <xsl:apply-templates mode="fr-toc-sections"/>
                </xhtml:ol>
            </xsl:if>
        </xhtml:li>
    </xsl:template>

    <!-- Add a default xforms:alert for those fields which don't have one. Only do this within grids and dialogs. -->
    <!-- Q: Do we really need this? -->
    <xsl:template
        match="xhtml:body//fr:grid//xforms:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload') and not(xforms:alert)]
                       | xhtml:body//xxforms:dialog//xforms:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload') and not(xforms:alert)]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xforms:alert ref="$fr-resources/detail/labels/alert"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
