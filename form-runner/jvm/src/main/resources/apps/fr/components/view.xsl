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
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:map="http://www.w3.org/2005/xpath-functions/map"
        xmlns:array="http://www.w3.org/2005/xpath-functions/array"
        xmlns:d="DAV:">

    <xsl:variable name="metadata"       select="if ($is-detail) then frf:metadataInstanceRootOpt($fr-form-model) else ()"/>
    <xsl:variable name="page-layout"    select="if ($is-detail) then frf:optionFromMetadataOrPropertiesXPath($metadata, 'html-page-layout', $app, $form, $mode) else ()"/>

    <xsl:variable name="view"                 select="(/xh:html/xh:body/fr:view)[1]"                   as="element(fr:view)?"/>
    <xsl:variable name="fluid"                select="$view/@fluid = 'true' or $page-layout = 'fluid'" as="xs:boolean"/>
    <xsl:variable name="body"                 select="($view/fr:body, $view)[1]"                       as="element()?"/>
    <xsl:variable name="custom-buttons"       select="$view/fr:buttons"                                as="element()*"/>
    <xsl:variable name="custom-inner-buttons" select="$view/fr:inner-buttons"                          as="element()*"/>

    <!-- Template for the default layout of a form -->
    <xsl:variable name="default-page-template" as="element(*)*">
        <fr:navbar/>

        <fr:description/>

        <!-- Error summary (if at top) -->
        <xsl:if test="$error-summary-top">
            <fr:error-summary position="top"/>
        </xsl:if>

        <xsl:if test="$allow-revision-history">
            <fr:revision-history
                id="fr-revision-history"
                ref=".[fr:mode() = ('edit', 'view')]"
                app="{{fr:app-name()}}"
                form="{{fr:form-name()}}"
                form-version="{{fr:form-version()}}"
                document="{{fr:document-id()}}"/>
        </xsl:if>
        <fr:row>
            <fr:top-messages/>
        </fr:row>
        <fr:row class="fr-toc-with-body fr-toc-position-{$toc-position-opt}">
            <fr:toc/>
            <fr:body/>
        </fr:row>
        <xsl:if test="p:property(string-join(('oxf.fr.detail.captcha.location', $app, $form), '.')) = 'form-bottom'">
            <fr:row>
                <fr:captcha id="fr-captcha" namespace-name="{frf:captchaComponent($app, $form)}"/>
            </fr:row>
        </xsl:if>

        <!-- Error summary (if at bottom) -->
        <!-- If the configuration tells us the bottom error summary should not be shown, still include it but hide it with 'display: none'.
             This is necessary because the persistence model relies on the error summary to know if the data is valid. -->
        <xh:div>
            <xsl:if test="not($error-summary-bottom)">
                <xsl:attribute name="class">xforms-hidden</xsl:attribute>
            </xsl:if>
            <fr:error-summary position="bottom"/>
        </xh:div>

        <fr:row>
            <fr:messages/>
        </fr:row>
        <fr:row>
            <fr:version/>
        </fr:row>
        <fr:template-buttons-bar/>
        <fr:pdf-header-footer/>
    </xsl:variable>

    <xsl:template match="fr:row">
        <xh:div class="row{if ($fluid) then '-fluid' else '', @class}">
            <xh:div class="span12">
                <xsl:apply-templates select="node()"/>
            </xh:div>
        </xh:div>
    </xsl:template>

    <xsl:template match="fr:top-messages">

        <!-- Lease message -->
        <xf:group
            ref="if ($_fr-lease-enabled) then . else ()"
            class="alert alert-info fr-top-alert"
            xxf:element="div"
        >
            <xh:i class="fa fa-lock" aria-hidden="true"/>

            <fr:alert-dialog id="fr-lease-renew-dialog">
                <fr:label ref="$fr-resources/detail/lease/renew-lease-title"/>
                <fr:message value="$fr-resources/detail/lease/renew-lease-message"/>
                <fr:negative-choice/>
                <fr:positive-choice>
                    <xf:send event="DOMActivate" submission="fr-acquire-lease-submission"/>
                </fr:positive-choice>
            </fr:alert-dialog>

            <xh:div>
                <xf:switch caseref="$_fr-lease-state">
                    <xf:case value="'current-user'">
                        <xh:div>
                            <xf:output value="$fr-resources/detail/lease/current-user-left"/>
                            <!-- Make `fr:countdown` non-relevant if this case isn't shown, as it expects a valid `lease-end-time` -->
                            <xf:var name="countdown-relevant" value="$_fr-lease-state = 'current-user'"/>
                            <fr:countdown
                                ref="$_fr-persistence-instance/lease-end-time[$countdown-relevant]"
                                alert-threshold-ref="$_fr-persistence-instance/lease-alert-threshold">
                                <xf:action
                                    event="fr-countdown-ended"
                                    if="$_fr-lease-state = 'current-user'">
                                    <xf:dispatch
                                        target="fr-lease-renew-dialog"
                                        name="fr-hide"/>
                                    <xf:setvalue
                                        ref="$_fr-lease-state"
                                        value="'relinquished'"/>
                                </xf:action>
                                <xf:action
                                    event="fr-countdown-alert"
                                    if="$_fr-lease-state = 'current-user'">
                                    <xf:dispatch
                                        target="fr-lease-renew-dialog"
                                        name="fr-show"/>
                                </xf:action>
                            </fr:countdown>
                            <xf:output value="$fr-resources/detail/lease/current-user-right"/>
                        </xh:div>
                    </xf:case>
                    <xf:case value="'relinquished'">
                        <xf:output value="$fr-resources/detail/lease/relinquished"/>
                    </xf:case>
                    <xf:case value="'other-user'">
                        <xf:output value="
                            xxf:format-message(
                                $fr-resources/detail/lease/other-user,
                                xxf:instance('fr-lockinfo-response')/d:owner/fr:username/string()
                            )
                        "/>
                    </xf:case>
                </xf:switch>
                <xh:div class="fr-top-alert-buttons">
                    <xf:switch caseref="
                            if ($_fr-lease-state = 'current-user')
                            then 'has-lease'
                            else 'does-not-have-lease'">
                        <xf:case value="'has-lease'">
                            <xf:trigger class="xforms-trigger-appearance-modal">
                                <xf:label ref="$fr-resources/detail/lease/relinquish"/>
                                <xf:action event="DOMActivate">
                                    <xf:setvalue event="DOMActivate" ref="$_fr-persistence-instance/found-document-message-to-show"/>
                                    <xf:action type="xpath">
                                        xxf:instance('fr-form-instance')/fr:run-process-by-name('oxf.fr.detail.process', 'relinquish-lease')
                                    </xf:action>
                                </xf:action>
                            </xf:trigger>
                            <xf:trigger class="xforms-trigger-appearance-modal">
                                <xf:label ref="$fr-resources/detail/lease/renew"/>
                                <xf:action event="DOMActivate">
                                    <xf:send submission="fr-acquire-lease-submission"/>
                                </xf:action>
                            </xf:trigger>
                        </xf:case>
                        <xf:case value="'does-not-have-lease'">
                            <xf:trigger class="xforms-trigger-appearance-modal">
                                <xf:label ref="$fr-resources/detail/lease/try-acquire"/>
                                <xf:action event="DOMActivate">
                                    <xf:setvalue ref="$_fr-persistence-instance/lease-load-document">true</xf:setvalue>
                                    <xf:send submission="fr-acquire-lease-submission"/>
                                </xf:action>
                            </xf:trigger>
                            <xf:var name="show-in-view-mode" value="
                                xxf:evaluate(
                                    xxf:property(
                                        string-join(
                                            (
                                                'oxf.fr.detail.button.lease.show-in-view-mode.visible',
                                                fr:app-name(),
                                                fr:form-name()
                                            ),
                                            '.'
                                        )
                                    )
                                )"/>
                            <xf:trigger ref=".[$show-in-view-mode]">
                                <xf:label ref="$fr-resources/detail/lease/show-in-view-mode"/>
                                <xf:action event="DOMActivate" type="xpath">
                                    fr:run-process-by-name('oxf.fr.detail.process', 'lease-view')
                                </xf:action>
                            </xf:trigger>
                        </xf:case>
                    </xf:switch>
                </xh:div>
            </xh:div>
        </xf:group>

        <!-- Availability message -->
        <xf:group
            ref="if ($_fr-document-available-too-early-or-late) then . else ()"
            class="alert alert-info fr-top-alert"
            xxf:element="div">

            <xf:output
                mediatype="text/html"
                value="
                    if   ($_fr-document-available-too-early)
                    then ($_fr-document-available-from-elem-opt/message/string(), xxf:r('detail.available-from.message', '|fr-fr-resources|'))[1]
                    else ($_fr-document-available-to-elem-opt  /message/string(), xxf:r('detail.available-to.message'  , '|fr-fr-resources|'))[1]
                "/>
        </xf:group>

        <!-- Found document messages -->
        <xf:group
            ref="if ($_fr-persistence-instance/found-document-message-to-show != '') then . else ()"
            class="alert alert-info fr-top-alert"
            xxf:element="div"
        >
            <xh:i class="fa fa-file-o" aria-hidden="true"/>
            <xf:var
                name="form-version-param"
                model="fr-persistence-model"
                value="concat('form-version=', $form-version)"/>
            <xf:switch caseref="$_fr-persistence-instance/found-document-message-to-show">
                <xf:case value="'found-draft-for-document'">
                    <xf:output value="$fr-resources/detail/draft-singleton/found-draft-for-document"/>
                    <xh:div class="fr-top-alert-buttons">
                        <xf:group>
                            <xf:action event="DOMActivate">
                                <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-before-data"/>
                                <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-after-data"/>
                                <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-after-controls"/>
                                <xf:setvalue ref="$_fr-persistence-instance/found-document-message-to-show"/>
                            </xf:action>
                            <xf:trigger>
                                <xf:label value="$fr-resources/detail/draft-singleton/open-saved"/>
                                <xf:send event="DOMActivate" submission="fr-get-document-submission">
                                    <xf:property name="data-or-draft" value="'data'"/>
                                </xf:send>
                            </xf:trigger>
                            <xf:trigger>
                                <xf:label value="$fr-resources/detail/draft-singleton/open-draft"/>
                                <xf:send event="DOMActivate" submission="fr-get-document-submission">
                                    <xf:property name="data-or-draft" value="'draft'"/>
                                </xf:send>
                            </xf:trigger>
                        </xf:group>
                    </xh:div>
                </xf:case>
                <xf:case value="'found-draft-for-never-saved'">
                    <xf:output value="$fr-resources/detail/draft-singleton/found-draft-for-never-saved"/>
                    <xh:div class="fr-top-alert-buttons">
                        <xf:group>
                            <xf:action event="DOMActivate">
                                <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-before-data"/>
                                <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-after-data"/>
                                <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-after-controls"/>
                                <xf:setvalue ref="$_fr-persistence-instance/found-document-message-to-show"/>
                            </xf:action>
                            <xf:trigger>
                                <xf:label value="$fr-resources/detail/draft-singleton/start-new"/>
                                <xf:action event="DOMActivate">
                                    <xf:setvalue
                                        ref="instance('fr-authorized-operations')"
                                        value="
                                            frf:authorizedOperationsForDetailModeOrThrow(
                                                '', (: No permissions from data :)
                                                (), (: No mode change to `new`  :)
                                                xxf:instance('fr-form-metadata')/permissions,
                                                false()
                                            )"/>
                                </xf:action>
                            </xf:trigger>
                            <xf:trigger>
                                <xf:label value="$fr-resources/detail/draft-singleton/open-draft"/>
                                <xf:action event="DOMActivate">
                                    <xf:setvalue ref="xxf:instance('fr-parameters-instance')/document" value="xxf:instance('fr-search-response')/document/@name"/>
                                    <xf:setvalue ref="xxf:instance('fr-parameters-instance')/draft">true</xf:setvalue>
                                    <xf:send submission="fr-get-document-submission">
                                        <xf:property name="data-or-draft" value="'draft'"/>
                                    </xf:send>
                                    <xf:action type="xpath">fr:run-process-by-name('oxf.fr.detail.process', 'new-to-edit')</xf:action>
                                </xf:action>
                            </xf:trigger>
                        </xf:group>
                    </xh:div>
                </xf:case>
                <xf:case value="'found-drafts-for-never-saved'">
                    <xf:output value="$fr-resources/detail/draft-singleton/found-drafts-for-never-saved"/>
                    <xh:div class="fr-top-alert-buttons">
                        <xf:group>
                            <xf:trigger>
                                <xf:label value="$fr-resources/detail/draft-singleton/start-new"/>
                                <xf:action event="DOMActivate">
                                    <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-before-data"/>
                                    <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-after-data"/>
                                    <xf:dispatch targetid="fr-form-model" name="fr-run-form-load-action-after-controls"/>
                                    <xf:setvalue ref="$_fr-persistence-instance/found-document-message-to-show"/>
                                </xf:action>
                            </xf:trigger>
                            <xf:trigger xxf:modal="true">
                                <xf:label value="$fr-resources/detail/draft-singleton/view-drafts"/>
                                <xf:load
                                    event="DOMActivate"
                                    model="fr-persistence-model"
                                    resource="/fr/{$app}/{$form}/summary?drafts-for-never-saved-document=true{{
                                        if (xxf:non-blank($form-version-param)) then '&amp;' else ''}}{{
                                        $form-version-param}}"/>
                            </xf:trigger>
                        </xf:group>
                    </xh:div>
                </xf:case>
                <xf:case value="'found-multiple-docs-for-singleton'">
                    <xf:output value="$fr-resources/detail/draft-singleton/multiple-docs-explanation"/>
                    <xh:div class="fr-top-alert-buttons">
                        <xf:group>
                            <xf:trigger xxf:modal="true">
                                <xf:label value="$fr-resources/detail/draft-singleton/multiple-docs-view-data"/>
                                <xf:load
                                    event="DOMActivate"
                                    model="fr-persistence-model"
                                    resource="/fr/{$app}/{$form}/summary{{
                                        if (xxf:non-blank($form-version-param)) then '?' else ''}}{{
                                        $form-version-param}}"/>
                            </xf:trigger>
                        </xf:group>
                    </xh:div>
                </xf:case>
            </xf:switch>
        </xf:group>

    </xsl:template>

    <xsl:template name="fr-detail-page-global-variables">

        <xf:var name="_fr-persistence-instance"                 as="element('_')"               value="xxf:instance('fr-persistence-instance')"/>
        <xf:var name="_fr-lease-enabled"                        as="xs:boolean"                 value="$_fr-persistence-instance/lease-enabled = 'true'"/>
        <xf:var name="_fr-lease-state"                          as="xs:string"                  value="$_fr-persistence-instance/lease-state/string()"/>
        <xf:var name="_fr-document-available-from-elem-opt"     as="element('available-from')?" value="xxf:instance('fr-form-metadata')/available-from"/>
        <xf:var name="_fr-document-available-to-elem-opt"       as="element('available-to')?"   value="xxf:instance('fr-form-metadata')/available-to"/>
        <xf:var name="_fr-document-available-from-dateTime-opt" as="xs:string?"                 value="if (exists($_fr-document-available-from-elem-opt))
                                                                                                       then $_fr-document-available-from-elem-opt/@dateTime/string()
                                                                                                       else xxf:property(string-join(('oxf.fr.detail.available-from.dateTime', fr:app-name(), fr:form-name()), '.'))"/>
        <xf:var name="_fr-document-available-to-dateTime-opt"   as="xs:string?"                 value="if (exists($_fr-document-available-to-elem-opt))
                                                                                                       then $_fr-document-available-to-elem-opt/@dateTime/string()
                                                                                                       else xxf:property(string-join(('oxf.fr.detail.available-to.dateTime', fr:app-name(), fr:form-name()), '.'))"/>
        <xf:var name="_fr-document-available-too-early"         as="xs:boolean"                 value="xxf:non-blank($_fr-document-available-from-dateTime-opt) and
                                                                                                       current-dateTime() lt xs:dateTime($_fr-document-available-from-dateTime-opt)"/>
        <xf:var name="_fr-document-available-too-late"          as="xs:boolean"                 value="xxf:non-blank($_fr-document-available-to-dateTime-opt) and
                                                                                                       current-dateTime() gt xs:dateTime($_fr-document-available-to-dateTime-opt)"/>
        <xf:var name="_fr-document-available-too-early-or-late" as="xs:boolean"                 value="$_fr-document-available-too-early or $_fr-document-available-too-late"/>

        <xf:var name="_fr-show-form-data"
                as="xs:boolean"
                value="
                    (: No draft message is showing :)
                    $_fr-persistence-instance/found-document-message-to-show = '' and
                    (: Either we don't need a lease or we have the lease :)
                    (not($_fr-lease-enabled) or $_fr-lease-state = 'current-user') and
                    (: Either the form is not available yet or not available anymore :)
                    not($_fr-document-available-too-early) and not($_fr-document-available-too-late)
                "/>

    </xsl:template>

    <xsl:template match="fr:body[not($is-detail)]">
        <xf:group
            id="fr-form-group"
            class="fr-body"
            model="fr-form-model"
            ref="instance('fr-form-instance')"
        >
            <!-- FIXME: `<a name>` is deprecated in favor of `id`. -->
            <xh:a name="fr-form"/>
            <xf:group id="fr-view-component" class="fr-view-appearance-full">

                <xsl:apply-templates
                    select="$body/(node() except fr:buttons)"
                    mode="within-controls">
                    <!-- Unclear if useful for `not($is-detail)` -->
                    <xsl:with-param
                        name="binds-root"
                        select="$fr-form-model/xf:bind[@id = 'fr-form-binds']"
                        tunnel="yes"/>
                </xsl:apply-templates>

            </xf:group>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:body[$is-detail]">

        <!--
            Form content. Set context on form instance and define this group as `#fr-form-group` as observers will refer to it.
            NOTE: Use `fr-view-component` whenever possible instead since Orbeon Forms 2017.1.
        -->
        <xf:group
            id="fr-form-group"
            class="{
                'fr-body',
                concat('fr-validation-mode-', $validation-mode)
            }"
            model="fr-form-model"
            ref="instance('fr-form-instance')[$_fr-show-form-data]"
            xxf:validation-mode="{$validation-mode}"
        >
            <xsl:if test="$is-full-update">
                <xsl:attribute name="xxf:update">full</xsl:attribute>
            </xsl:if>

            <!-- FIXME: `<a name>` is deprecated in favor of `id`. -->
            <xh:a name="fr-form"/>
            <xsl:choose>
                <xsl:when test="not($use-view-appearance)">
                    <xf:group id="fr-view-component" class="fr-view-appearance-full">

                        <xsl:apply-templates
                            select="if ($body) then $body/(node() except fr:buttons) else node()"
                            mode="within-controls">
                            <xsl:with-param
                                name="binds-root"
                                select="$fr-form-model/xf:bind[@id = 'fr-form-binds']"
                                tunnel="yes"/>
                        </xsl:apply-templates>

                        <!-- Keep markup even in `view` mode for form caching. -->
                        <xxf:setvisited
                            event="fr-visit-all fr-show-relevant-errors"
                            target="#observer"

                            control="fr-view-component"
                            visited="true"
                            recurse="true"/>

                    </xf:group>
                </xsl:when>
                <xsl:otherwise>
                    <!-- Insert appropriate XBL component -->
                    <!-- NOTE: Once we support XBL matching on @appearance, use instead
                         <fr:view appearance="{$view-appearance}">. -->
                    <xsl:element name="fr:{$view-appearance}">
                        <xsl:attribute name="id"              select="'fr-view-component'"/>
                        <xsl:attribute name="class"           select="concat('fr-view-appearance-', $view-appearance)"/>

                        <xsl:attribute name="app"             select="$app"/>
                        <xsl:attribute name="form"            select="$form"/>

                        <xsl:for-each select="('strict'[$mode = 'import'], $wizard-mode)[1]">
                            <!--
                                We'd prefer `mode` to match the term used in the documentation, or maybe
                                `forward-validation-mode`. But `validate` is how the property was named.
                            -->
                            <xsl:attribute name="validate" select="."/>
                        </xsl:for-each>

                        <xsl:for-each select="$wizard-subsections-nav[1]">
                            <xsl:attribute name="subsections-nav" select="."/>
                        </xsl:for-each>

                        <xsl:for-each select="$wizard-subsections-toc[1]">
                            <xsl:attribute name="subsections-toc" select="."/>
                        </xsl:for-each>

                        <xsl:for-each select="$wizard-separate-toc[1]">
                            <xsl:attribute name="separate-toc" select="."/>
                        </xsl:for-each>

                        <xsl:for-each select="$wizard-section-status[1]">
                            <xsl:attribute name="section-status" select="."/>
                        </xsl:for-each>

                        <xsl:for-each select="$wizard-full-update[1]">
                            <xsl:attribute name="full-update" select="."/>
                        </xsl:for-each>

                        <!--
                            This is the `incremental|explicit` Form Runner validation mode, which is passed to the wizard but
                            is also used separately from the wizard. Here too the name should be clearer.
                        -->
                        <xsl:attribute name="validation-mode" select="$validation-mode"/>

                        <xsl:apply-templates
                            select="if ($body) then $body/(node() except fr:buttons) else node()"
                            mode="within-controls">
                            <xsl:with-param
                                name="binds-root"
                                select="$fr-form-model/xf:bind[@id = 'fr-form-binds']"
                                tunnel="yes"/>
                        </xsl:apply-templates>
                        <!-- Optional inner buttons -->
                        <xsl:choose>
                            <xsl:when test="exists($custom-inner-buttons)">
                                <xh:span class="fr-buttons">
                                    <xsl:apply-templates select="$custom-inner-buttons/node()"/>
                                </xh:span>
                            </xsl:when>
                            <xsl:when test="exists($inner-buttons)">
                                <xsl:call-template name="fr-buttons-bar">
                                    <xsl:with-param name="buttons-property"  select="'oxf.fr.detail.buttons.inner'" tunnel="yes"/>
                                    <xsl:with-param name="highlight-primary" select="true()"                        tunnel="yes"/>
                                    <xsl:with-param name="inverse"           select="false()"                       tunnel="yes"/>
                                </xsl:call-template>
                            </xsl:when>
                        </xsl:choose>
                    </xsl:element>
                </xsl:otherwise>
            </xsl:choose>
        </xf:group>
    </xsl:template>

    <!-- Main entry point -->
    <xsl:template match="xh:body">
        <xsl:copy>
            <!-- .orbeon is here to scope all Orbeon CSS rules -->
            <xsl:attribute
                name="class"
                select="
                    string-join(
                        (
                            'orbeon',
                            'xforms-disable-alert-as-tooltip',
                            @class
                        ),
                        ' '
                    )"/>
            <xsl:apply-templates select="@* except @class"/>
            <xf:group
                model="fr-form-model"
                id="fr-view"
                class="container{
                    '-fluid'[$fluid]
                } fr-view fr-mode-{{
                    fr:mode()
                }}{{
                    ' fr-static-readonly-required'[
                        fr:is-readonly-mode() and (
                            let $property-opt := xxf:property(string-join(('oxf.fr.detail.static-readonly-required', fr:app-name(), fr:form-name()), '.'))[. = (true(), false())],
                                $param-opt    := xxf:get-request-parameter('fr-pdf-show-required')[. = ('true', 'false')]
                            return
                                if ((fr:is-service-path() or xxf:get-request-method() = 'POST') and exists($param-opt)) then
                                    xs:boolean($param-opt)    (: for PDF/TIFF :)
                                else if (exists($property-opt)) then
                                    xs:boolean($property-opt) (: also for the `view` mode :)
                                else
                                    false()
                        )
                    ]
                }}"
                xxf:element="div">
                <xsl:choose>
                    <xsl:when test="$is-detail and not($is-form-builder)">
                        <xsl:call-template name="fr-detail-page-global-variables"/>
                        <xsl:apply-templates select="$default-page-template"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="node()"/>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:call-template name="fr-hidden-controls"/>
                <xsl:call-template name="fr-dialogs"/>
            </xf:group>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="fr:navbar[not($bs5)]" name="fr-navbar">
        <xf:group
                xxf:element="div"
                model="fr-form-model"
                ref=".[not(xxf:property(string-join(('oxf.fr.detail.hide-header', fr:app-name(), fr:form-name()), '.')))]"
                class="navbar navbar-inverse navbar-fixed-top">
            <xh:div class="navbar-inner">
                <xh:div class="container">
                    <xsl:variable name="default-objects" as="element()+">
                        <fr:goto-content/>
                        <!-- These are typically to the left -->
                        <fr:logo/>
                        <fr:title/>
                        <!-- These are typically to the right -->
                        <fr:language-selector/>
<!--                        <xh:div>-->
<!--                            xxx: <xf:output value="fr:workflow-stage-value()"/>-->
<!--                        </xh:div>-->
                        <fr:share-icon/>
                        <fr:revision-history-icon/>
                        <fr:status-icons/>
                        <fr:user-nav/>
                        <fr:navbar-home-link/>
                    </xsl:variable>

                    <xsl:apply-templates select="$default-objects"/>
                </xh:div>
            </xh:div>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:navbar[$bs5]" name="fr-navbar5">
        <xf:group
            xxf:element="nav"
            model="fr-form-model"
            ref=".[not(xxf:property(string-join(('oxf.fr.detail.hide-header', fr:app-name(), fr:form-name()), '.')))]"
            class="navbar navbar-expand-lg navbar-dark bg-dark {if ($bs5) then 'fixed-top' else 'position-sticky'}">
            <xh:div class="container-fluid">
                <xsl:variable name="default-objects" as="element()+">
                    <fr:goto-content/>
                    <!-- These are typically to the left -->
                    <fr:logo/>
                    <fr:title/>
                    <xh:button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
                        <xh:span class="navbar-toggler-icon"></xh:span>
                    </xh:button>
                    <xh:div class="collapse navbar-collapse" id="navbarSupportedContent">
                        <xh:ul class="navbar-nav {if ($bs5) then 'ms-auto mt-2 mt-lg-0' else 'me-auto mb-2 mb-lg-0'}">
                            <xh:li class="nav-item px-0 ps-lg-3 py-1 py-lg-1">
                                <fr:language-selector appearance="bootstrap5" fr:dropdown-align="right"/>
                            </xh:li>
                            <xh:li>
                                <fr:status-icons/>
                            </xh:li>
                            <xh:li class="nav-item {if ($bs5) then 'px-0 ps-lg-3 py-1 py-lg-1 d-flex align-items-center' else ''}">
                                <fr:user-nav/>
                            </xh:li>
<!--                            <xh:li class="nav-item {if ($bs5) then 'px-3 d-flex align-items-center' else ''}">-->
<!--                                <xh:div>-->
<!--                                    <xh:a href="/fr/">-->
<!--                                        <xh:i class="fa fa-fw fa-th"/>-->
<!--                                    </xh:a>-->
<!--                                </xh:div>-->
<!--                            </xh:li>-->
                        </xh:ul>
                    </xh:div>
                </xsl:variable>

                <xsl:apply-templates select="$default-objects"/>
            </xh:div>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:hidden-controls" name="fr-hidden-controls">
        <xh:span class="xforms-hidden">

            <!-- Communicate to the client whether the data is safe -->
            <xf:var
                name  = "fr-data-safe"
                id    = "fr-data-safe"
                model = "fr-persistence-model"
                ref   = "instance('fr-persistence-instance')/data-safe"
                value = "data(.)"/>
            <xf:var
                name  = "fr-warn-when-data-unsafe"
                id    = "fr-warn-when-data-unsafe"
                value ="
                    let $property :=
                        xxf:property(
                            string-join(
                                ('oxf.fr.detail.warn-when-data-unsafe', fr:app-name(), fr:form-name()),
                                '.'
                            )
                        )
                    return
                        (: Support boolean property for backward compatibility :)
                        if   ($property instance of xs:boolean)
                        then $property
                        else xxf:evaluate-avt($property) = 'true'
                    "/>
            <xf:action
                event    = "xforms-enabled xforms-value-changed"
                observer = "fr-data-safe fr-warn-when-data-unsafe">
                <xf:action type="javascript">
                    <xf:param name="uuid" value="xxf:document-id()"/>
                    <xf:param name="safe" value="$fr-data-safe = 'true' or not($fr-warn-when-data-unsafe)"/>
                    <xf:body>ORBEON.fr.private.API.setDataStatus(uuid, safe == "true")</xf:body>
                </xf:action>
            </xf:action>

            <!-- Expose document id to JavaScript -->
            <xf:output id="fr-parameters-instance-document" ref="fr:document-id()" class="xforms-hidden"/>

            <!-- When the mode changes to `edit` after a save from `/new`, attempt to change the URL. -->
            <!-- NOTE: Keep `xxf:instance()`, see https://github.com/orbeon/orbeon-forms/issues/2872 -->
            <xf:var name="mode-for-save" value="fr:mode()">
                <xf:action
                    type="javascript"
                    event="xforms-value-changed"
                    if="$mode-for-save = 'edit'">
                    <xf:param name="documentId" value="fr:document-id()"/>
                    <xf:param name="isDraft"    value="fr:is-draft()"/>
                    <xf:body>ORBEON.fr.private.API.newToEdit(documentId, isDraft)</xf:body>
                </xf:action>
            </xf:var>

            <!-- This is a HACK for Form Builder only: place non-relevant instances of all toolbox controls so that
                 xxf:dynamic will have all the JavaScript and CSS resources available on the client.
                 See: https://github.com/orbeon/orbeon-forms/issues/31 -->
            <xsl:if
                test="$is-form-builder and $is-detail"
                xmlns:p="http://www.orbeon.com/oxf/pipeline"
                xmlns:fb="http://orbeon.org/oxf/xml/form-builder">

                <xsl:variable
                    name="property-names"
                    select="p:properties-start-with('oxf.fb.toolbox.group')"
                    as="xs:string*"/>

                <xsl:variable
                    name="resources-names"
                    select="distinct-values(for $n in $property-names return p:split(p:property($n)))"
                    as="xs:string*"/>

                <xsl:variable
                    name="resources"
                    select="for $uri in $resources-names return doc($uri)"
                    as="document-node()*"/>

                <xsl:if test="$resources">
                    <!-- Non-relevant group -->
                    <xf:group ref="()">
                        <xsl:apply-templates
                            select="$resources/*/xbl:binding/fb:metadata/(fb:template | fb:templates/fb:view)/*"
                            mode="filter-fb-template"/>
                        <!-- So that the xxbl:global for repeated sections is included -->
                        <fr:repeater ref="()"/>
                    </xf:group>
                </xsl:if>
                <!-- This part of the hack is to cause the initialization of the grid menus in Form Builder, as
                     some dialogs still use the older grid, which doesn't make a distinction between repeated and
                     non-repeated. We could use `javascript-lifecycle` and initialize the menus for the legacy
                     grids as well but decided not to. We should update the repeated grids in Form Builder to
                     use the non-legacy grids and then remove this. -->
                <fr:grid repeat="content" ref="xf:element('_')" template="()"/>
            </xsl:if>
        </xh:span>
    </xsl:template>

    <!-- Remove id elements on Form Builder templates -->
    <xsl:template match="@id | @bind" mode="filter-fb-template"/>
    <xsl:template match="@ref[not(normalize-space())] | @nodeset[not(normalize-space())]" mode="filter-fb-template">
        <xsl:attribute name="{name()}" select="'()'"/>
    </xsl:template>

    <xsl:template match="fr:title" name="fr-title">
        <!-- Q: Why do we need @ref here? -->
        <xh:h1 class="{if ($bs5) then 'text-white-50 fs-3 mb-0' else ''}"><xf:output value="{if (exists(@ref)) then @ref else '$title'}"/></xh:h1>
    </xsl:template>

    <!-- Description in chosen language or first one if not found -->
    <xsl:template match="fr:description" name="fr-description">
        <xh:div class="row{if ($fluid) then '-fluid' else ''}">
            <xh:div class="span12">

                <xf:var
                    name="description"
                    value="
                        (
                            xxf:instance('fr-form-metadata')/description[@xml:lang = xxf:instance('fr-language-instance')],
                            xxf:instance('fr-form-metadata')/description
                        )[1]"/>

                <xf:group xxf:element="div" ref=".[xxf:non-blank($description)]" class="alert fr-form-description">
                    <!-- Don't allow closing as that removes the markup and the XForms engine might attempt to update the nested
                         xf:output, which will cause an error. -->
                    <xf:var name="is-html" value="$description/@mediatype = 'text/html'"/>
                    <xf:output ref=".[$is-html]"      value="$description" mediatype="text/html"/>
                    <xf:output ref=".[not($is-html)]" value="$description"/>
                </xf:group>
            </xh:div>
        </xh:div>
    </xsl:template>

    <xsl:template match="fr:logo">
        <xsl:if test="not($hide-logo) and exists($default-logo-uri)">
            <xh:div class="navbar-brand">
                <xh:img src="{$default-logo-uri}" alt=""/>
            </xh:div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:language-selector">
        <xf:group xxf:element="div" model="fr-form-model" class="fr-language-choice nav-item">
            <!-- Put default language first, then other languages -->
            <xf:var
                name="available-languages"
                model="fr-resources-model"
                value="frf:getFormLangSelection($app, $form, $fr-selector-resources/resource/@xml:lang/string())"/>

            <!-- Don't display language selector if there is only one language -->
            <!-- NOTE: Resolve model here, as for now model within XBL component won't resolve -->
            <!-- FIXME: This logic is duplicated in language-choice.xbl. -->
            <xf:group
                id="fr-language-selector"
                model="fr-resources-model"
                ref="
                    .[
                        xxf:property('oxf.fr.navbar.language-selector.enable') and
                        not(fr:is-readonly-mode())                             and
                        count($available-languages) gt 1                       and
                        empty(
                            xxf:get-request-header('orbeon-liferay-language')[
                                xxf:non-blank()
                            ]
                        )
                    ]">
                <xf:select1 ref="$fr-selector-lang" appearance="bootstrap" id="fr-language-selector-select" fr:dropdown-align="right">
                    <xsl:if test="@appearance">
                        <xsl:attribute name="appearance" select="@appearance"/>
                    </xsl:if>
                    <xf:itemset ref="$available-languages">
                        <xf:label ref="(xxf:instance('fr-languages-instance')/language[@code = context()]/@native-name, context())[1]"/>
                        <xf:value ref="context()"/>
                    </xf:itemset>
                </xf:select1>
            </xf:group>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:goto-content">
        <xf:group model="fr-form-model" class="xforms-hidden">
            <!-- Group to scope variables -->
            <xf:group appearance="xxf:internal" model="fr-error-summary-model">
                <!-- Link to form content or to errors if any -->
                <!-- See https://github.com/orbeon/orbeon-forms/issues/4993 -->
                <xh:a class="fr-goto-content" href="#{{if (xs:integer(counts/@error) gt 0) then 'fr-errors' else 'fr-form'}}">
<!--                <xh:a class="fr-goto-content" href="#{{if (counts/@error gt 0) then 'fr-errors' else 'fr-form'}}">-->
                    <xf:output model="fr-form-model" value="$fr-resources/summary/labels/goto-content"/>
                </xh:a>
            </xf:group>
        </xf:group>
    </xsl:template>

    <xsl:template name="fr-user-nav-dropdown-content">
        <xh:a id="menu-button" href="#" class="{if ($bs5) then 'btn btn-secondary' else ''} dropdown-toggle" data-toggle="dropdown" data-bs-toggle="dropdown">
            <xh:i class="fa fa-user"/>
        </xh:a>
        <xh:ul class="dropdown-menu dropdown-menu-end" role="menu" aria-labelledBy="menu-button">

            <xf:var name="is-logged-in"  value="exists(xxf:username())"/>

            <!-- Who is logged in -->
            <xf:var
                name="logged-in-class"
                value="
                    if ($is-logged-in)
                    then ''
                    else 'xforms-hidden'
                "/>
            <xh:li role="presentation" class="dropdown-item disabled {{$logged-in-class}}">
                <xh:a role="menuitem" href="#" class="btn btn-link">
                    <xf:output value="
                        xxf:format-message(
                            xxf:r(
                                'authentication.menu.logged-in-as',
                                'fr-fr-resources'
                            ),
                            xxf:username()
                        )"/>
                </xh:a>
            </xh:li>

            <xh:li role="presentation" class="divider {{$logged-in-class}}"/>

            <!-- Logout -->
            <xf:var
                name="logout-url"
                value="xxf:evaluate-avt(xxf:property('oxf.fr.authentication.user-menu.uri.logout'))"/>
            <xf:var
                name="logout-class"
                value="
                    if ($is-logged-in and $logout-url != '')
                    then ''
                    else 'xforms-hidden'
                "/>
            <xh:li role="presentation" class="dropdown-item {{$logout-class}}">
                <xh:a role="menuitem" href="{{$logout-url}}" class="btn btn-link fr-logout-link">
                    <xf:output value="
                        xxf:r(
                            'authentication.menu.logout',
                            'fr-fr-resources'
                        )"/>
                </xh:a>
            </xh:li>

            <!-- Login -->
            <xf:var
                name="login-url"
                value="xxf:evaluate-avt(xxf:property('oxf.fr.authentication.user-menu.uri.login'))"/>
            <xf:var
                name="login-class"
                value="
                    if (not($is-logged-in) and $login-url != '')
                    then ''
                    else 'xforms-hidden'
                "/>
            <xh:li role="presentation" class="dropdown-item {{$login-class}}">
                <xh:a role="menuitem" href="{{$login-url}}" class="btn btn-link">
                    <xf:output value="
                        xxf:r(
                            'authentication.menu.login',
                            'fr-fr-resources'
                        )"/>
                </xh:a>
            </xh:li>

            <!-- Register -->
            <xf:var
                name="register-url"
                value="xxf:evaluate-avt(xxf:property('oxf.fr.authentication.user-menu.uri.register'))"/>
            <xf:var
                name="register-class"
                value="
                    if (not($is-logged-in) and $register-url != '')
                    then ''
                    else 'xforms-hidden'
                "/>
            <xh:li role="presentation" class="dropdown-item {{$register-class}}">
                <xh:a role="menuitem" href="{{$register-url}}" class="btn btn-link">
                    <xf:output value="
                        xxf:r(
                            'authentication.menu.register',
                            'fr-fr-resources'
                        )"/>
                </xh:a>
            </xh:li>

        </xh:ul>
    </xsl:template>

    <xsl:template match="fr:share-icon">
        <xf:group
            class="fr-share-nav"
            ref=".[
                xxf:property(string-join(('oxf.fr.navbar.share-button.enable', fr:app-name(), fr:form-name()), '.')) and
                not(fr:is-embedded())                                                                                and
                xxf:non-blank(xxf:instance('fr-share-dialog-instance')/possible-operations)                          and
                fr:mode() = (
                    'edit',
                    'view'
                )
            ]">

            <xf:trigger appearance="minimal" class="fr-share-button">
                <xf:label><xh:i class="fa fa-fw fa-share-nodes"/></xf:label>
                <xxf:show
                    event="DOMActivate"
                    dialog="fr-share-dialog"/>
            </xf:trigger>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:revision-history-icon[$allow-revision-history]">
        <xf:group
            class="fr-revision-history"
            ref=".[
                not(fr:is-embedded()) and
                fr:mode() = ('edit', 'view')
            ]">

            <xf:trigger appearance="minimal" class="fr-revision-history-button">
                <xf:label><xh:i class="fa fa-fw fa-history" title="{{xxf:r('components.revision-history.label', '|fr-fr-resources|')}}"/></xf:label>
                <xf:dispatch
                    event="DOMActivate"
                    name="fr-open"
                    targetid="fr-revision-history"/>
            </xf:trigger>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:user-nav">
        <xf:group
            class="fr-user-nav"
            ref=".[
                not(fr:mode() = 'test')                                and
                xxf:property('oxf.fr.authentication.user-menu.enable') and (: Q: Why is this not by app/form? :)
                not(fr:is-embedded())
            ]">
            <xsl:choose>
                <xsl:when test="$bs5">
                    <xh:div class="dropdown">
                        <xsl:call-template name="fr-user-nav-dropdown-content"/>
                    </xh:div>
                </xsl:when>
                <xsl:otherwise>
                    <xh:ul class="nav pull-right">
                        <xh:li class="dropdown">
                            <xsl:call-template name="fr-user-nav-dropdown-content"/>
                        </xh:li>
                    </xh:ul>
                </xsl:otherwise>
            </xsl:choose>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:navbar-home-link">
        <xf:group
            class="fr-navbar-home-link"
            ref=".[
                not(fr:mode() = 'test')                        and
                xxf:property('oxf.fr.navbar.home-link.enable') and
                not(fr:is-embedded())
            ]">
            <xh:ul class="nav">
              <xh:li class="">
                <xh:a href="/fr/" title="{{xxf:r('landing.title', '|fr-fr-resources|')}}"><xh:i class="fa fa-th"/></xh:a>
              </xh:li>
            </xh:ul>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:version">
        <xsl:if test="$version">
            <fr:row>
                <xh:div class="fr-orbeon-version"><xsl:value-of select="$version"/></xh:div>
            </fr:row>
        </xsl:if>
    </xsl:template>

    <!-- Content handled separately -->
    <xsl:template match="fr:dialogs"/>

    <xsl:template name="fr-dialogs">
        <!-- Copy custom dialogs under fr:dialogs only (other dialogs will be left in place) -->
        <xsl:apply-templates select=".//fr:dialogs//xxf:dialog" mode="within-dialogs"/>

        <!-- Misc standard dialogs -->
        <xi:include href="oxf:/apps/fr/includes/clear-dialog.xhtml"            xxi:omit-xml-base="true"/>
        <xi:include href="oxf:/apps/fr/includes/submission-dialog.xhtml"       xxi:omit-xml-base="true"/>
        <xi:include href="oxf:/apps/fr/includes/validation-dialog.xhtml"       xxi:omit-xml-base="true"/>

        <!-- Include dialogs from property -->
        <xsl:variable
            name="custom-dialogs"
            select="p:split(p:property(string-join(('oxf.fr.detail.dialogs.custom', $app, $form), '.')))"/>
        <xsl:for-each select="$custom-dialogs">
            <xsl:copy-of select="doc(.)"/>
        </xsl:for-each>

        <!-- Error dialog -->
        <fr:alert-dialog id="fr-error-dialog">
            <fr:label ref="$fr-resources/detail/messages/error-dialog-title"/>
            <fr:neutral-choice/>
        </fr:alert-dialog>

        <!-- Generic confirmation dialog (message must be passed dynamically) -->
        <fr:alert-dialog id="fr-confirmation-dialog">
            <fr:label ref="$fr-resources/detail/messages/confirmation-dialog-title"/>
            <fr:negative-choice>
                <xf:action event="DOMActivate" type="xpath">
                    fr:run-process('oxf.fr.detail.process', 'abort')
                </xf:action>
            </fr:negative-choice>
            <fr:positive-choice>
                <xf:action event="DOMActivate" type="xpath">
                    fr:run-process('oxf.fr.detail.process', 'resume')
                </xf:action>
            </fr:positive-choice>
        </fr:alert-dialog>

        <!-- Action continuation dialog -->
        <fr:alert-dialog id="fr-action-continuation-dialog">
            <fr:label ref="$fr-resources/detail/messages/confirmation-dialog-title"/>
            <fr:negative-choice/>
            <fr:positive-choice/>
        </fr:alert-dialog>

        <xxf:dialog
            id="fr-share-dialog"
            class="fr-dialog-share"
            model="fr-persistence-model"
            level="modal"
            close="true"
            draggable="true">
            <xf:label ref="xxf:r('share-dialog.label', '|fr-fr-resources|')"/>

            <xf:action event="xxforms-dialog-open">
                <xf:setvalue
                    ref="instance('fr-share-dialog-instance')/selected-operations"
                    value="'read'"/>
            </xf:action>

            <xf:action event="xxforms-dialog-close">
                <xf:setvalue
                    iterate="instance('fr-share-dialog-instance')"
                    ref="."/>
            </xf:action>

            <fr:grid ref="instance('fr-share-dialog-instance')">
                <fr:c x="1" w="12" y="1">
                    <xf:output value="xxf:r('share-dialog.body', '|fr-fr-resources|')" mediatype="text/html"/>
                </fr:c>
                <fr:c x="1" w="12" y="2">
                    <xf:output
                        id="my-output"
                        appearance="clipboard-copy"
                        ref="link">
                        <xf:label appearance="minimal" ref="xxf:r('share-dialog.link', '|fr-fr-resources|')"/>
                    </xf:output>
                </fr:c>
                <fr:c x="1" w="4" y="3">
                     <xf:select1 appearance="minimal" ref="selected-operations">
                        <xf:label appearance="minimal" ref="xxf:r('share-dialog.select-operations', '|fr-fr-resources|')"/>
                        <xf:item>
                            <xf:label ref="xxf:r('share-dialog.readonly', '|fr-fr-resources|')"/>
                            <xf:value>read</xf:value>
                        </xf:item>
                        <xf:item>
                            <xf:label ref="xxf:r('share-dialog.readwrite', '|fr-fr-resources|')"/>
                            <xf:value>read update</xf:value>
                        </xf:item>
                        <xf:setvalue
                            event="xforms-enabled xforms-value-changed"
                            ref="instance('fr-share-dialog-instance')/link"
                            value="
                                fr:form-runner-link(
                                    if (instance('fr-share-dialog-instance')/selected-operations = 'read') then
                                        'view'
                                    else if (instance('fr-share-dialog-instance')/selected-operations = 'read update') then
                                        'edit'
                                    else
                                        error(),
                                    true()
                                )"/>
                    </xf:select1>
                </fr:c>
            </fr:grid>

            <xh:div class="fr-dialog-buttons">
                <xf:trigger appearance="xxf:primary">
                    <xf:label ref="xxf:r('buttons.close', '|fr-fr-resources|')" mediatype="text/html"/>
                    <xf:action event="DOMActivate">
                        <xxf:hide dialog="fr-share-dialog"/>
                    </xf:action>
                </xf:trigger>
            </xh:div>
        </xxf:dialog>

        <!-- Listen for upload events -->
        <xf:action
            ev:event="xxforms-upload-error"
            ev:observer="fr-view-component"
            ev:defaultAction="cancel"
            xxf:phantom="true">
            <xf:action type="xpath">
                frf:errorMessage(
                    if (event('error-type') = 'empty-file-error') then
                        xxf:format-message(
                            xxf:r(
                                'detail.messages.upload-error-empty-file',
                                'fr-fr-resources'
                            ),
                            ()
                        )
                    else if (event('error-type') = 'size-error') then
                        xxf:format-message(
                            xxf:r(
                                'detail.messages.upload-error-size',
                                'fr-fr-resources'
                            ),
                            (
                                event('permitted'),
                                event('actual')
                            )
                        )
                    else if (event('error-type') = 'mediatype-error') then
                        xxf:format-message(
                            xxf:r(
                                'detail.messages.upload-error-mediatype',
                                'fr-fr-resources'
                            ),
                            (
                                event('filename'),
                                (: NOTE: As of 2017-11-09 this is not used by the message. :)
                                event('permitted'),
                                event('actual')
                            )
                        )
                    else if (event('error-type') = 'file-scan-error') then
                        xxf:format-message(
                            xxf:r(
                                'detail.messages.upload-error-file-scan',
                                'fr-fr-resources'
                            ),
                            (
                                event('message')
                            )
                        )
                    else
                        xxf:r(
                            concat('detail.messages.', substring-after(event('xxf:type'), 'xxforms-')),
                            'fr-fr-resources'
                        )
                )
            </xf:action>
        </xf:action>

        <xf:action
            ev:event="xxforms-upload-done"
            ev:observer="fr-view-component"
            ev:defaultAction="cancel"
            xxf:phantom="true"
            type="xpath">
            frf:successMessage(
                xxf:r(
                    concat('detail.messages.', substring-after(event('xxf:type'), 'xxforms-')),
                    'fr-fr-resources'
                )
            )
        </xf:action>

        <xf:var
            name="session-expiration-dialog-enabled"
            value="xxf:property(string-join(('oxf.fr.detail.session-expiration-dialog.enabled', fr:app-name(), fr:form-name()), '.'))"/>

        <!-- Session about to expire or expired dialog -->
        <!-- Do not use `fade` class in this modal, as it will prevent the dialog from being hidden when the page/tab is not visible.
             See: https://stackoverflow.com/questions/23677765/bootstrap-modal-hide-is-not-working -->
        <xh:div
            class="
                fr-session-expiration-dialog modal hide
                fr-feature-{{ if ($session-expiration-dialog-enabled) then 'enabled' else 'disabled' }}"
            tabindex="-1" role="dialog" aria-hidden="true">

            <xh:div class="modal-dialog">
                <xh:div class="modal-content">
                    <xh:div class="modal-header">
                        <xh:h4><xf:output value="$fr-resources/detail/session-expiration/title/expiring"/></xh:h4>
                        <xh:h4><xf:output value="$fr-resources/detail/session-expiration/title/expired"/></xh:h4>
                    </xh:div>
                    <xh:div class="modal-body">
                        <xf:output mediatype="text/html" value="$fr-resources/detail/session-expiration/message/expiring"/>
                        <xf:output mediatype="text/html" value="$fr-resources/detail/session-expiration/message/expired"/>
                    </xh:div>
                    <xh:div class="modal-footer">
                        <xh:button class="btn btn-primary">
                            <xf:output value="$fr-resources/detail/session-expiration/renew-button"/>
                        </xh:button>
                    </xh:div>
                </xh:div>
            </xh:div>
        </xh:div>

    </xsl:template>

    <!-- Error summary added by Form Runner -->
    <xsl:template match="fr:error-summary[@position]">
        <xsl:variable name="position" select="@position" as="xs:string"/>

        <!-- NOTE: We used to only handle events coming from controls bound to "fr-form-instance" instance, but this
             doesn't work with "section templates". We now use the observer mechanism of fr:error-summary -->

        <!-- For form builder we disable the error summary and say that the form is always valid -->
        <xsl:if test="not($is-form-builder)">
            <fr:row>
                <fr:error-summary
                    id="error-summary-control-{$position}"
                    observer="fr-view-component fr-captcha"
                    model="fr-error-summary-model"
                    alerts-count-ref="counts/@alert"
                    errors-count-ref="counts/@error"
                    warnings-count-ref="counts/@warning"
                    infos-count-ref="counts/@info"
                    visible-alerts-count-ref="visible-counts/@alert"
                    visible-errors-count-ref="visible-counts/@error"
                    visible-warnings-count-ref="visible-counts/@warning"
                    visible-infos-count-ref="visible-counts/@info"
                    valid-ref="valid"
                >
                    <fr:label>
                        <!-- If there are e.g. some errors AND warnings, the formatter will display a generic word such as "message" -->
                        <xf:output
                            value="
                                xxf:format-message(
                                    xxf:r('errors.summary-title', '|fr-fr-resources|'),
                                    (
                                        xxf:instance('fr-error-summary-instance')/visible-counts/(
                                            if (count((@error, @warning, @info)[. gt 0]) gt 1) then
                                                3
                                            else if (@error gt 0) then
                                                0
                                            else if (@warning gt 0) then
                                                1
                                            else if (@info gt 0) then
                                                2
                                            else
                                                4
                                        ),
                                        xxf:instance('fr-error-summary-instance')/visible-counts/xs:integer(@alert)
                                    )
                                )"/>
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

    <!-- Optional standard explanation message for view mode -->
    <xsl:template name="fr-explanation">
        <xf:group
                xxf:element="div"
                model="fr-form-model"
                ref=".[$fr-mode = 'view' and xxf:property(string-join(('oxf.fr.detail.view.show-explanation', fr:app-name(), fr:form-name()), '.')) = true()]"
                class="fr-explanation">
            <xf:output value="$fr-resources/detail/view/explanation"/>
        </xf:group>
    </xsl:template>

    <xsl:template match="fr:status-icons" name="fr-status-icons">
        <!-- Status icons for detail page -->

        <xf:group
            model="fr-form-model"
            ref=".[not($fr-mode = ('summary', 'home', 'landing'))]"
            class="fr-status-icons nav-item">

            <xf:group model="fr-error-summary-model" ref=".[visible-counts/@alert gt 0]">
                <!-- Form has error or warning messages -->
                <xf:repeat ref="visible-counts/(@error, @warning, @info)[. gt 0]">
                    <xh:span class="badge badge-{{if (name() = 'error') then 'important' else if (name() = 'warning') then 'warning' else 'info'}}">
                        <xf:output value="."/>
                    </xh:span>
                </xf:repeat>
            </xf:group>
            <xf:group model="fr-error-summary-model" ref=".[visible-counts/@alert = 0]" class="fr-validity-icon">
                <!-- Form has no error or warning messages -->
                <xf:group model="fr-form-model"><xh:i class="fa fa-check fa-fw" title="{{$fr-resources/errors/none}}"/></xf:group>
            </xf:group>
            <xf:group model="fr-persistence-model" ref="instance('fr-persistence-instance')[data-status = 'dirty']" class="fr-data-icon">
                <!-- Data is dirty -->
                <xf:group model="fr-form-model"><xh:i class="fa fa-hdd-o fa-fw" title="{{$fr-resources/errors/unsaved}}"/></xf:group>
            </xf:group>
        </xf:group>
    </xsl:template>

    <!-- Success messages -->
    <xsl:template match="fr:messages" name="fr-messages">
        <xf:switch
            class="fr-messages"
            model="fr-persistence-model"
            ref=".[instance('fr-persistence-instance')/message != '']"
            xh:aria-live="polite">

            <xf:case id="fr-message-none">
                <xh:span/>
            </xf:case>
            <xf:case id="fr-message-success">
                <xf:output value="instance('fr-persistence-instance')/message" mediatype="text/html" class="fr-message-success alert alert-success"/>
            </xf:case>
            <xf:case id="fr-message-error">
                <xf:output value="instance('fr-persistence-instance')/message" mediatype="text/html" class="fr-message-error alert alert-error"/>
            </xf:case>

        </xf:switch>
    </xsl:template>

    <xsl:template match="fr:template-buttons-bar" name="fr-buttons-bar">

        <xsl:param name="buttons-property"  tunnel="yes" as="xs:string*"  select="()"/>
        <xsl:param name="highlight-primary" tunnel="yes" as="xs:boolean?" select="()"/>
        <xsl:param name="inverse"           tunnel="yes" as="xs:boolean?" select="()"/>

        <!-- Nothing below must statically depend on the mode -->
        <xsl:choose>
            <xsl:when test="exists($custom-buttons) and empty($buttons-property)">
                <xh:span class="fr-buttons">
                    <xsl:apply-templates select="$custom-buttons/node()"/>
                </xh:span>
            </xsl:when>
            <xsl:when test="not($hide-buttons-bar)">
                <xf:var
                    name="hide-buttons-bar"
                    value="$_fr-persistence-instance/found-document-message-to-show != '' or
                           $_fr-document-available-too-early-or-late"/>
                <xf:group
                    model="fr-form-model"
                    class="fr-buttons"
                    ref=".[not($hide-buttons-bar)]">
                    <fr:buttons-bar
                        buttons-property="{$buttons-property}"
                        highlight-primary="{$highlight-primary}"
                        inverse="{$inverse}"/>
                </xf:group>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="fr:pdf-header-footer[$is-readonly-mode]">

        <xsl:variable
            xmlns:FormRunnerPdfConfig="java:org.orbeon.oxf.fr.pdf.PdfConfig20231"
            name="pdf-header-footer-config-elem"
            select="FormRunnerPdfConfig:getHeaderFooterConfigXml($app, $form)/*"/>

        <xsl:if test="exists($pdf-header-footer-config-elem/pages/*/*/*/values)">
            <xf:group ref="instance('fr-form-instance')[fr:mode() = 'pdf']" class="fr-pdf-header-footer-details xforms-hidden">

                <!-- We need this in XForms as well, so we can handle the choice of language dynamically -->
                <xf:var
                    xmlns:FormRunnerPdfConfig="java:org.orbeon.oxf.fr.pdf.PdfConfig20231"
                    name="pdf-header-footer-config-elem"
                    value="FormRunnerPdfConfig:getHeaderFooterConfigXml(fr:app-name(), fr:form-name())/*"/>

                <!-- Poor man's hashmap until we use XSLT 3 -->
                <xsl:variable
                    name="custom-css-class-to-control-names-map"
                    as="element(_)"
                    select="frf:buildCustomCssClassToControlNamesMapDocument($body)"/>

                <!-- Parameters are shared among all settings -->
                <!-- TODO: Error in comparison when the variable contains a `map()` -->
    <!--            <xf:var-->
    <!--                name="template-params"-->
    <!--                value="{fr:build-template-param-map($pdf-header-footer-config-elem/parameters/_, ())}"/>-->

                <xsl:for-each select="$pdf-header-footer-config-elem/pages/*/*/*[exists(values)]">
                    <xf:output
                        class="fr-{../../name()}-{../name()}-{name()}"
                        ref=".[{(visible[not(@type = 'null')], 'true()')[1]}]"
                        value="
                            let $current  := $pdf-header-footer-config-elem/pages/{../../name()}/{../name()}/{name()},
                                $template := $current/values/(*[name() = fr:lang()], *[name() = '_'], *)[1]
                            return
                                if (exists($template)) then
                                    concat(
                                        '&quot;',
                                        xxf:process-template(
                                            replace($template, '&quot;', '\\&quot;'),
                                            'en', (: unused! :)
                                            {
                                                fr:build-template-param-map(
                                                    $pdf-header-footer-config-elem/parameters/*,
                                                    (),
                                                    true(),
                                                    $custom-css-class-to-control-names-map
                                                )
                                            }
                                        ),
                                        '&quot;'
                                    )
                                else
                                    ''"/>
                </xsl:for-each>

            </xf:group>
        </xsl:if>

    </xsl:template>

    <!-- TOC: Top-level -->
    <xsl:template match="fr:toc[$has-toc]" name="fr-toc">
        <!-- This is statically built in XSLT instead of using XForms -->
        <xh:div class="fr-toc well sidebar-nav">
            <xh:ul class="nav nav-list">
                <xh:li class="nav-header"><xf:output ref="$fr-resources/summary/titles/toc"/></xh:li>
                <xsl:apply-templates select="$body" mode="fr-toc-sections"/>
            </xh:ul>
        </xh:div>
    </xsl:template>

    <!-- TOC: Swallow unneeded nodes -->
    <xsl:template match="text()" mode="fr-toc-sections"/>

    <xsl:template match="*" mode="fr-toc-sections">
        <xsl:apply-templates mode="fr-toc-sections"/>
    </xsl:template>

    <!-- TOC: handle section -->
    <xsl:template match="fr:section" mode="fr-toc-sections">
        <xh:li xxf:control="true">
            <!-- Propagate binding so that entry for section disappears if the section is non-relevant -->
            <xsl:copy-of select="@model | @context | @bind | @ref"/>
            <!-- Clicking sets the focus -->
            <xf:trigger appearance="minimal">
                <xf:label value="xxf:label('{@id}')"/>
                <xf:setfocus
                    event="DOMActivate"
                    control="{@id}"
                    includes="{{frf:xpathFormRunnerStringProperty('oxf.fr.detail.focus.includes')}}"
                    excludes="{{frf:xpathFormRunnerStringProperty('oxf.fr.detail.focus.excludes')}}"/>
            </xf:trigger>
            <!-- Sub-sections if any -->
            <xsl:if test="exists(fr:section)">
                <xh:ol>
                    <xsl:apply-templates mode="fr-toc-sections"/>
                </xh:ol>
            </xsl:if>
        </xh:li>
    </xsl:template>

    <xsl:function name="fr:maybe-replace" as="xs:string?">
        <xsl:param name="content" as="xs:string?"/>
        <xsl:param name="replace" as="xs:boolean"/>
        <xsl:value-of select="
            if (exists($content)) then
                if ($replace) then
                    replace(
                        $content,
                        '&quot;',
                        '\\&quot;'
                    )
                else
                    $content
            else
                ()"/>
    </xsl:function>

</xsl:stylesheet>
