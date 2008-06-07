<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2008 Orbeon, Inc.

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
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:exforms="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:attribute name="class" select="string-join(('xforms-disable-hint-as-tooltip', 'xforms-disable-alert-as-tooltip', @class), ' ')"/>
            <xsl:apply-templates select="@* except @class"/>
            <xforms:group model="fr-form-model" appearance="xxforms:internal">
                <xsl:apply-templates select="node()"/>
                <!--<widget:xforms-instance-inspector xmlns:widget="http://orbeon.org/oxf/xml/widget"/>-->
            </xforms:group>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xhtml:body//fr:view">

        <!-- Scope variable with Form Runner resources -->
        <xxforms:variable name="fr-resources" select="xxforms:instance('fr-fr-current-resources')"/>

        <xsl:variable name="label" select="xforms:label"/>

        <xsl:if test="@width and not(@width = ('750px', '950px', '974px', '1154px'))">
            <xsl:message terminate="yes">Value of fr:view/@view is not valid</xsl:message>
        </xsl:if>
        <xhtml:div id="fr-view">
            <xhtml:div id="{if (@width = '750px') then 'doc' else if (@width = '950px') then 'doc2' else if (@width = '1154px') then 'doc-fb' else 'doc4'}"
                       class="{if (doc('input:instance')/*/mode = 'print') then ' fr-print-mode' else ''}">
                <!-- Scope form resources -->
                <xxforms:variable name="form-resources" select="xxforms:instance('fr-current-form-resources')"/>
                <xhtml:div class="fr-header">
                    <!-- Switch language -->
                    <xsl:if test="not(doc('input:instance')/*/mode = ('print', 'pdf'))">
                        <xhtml:div class="fr-summary-language-choice">
                            <xxforms:variable name="available-languages"
                                              select="xxforms:instance('fr-form-resources')/resource/@xml:lang"/>
                            <!-- This implements a sort of xforms:select1[@appearance = 'xxforms:full']. Should be componentized. -->
                            <xforms:group id="fr-language-selector">
                                <xforms:repeat model="fr-resources-model" nodeset="$available-languages">
                                    <xxforms:variable name="position" select="position()"/>
                                    <xxforms:variable name="label" select="(instance('fr-languages-instance')/language[@code = context()]/@native-name, context())[1]"/>
                                    <xforms:group ref=".[$position > 1]"> | </xforms:group>
                                    <xforms:trigger ref=".[context() != instance('fr-language-instance')]" appearance="minimal">
                                        <xforms:label value="$label"/>
                                        <xforms:action ev:event="DOMActivate">
                                            <xforms:setvalue ref="instance('fr-language-instance')" value="context()"/>
                                        </xforms:action>
                                    </xforms:trigger>
                                    <xforms:output ref=".[context() = instance('fr-language-instance')]" value="$label"/>
                                </xforms:repeat>
                            </xforms:group>
                        </xhtml:div>
                    </xsl:if>
                    <!-- Custom content added to the header -->
                    <xsl:if test="fr:header">
                        <xforms:group model="fr-form-model" context="instance('fr-form-instance')">
                            <xsl:apply-templates select="fr:header/node()"/>
                        </xforms:group>
                    </xsl:if>
                </xhtml:div>
                <!-- Custom content added to the header -->
                <xsl:if test="fr:header">
                    <xforms:group model="fr-form-model" context="instance('fr-form-instance')">
                        <xsl:apply-templates select="fr:header/node()"/>
                    </xforms:group>
                </xsl:if>
                <xhtml:div id="hd" class="fr-top">&#160;</xhtml:div>
                <xhtml:div id="bd" class="fr-container">
                    <xhtml:div id="yui-main">
                        <xhtml:div class="yui-b">
                            <xxforms:variable name="metadata-lang" select="xxforms:instance('fr-language-instance')"/>
                            <xxforms:variable name="source-form-metadata" select="xxforms:instance('fr-source-form-instance')/xhtml:head/xforms:model/xforms:instance[@id = 'fr-form-metadata']/*"/>
                            <!-- title in chosen language or first one if not found -->
                            <xxforms:variable name="title"
                                              select="(($source-form-metadata/title[@xml:lang = $metadata-lang],
                                                        $source-form-metadata/title[1],
                                                        instance('fr-form-metadata')/title[@xml:lang = $metadata-lang],
                                                        instance('fr-form-metadata')/title[1],
                                                        ({$label/@ref}),
                                                        '{$label}',
                                                        /xhtml:html/xhtml:head/xhtml:title)[normalize-space() != ''])[1]"/>
                            <!-- description in chosen language or first one if not found -->
                            <xxforms:variable name="description"
                                              select="($source-form-metadata/description[@xml:lang = $metadata-lang],
                                                        $source-form-metadata/description[1],
                                                        instance('fr-form-metadata')/description[@xml:lang = $metadata-lang],
                                                        instance('fr-form-metadata')/description[1])[1]"/>


                            <!--<xforms:output value="string-join(($source-form-metadata/description[@xml:lang = $metadata-lang], $source-form-metadata/description[1]), ' - ')"/>-->
                            <!--xxx-->
                            <!--<xforms:output value="string-join($source-form-metadata/(description[@xml:lang = $metadata-lang], description[1]), ' - ')"/>-->
                            <xhtml:div class="yui-g fr-logo">
                                <xsl:choose>
                                    <!-- If custom logo section is provided, use that -->
                                    <xsl:when test="fr:logo">
                                        <xsl:apply-templates select="fr:logo/node()"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xforms:group model="fr-form-model" appearance="xxforms:internal">
                                            <xhtml:table class="fr-layout-table">
                                                <xhtml:tr>
                                                    <xhtml:td>
                                                        <xforms:output value="((instance('fr-form-metadata')/logo, '/apps/fr/style/orbeon-logo-trimmed-transparent-42.png')[normalize-space() != ''])[1]" mediatype="image/*"/>
                                                    </xhtml:td>
                                                    <xhtml:td>
                                                        <xhtml:h1>
                                                            <xforms:output value="$title"/>
                                                        </xhtml:h1>
                                                    </xhtml:td>
                                                </xhtml:tr>
                                            </xhtml:table>
                                        </xforms:group>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xhtml:div>
                            <xhtml:div class="yui-g fr-separator">&#160;</xhtml:div>
                            <xhtml:div class="yui-g fr-body">
                                <!-- Optional description-->
                                <xforms:group model="fr-form-model" ref=".[normalize-space($description) != '']">
                                    <xforms:switch>
                                        <xforms:case id="fr-form-description-case-on">
                                            <xhtml:div class="fr-form-description">
                                                <xforms:output value="$description"/>
                                                <xforms:trigger appearance="minimal" class="fr-close">
                                                    <xforms:label>[close]</xforms:label>
                                                    <xforms:toggle ev:event="DOMActivate" case="fr-form-description-case-off"/>
                                                </xforms:trigger>
                                            </xhtml:div>
                                        </xforms:case>
                                        <xforms:case id="fr-form-description-case-off"/>
                                    </xforms:switch>
                                </xforms:group>

                                <!-- Set context on form instance and define this group as #fr-form-group as observers will refer to it -->
                                <xforms:group id="fr-form-group" model="fr-form-model" ref="instance('fr-form-instance')">
                                    <!-- Main form content -->
                                    <xsl:apply-templates select="fr:body/node()"/>
                                </xforms:group>
                            </xhtml:div>
                            <xhtml:div class="yui-g fr-separator">&#160;</xhtml:div>
                            <xhtml:div class="yui-g fr-buttons-block">
                                <!-- Display the toolbar and errors -->
                                <xforms:group model="fr-persistence-model" appearance="xxforms:internal">

                                    <xhtml:div class="fr-status-icons">
                                        <xforms:group model="fr-error-summary-model" ref=".[instance('fr-form-valid-instance') = 'false']">
                                            <!-- Form is invalid -->
                                            <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/exclamation.png" alt="Errors on Form" title="Errors on Form"/>
                                        </xforms:group>
                                        <xforms:group model="fr-error-summary-model" ref=".[instance('fr-form-valid-instance') = 'true']">
                                            <!-- Form is valid -->
                                            <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/tick.png" alt="No Errors on Form" title="No Errors on Form"/>
                                        </xforms:group>
                                        <xforms:group ref="instance('fr-persistence-instance')[data-status = 'dirty']">
                                            <!-- Data is dirty -->
                                            <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/disk.png" alt="Unsaved Changes" title="Unsaved Changes"/>
                                            <!--<xhtml:p class="fr-unsaved-data">-->
                                                <!-- TODO: i18n -->
                                                <!--Your document contains unsaved changes.-->
                                            <!--</xhtml:p>-->
                                        </xforms:group>
                                    </xhtml:div>

                                    <xxforms:variable name="message-or-errors-shown" select="instance('fr-persistence-instance')/message != ''
                                                                                    or xxforms:instance('fr-errors-instance')/error"/>

                                    <!-- Only show messages and error table if necessary -->
                                    <xforms:group model="fr-persistence-model" ref=".[$message-or-errors-shown]">
                                        <xhtml:table class="fr-error-messages">
                                            <xhtml:tr>
                                                <xhtml:td>
                                                    <!-- Display messages -->
                                                    <xforms:switch>
                                                        <xforms:case id="fr-message-none">
                                                            <xhtml:p/>
                                                        </xforms:case>
                                                        <xforms:case id="fr-message-success">
                                                            <xhtml:p class="fr-message-success">
                                                                <xforms:output value="instance('fr-persistence-instance')/message"/>
                                                            </xhtml:p>
                                                        </xforms:case>
                                                        <xforms:case id="fr-message-validation-error">
                                                            <xhtml:p class="fr-message-validation-error">
                                                                <xforms:output value="instance('fr-persistence-instance')/message"/>
                                                            </xhtml:p>
                                                        </xforms:case>
                                                        <xforms:case id="fr-message-fatal-error">
                                                            <xhtml:p class="fr-message-fatal-error">
                                                                <xforms:output value="instance('fr-persistence-instance')/message"/>
                                                            </xhtml:p>
                                                        </xforms:case>
                                                    </xforms:switch>
                                                </xhtml:td>
                                            </xhtml:tr>
                                            <xhtml:tr>
                                                <xhtml:td>
                                                    <xforms:group ref=".">
                                                        <xforms:group model="fr-error-summary-model" ref="instance('fr-errors-instance')[error[@id = instance('fr-visited-instance')/control/@id]]">
                                                            <!-- TODO: i18n -->
                                                            <xhtml:span class="fr-error-title">Form Errors</xhtml:span>
                                                            <xhtml:ol class="fr-error-list">
                                                                <xforms:repeat nodeset="error" id="fr-errors-repeat">
                                                                    <xhtml:li>
                                                                        <xforms:trigger appearance="minimal">
                                                                            <xforms:label>
                                                                                <xforms:output value="@label" class="fr-error-label"/>
                                                                            </xforms:label>
                                                                            <xforms:setfocus ev:event="DOMActivate" control="{{@id}}"/>
                                                                        </xforms:trigger>
                                                                        <!--<xhtml:a href="#{{@id}}">-->
                                                                            <!--<xforms:output value="@label" class="fr-error-label"/>-->
                                                                        <!--</xhtml:a>-->
                                                                        <xforms:group ref=".[string-length(@indexes) > 0]" class="fr-error-row">
                                                                            <xforms:output value="concat(' (row ', @indexes, ')')"/>
                                                                        </xforms:group>
                                                                        <xforms:group ref=".[normalize-space(@alert) != '']" class="fr-error-alert">
                                                                            - <xforms:output value="@alert"/>
                                                                        </xforms:group>
                                                                    </xhtml:li>
                                                                </xforms:repeat>
                                                            </xhtml:ol>
                                                        </xforms:group>
                                                        <xforms:group model="fr-error-summary-model" ref="instance('fr-errors-instance')[not(error)]">
                                                        </xforms:group>
                                                    </xforms:group>
                                                </xhtml:td>
                                            </xhtml:tr>
                                        </xhtml:table>
                                    </xforms:group>
                                    <xhtml:div class="fr-buttons">
                                        <xsl:choose>
                                            <!-- In print and test modes, only include a close button -->
                                            <xsl:when test="doc('input:instance')/*/mode = ('print', 'test')">
                                                <xsl:variable name="default-buttons" as="element(fr:buttons)">
                                                    <fr:buttons>
                                                        <fr:close-button/>
                                                    </fr:buttons>
                                                </xsl:variable>
                                                <xsl:apply-templates select="$default-buttons/*"/>
                                            </xsl:when>
                                            <!-- In view mode  -->
                                            <xsl:when test="doc('input:instance')/*/mode = ('view')">
                                                <xsl:variable name="default-buttons" as="element(fr:buttons)">
                                                    <fr:buttons>
                                                        <fr:back-button/>
                                                        <fr:pdf-button/>
                                                    </fr:buttons>
                                                </xsl:variable>
                                                <xsl:apply-templates select="$default-buttons/*"/>
                                            </xsl:when>
                                            <!-- In PDF mode, don't include anything -->
                                            <xsl:when test="doc('input:instance')/*/mode = ('pdf')"/>
                                            <!-- Use user-provided buttons -->
                                            <xsl:when test="fr:buttons">
                                                <xsl:apply-templates select="fr:buttons/node()"/>
                                            </xsl:when>
                                            <!-- Use default buttons -->
                                            <xsl:otherwise>
                                                <xsl:variable name="default-buttons" as="element(fr:buttons)">
                                                    <fr:buttons>
                                                        <fr:back-button/>
                                                        <fr:clear-button/>
                                                        <fr:print-button/>
                                                        <fr:pdf-button/>
                                                        <!-- These buttons are disabled until we can save initial changes to the DOM in store and
                                                              replay them when the form is first loaded offline -->
                                                        <!--<fr:take-offline/>-->
                                                        <!--<fr:take-online/>-->
                                                        <!--<fr:save-offline/>-->
                                                        <fr:save-locally-button/>
                                                        <fr:save-button/>
                                                    </fr:buttons>
                                                </xsl:variable>
                                                <xsl:apply-templates select="$default-buttons/*"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xhtml:div>
                                </xforms:group>
                            </xhtml:div>
                        </xhtml:div>
                    </xhtml:div>
                    <!--<xsl:if test="fr:leftxxx">-->
                        <!--<xhtml:div class="yui-b">-->
                            <!--<xsl:apply-templates select="fr:left/node()"/>-->
                        <!--</xhtml:div>-->
                    <!--</xsl:if>-->
                </xhtml:div>
                <xhtml:div id="ft" class="fr-bottom">
                    <xsl:choose>
                        <xsl:when test="fr:footer">
                            <xsl:apply-templates select="fr:footer/node()"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version" name="orbeon-forms-version" select="version:getVersion()" as="xs:string"/>
                            <xhtml:div class="fr-orbeon-version">Orbeon Forms <xsl:value-of select="$orbeon-forms-version"/></xhtml:div>
                        </xsl:otherwise>
                    </xsl:choose>
                </xhtml:div>
            </xhtml:div>

            <xsl:if test="fr:left">
                <xhtml:div>
                    <xsl:apply-templates select="fr:left/node()"/>
                </xhtml:div>
            </xsl:if>

        </xhtml:div>
        <xi:include href="import-export/import-export-dialog.xml" xxi:omit-xml-base="true"/>
        <xi:include href="includes/clear-dialog.xhtml" xxi:omit-xml-base="true"/>

        <xhtml:span class="fr-hidden">
            <!-- Hidden field to communicate to the client the current section to collapse or expand -->
            <xforms:input model="fr-sections-model" ref="instance('fr-current-section-instance')/id" id="fr-current-section-id-input" class="xforms-disabled"/>
            <xforms:input model="fr-sections-model" ref="instance('fr-current-section-instance')/repeat-indexes" id="fr-current-section-repeat-indexes-input" class="xforms-disabled"/>
            <!-- Hidden field to communicate to the client whether the data is clean or dirty -->
            <xforms:input model="fr-persistence-model" ref="instance('fr-persistence-instance')/data-status" id="fr-data-status-input" class="xforms-disabled"/>
        </xhtml:span>

    </xsl:template>

    <xsl:template match="fr:back-button">
        <!-- Display a "close" button as it's clearer for users -->
        <xforms:trigger>
            <xforms:label>
                <xhtml:img width="11" height="16" src="/apps/fr/style/close.gif" alt=""/>
                <xforms:output value="$fr-resources/detail/labels/close"/>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:dispatch target="fr-persistence-model" name="fr-goto-summary"/>
            </xforms:action>
        </xforms:trigger>
        <xsl:if test="false()">
            <!-- Trigger shown to go back if the data is dirty -->
            <xforms:trigger ref="instance('fr-persistence-instance')[data-status = 'dirty']">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/house.png" alt="Home"/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/discard"/></xhtml:span>
                </xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <xforms:dispatch target="fr-persistence-model" name="fr-goto-summary"/>
                </xforms:action>
            </xforms:trigger>
            <!-- Trigger shown to go back if the data is clean -->
            <xforms:trigger ref="instance('fr-persistence-instance')[data-status = 'clean']">
                <xforms:label>
                    <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/house.png" alt="Home"/>
                    <xhtml:span><xforms:output value="$fr-resources/detail/labels/return"/></xhtml:span>
                </xforms:label>
                <xforms:action ev:event="DOMActivate">
                    <xforms:dispatch target="fr-persistence-model" name="fr-goto-summary"/>
                </xforms:action>
            </xforms:trigger>
        </xsl:if>
    </xsl:template>

    <xsl:template match="fr:clear-button">
        <xforms:trigger>
            <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/clear.gif" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/clear"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:setvalue ref="xxforms:instance('errors-state')/submitted">true</xforms:setvalue>
                <xxforms:show dialog="fr-clear-confirm-dialog"/>
            </xforms:action>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:print-button">
        <xforms:trigger>
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/printer.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/print"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:send submission="fr-print-submission"/>
            </xforms:action>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:pdf-button">
        <!-- Show button only if there is no PDF template -->
        <xforms:trigger model="fr-persistence-model"
                        ref=".[instance('fr-source-form-instance')/xhtml:head/xforms:model/xforms:instance[@id = 'fr-form-attachments']/*/pdf = '']">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/pdf.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/print-pdf"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:send submission="fr-pdf-submission"/>
            </xforms:action>
        </xforms:trigger>
        <!-- Show button only if there is a PDF template -->
        <xforms:trigger model="fr-persistence-model"
                        ref=".[instance('fr-source-form-instance')/xhtml:head/xforms:model/xforms:instance[@id = 'fr-form-attachments']/*/pdf != '']">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/pdf.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/print-pdf"/></xhtml:span>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xforms:send submission="fr-pdf-template-submission"/>
            </xforms:action>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:take-offline">
        <xforms:trigger id="take-offline-button" ref="if (xxforms:instance('fr-offline-instance')/is-online = 'true') then . else ()">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/take-offline.gif" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/offline"/></xhtml:span>
            </xforms:label>
            <xxforms:offline ev:event="DOMActivate"/>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:take-online">
        <xforms:trigger id="take-online-button" ref="if (xxforms:instance('fr-offline-instance')/is-online = 'false') then . else ()">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/take-online.gif" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/online"/></xhtml:span>
            </xforms:label>
            <xxforms:online ev:event="DOMActivate"/>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:save-offline">
        <xforms:trigger id="save-offline-button" ref="if (xxforms:instance('fr-offline-instance')/is-online = 'false') then . else ()">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/save.gif" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/save-offline"/></xhtml:span>
            </xforms:label>
            <xxforms:offline-save ev:event="DOMActivate"/>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:save-locally-button">
        <xforms:trigger id="save-locally-button">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/disk.png" alt=""/>
                <xforms:output value="$fr-resources/detail/labels/save-locally"/>
            </xforms:label>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:save-button">
        <xforms:trigger ref="instance('fr-triggers-instance')/save" xxforms:modal="true" id="fr-save-button">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/database_save.png" alt=""/>
                <xhtml:span><xforms:output value="$fr-resources/detail/labels/save-document"/></xhtml:span>
            </xforms:label>
        </xforms:trigger>
    </xsl:template>

    <xsl:template match="fr:close-button">
        <xforms:trigger>
            <xforms:label>
                <xhtml:img width="11" height="16" src="/apps/fr/style/close.gif" alt=""/>
                <xforms:output value="$fr-resources/detail/labels/close"/>
            </xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xxforms:script>window.close();</xxforms:script>
            </xforms:action>
        </xforms:trigger>
    </xsl:template>

    <xsl:template name="fr-handle-inplace-hint">
        <xsl:choose>
            <xsl:when test="@ref">
                <xsl:choose>
                    <xsl:when test="xforms:hint">
                        <xsl:attribute name="value"
                                       select="concat('for $value in ', @ref, '
                                                       return if (normalize-space($value) = '''')
                                                              then concat(''['', ', if (xforms:hint/@ref) then xforms:hint/@ref else concat('''', xforms:hint, ''''), ', '']'')
                                                              else $value')"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:attribute name="value" select="@ref"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
            <xsl:when test="@bind">
                <xsl:choose>
                    <xsl:when test="xforms:hint">
                        <xsl:attribute name="value"
                                       select="concat('for $value in xxforms:bind(''', @bind, ''')
                                                       return if (normalize-space($value) = '''')
                                                              then concat(''['', ', if (xforms:hint/@ref) then xforms:hint/@ref else concat('''', xforms:hint, ''''), ', '']'')
                                                              else $value')"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:copy-of select="@bind"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
        </xsl:choose>
    </xsl:template>
    

    <xsl:template match="xforms:input[@appearance = 'fr:in-place']">
        <xsl:if test="not(@id != '')">
            <xsl:message terminate="yes">"id" attribute is mandatory</xsl:message>
        </xsl:if>
        <xforms:switch id="{@id}">
            <xsl:attribute name="class" select="string-join(('fr-inplace-input', @class), ' ')"/>
            <xforms:case id="fr-inplace-{@id}-view">
                <!-- View mode -->
                <xhtml:span class="fr-inplace-view">
                    <xhtml:span class="fr-inplace-content">
                        <xforms:output>
                            <xsl:if test="@tabindex | @navindex">
                                <xsl:attribute name="navindex" select="(@navindex, @tabindex)[1]"/>
                            </xsl:if>
                            <!-- Handle inline hint-->
                            <xsl:call-template name="fr-handle-inplace-hint"/>
                            <!-- Keep label as is -->
                            <xsl:copy-of select="xforms:label"/>
                            <!-- React to user click -->
                            <xforms:action ev:event="DOMActivate">
                                <xforms:toggle case="fr-inplace-{@id}-edit"/>
                                <xforms:setfocus control="fr-inplace-{@id}-input"/>
                            </xforms:action>
                        </xforms:output>
                        <!-- Hidden output just to display alert -->
                        <xforms:output class="fr-hidden">
                            <xsl:copy-of select="@ref | @bind | xforms:alert"/>
                        </xforms:output>
                        <xsl:if test="fr:buttons">
                            <xhtml:span class="fr-inplace-buttons">
                                <xsl:apply-templates select="fr:buttons/node()"/>
                            </xhtml:span>
                        </xsl:if>
                    </xhtml:span>
                </xhtml:span>
            </xforms:case>
            <xforms:case id="fr-inplace-{@id}-edit">
                <!-- Edit mode -->
                <xhtml:span class="fr-inplace-edit">
                    <xhtml:span class="fr-inplace-content">
                        <xforms:input id="fr-inplace-{@id}-input">
                            <xsl:if test="@tabindex | @navindex">
                                <xsl:attribute name="navindex" select="(@navindex, @tabindex)[1]"/>
                            </xsl:if>
                            <xsl:copy-of select="@ref | @bind | @incremental | xforms:label | xforms:alert"/>
                            <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                        </xforms:input>
                        <xhtml:span class="fr-inplace-buttons">
                            <xforms:trigger class="fr-inplace-rename">
                                <xforms:label>Apply <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                            or
                            <xforms:trigger appearance="minimal" class="fr-inplace-cancel">
                                <xforms:label>Cancel</xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                        </xhtml:span>
                    </xhtml:span>
                </xhtml:span>
            </xforms:case>
            <!-- Copy other children elements, including event handlers -->
            <xsl:apply-templates select="* except (xforms:label, xforms:hint, fr:buttons)"/>
        </xforms:switch>
    </xsl:template>

    <xsl:template match="xhtml:body//xforms:textarea[@appearance = 'fr:in-place']">
        <xforms:switch id="{@id}">
            <xsl:attribute name="class" select="string-join(('fr-inplace-textarea', @class), ' ')"/>
            <xforms:case id="fr-inplace-{@id}-view">
                <xhtml:div class="fr-inplace-view">
                    <xhtml:span class="fr-inplace-content">
                        <xforms:output>
                            <!-- Handle inline hint-->
                            <xsl:call-template name="fr-handle-inplace-hint"/>
                            <!-- Keep label as is -->
                            <xsl:copy-of select="xforms:label"/>
                            <!-- React to user click -->
                            <xforms:action ev:event="DOMActivate">
                                <xforms:toggle case="fr-inplace-{@id}-edit"/>
                                <xforms:setfocus control="fr-inplace-{@id}-textarea"/>
                            </xforms:action>
                        </xforms:output>
                        <!-- Hidden output just to display alert -->
                        <xforms:output class="fr-hidden">
                            <xsl:copy-of select="@ref | @bind | xforms:alert"/>
                        </xforms:output>
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
            <xforms:case id="fr-inplace-{@id}-edit">
                <xhtml:div class="fr-inplace-edit">
                    <xhtml:span class="fr-inplace-content">
                        <xforms:textarea id="fr-inplace-{@id}-textarea" appearance="xxforms:autosize">
                            <xsl:copy-of select="@ref | @bind | @incremental | xforms:label | xforms:alert"/>
                            <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                        </xforms:textarea>
                        <xhtml:span class="fr-inplace-buttons">
                            <xforms:trigger class="fr-inplace-rename">
                                <xforms:label>Apply <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                            or
                            <xforms:trigger appearance="minimal" class="fr-inplace-cancel">
                                <xforms:label>Cancel</xforms:label>
                                <xforms:toggle ev:event="DOMActivate" case="fr-inplace-{@id}-view"/>
                            </xforms:trigger>
                        </xhtml:span>
                    </xhtml:span>
                </xhtml:div>
            </xforms:case>
        </xforms:switch>
    </xsl:template>

    <xsl:template match="xhtml:body//fr:section">
        <xsl:variable name="open" as="xs:boolean" select="if (doc('input:instance')/*/mode = 'print') then true() else if (@open = 'false') then false() else true()"/>
        <xsl:variable name="section-id" as="xs:string" select="@id"/>

        <xsl:variable name="content">
            <xsl:variable name="ancestor-sections" as="xs:integer" select="count(ancestor::fr:section)"/>
            <xforms:group id="{$section-id}">
                <xsl:attribute name="class" select="string-join(('fr-section-container', @class), ' ')"/>
                <xforms:switch id="switch-{$section-id}" context="{if (@context) then @context else '.'}" xxforms:readonly-appearance="dynamic">
                    <xforms:case id="case-{$section-id}-closed" selected="{if (not($open)) then 'true' else 'false'}">
                        <xhtml:div>
                            <xsl:element name="{if ($ancestor-sections = 0) then 'h2' else 'h3'}">
                                <xsl:attribute name="class" select="'fr-section-title'"/>

                                <xsl:variable name="action" as="element(xforms:action)">
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/id">
                                            <xsl:value-of select="$section-id"/>
                                        </xforms:setvalue>
                                        <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/repeat-indexes" value="event('xxforms:repeat-indexes')"/>
                                        <xforms:dispatch target="fr-sections-model" name="fr-expand"/>
                                    </xforms:action>
                                </xsl:variable>

                                <xforms:group appearance="xxforms:internal">
                                    <!-- "+" trigger -->
                                    <xforms:trigger appearance="minimal">
                                        <xforms:label>
                                            <xhtml:img width="12" height="12" src="/apps/fr/style/plus.png" alt="Open section" title="Open section" class="fr-open-close"/>
                                        </xforms:label>
                                        <xsl:if test="@editable = 'true'">
                                            <xsl:apply-templates select="$action"/>
                                        </xsl:if>
                                    </xforms:trigger>
                                    <!-- Display label, editable or not -->
                                    <xsl:choose>
                                        <xsl:when test="@editable = 'true'">
                                            <xsl:variable name="input" as="element(xforms:input)">
                                                <xforms:input id="{$section-id}-input-closed" ref="{xforms:label/@ref}" appearance="fr:in-place">
                                                    <xsl:apply-templates select="xforms:hint"/>
                                                    <xsl:apply-templates select="fr:buttons"/>
                                                </xforms:input>
                                            </xsl:variable>
                                            <xsl:apply-templates select="$input"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xforms:trigger appearance="minimal">
                                                <xsl:apply-templates select="xforms:label"/>
                                            </xforms:trigger>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                    <xsl:if test="not(@editable = 'true')">
                                        <xsl:apply-templates select="$action"/>
                                    </xsl:if>
                                </xforms:group>
                            </xsl:element>
                        </xhtml:div>
                    </xforms:case>
                    <xforms:case id="case-{$section-id}-open" selected="{if ($open) then 'true' else 'false'}">
                        <xhtml:div>
                            <xsl:element name="{if ($ancestor-sections = 0) then 'h2' else 'h3'}">
                                <xsl:attribute name="class" select="'fr-section-title'"/>

                                <xsl:variable name="action" as="element(xforms:action)">
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/id">
                                            <xsl:value-of select="$section-id"/>
                                        </xforms:setvalue>
                                        <xforms:setvalue model="fr-sections-model" ref="instance('fr-current-section-instance')/repeat-indexes" value="event('xxforms:repeat-indexes')"/>
                                        <xforms:dispatch target="fr-sections-model" name="fr-collapse"/>
                                    </xforms:action>
                                </xsl:variable>

                                <xforms:group appearance="xxforms:internal">
                                    <!-- "-" trigger -->
                                    <xforms:trigger appearance="minimal">
                                        <xforms:label>
                                            <xhtml:img width="12" height="12" src="/apps/fr/style/minus.png" alt="Close section" title="Close section" class="fr-open-close"/>
                                        </xforms:label>
                                        <xsl:if test="@editable = 'true'">
                                            <xsl:apply-templates select="$action"/>
                                        </xsl:if>
                                    </xforms:trigger>
                                    <!-- Display label, editable or not -->
                                    <xsl:choose>
                                        <xsl:when test="@editable = 'true'">
                                            <xsl:variable name="input" as="element(xforms:input)">
                                                <xforms:input id="{$section-id}-input-open" ref="{xforms:label/@ref}" appearance="fr:in-place">
                                                    <xsl:apply-templates select="xforms:hint"/>
                                                    <xsl:apply-templates select="fr:buttons"/>
                                                </xforms:input>
                                            </xsl:variable>
                                            <xsl:apply-templates select="$input"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xforms:trigger appearance="minimal">
                                                <xsl:apply-templates select="xforms:label"/>
                                            </xforms:trigger>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                    <xsl:if test="not(@editable = 'true')">
                                        <xsl:apply-templates select="$action"/>
                                    </xsl:if>
                                </xforms:group>
                            </xsl:element>
                            <xhtml:div class="fr-collapsible">
                                <!-- Section content except label, event handlers, and buttons -->
                                <xsl:apply-templates select="* except (xforms:label, *[@ev:*], fr:buttons)"/>
                            </xhtml:div>
                        </xhtml:div>
                    </xforms:case>
                </xforms:switch>
                <!-- Event handlers children of fr:seciton -->
                <xsl:apply-templates select="*[@ev:*]"/>
            </xforms:group>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="@ref">
                <xforms:group ref="{@ref}">
                    <xsl:apply-templates select="$content"/>
                </xforms:group>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates select="$content"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="xhtml:body//fr:grid">
        <xhtml:table class="fr-grid fr-grid-{@columns}-columns{ if (@class) then concat(' ', @class) else ()}">
            <!-- Grid content -->
            <xsl:apply-templates select="* except xforms:label" mode="grid-content"/>
        </xhtml:table>
    </xsl:template>

    <xsl:template match="fr:optional-element">
        <xforms:input ref="{@ref}">
            <xsl:apply-templates select="xforms:label"/>
        </xforms:input>
        <xforms:trigger ref=".[not({@ref})]" appearance="minimal">
            <xforms:label>
                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/add.png" alt=""/>
                Add <xsl:value-of select="lower-case(xforms:label)"/>
            </xforms:label>
            <xforms:insert ev:event="DOMActivate" origin="instance('templates')/{@ref}" nodeset="{@after}"/>
        </xforms:trigger>
        <xforms:trigger ref=".[{@ref}]" appearance="minimal">
            <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/images/silk/delete.png" alt="Remove" title="Remove"/></xforms:label>
            <xforms:delete ev:event="DOMActivate" nodeset="{@ref}"/>
        </xforms:trigger>
    </xsl:template>

    <!-- Helper for repeats -->
    <xsl:template match="xhtml:body//fr:repeat">
        <xsl:variable name="tokenized-path" select="tokenize(@nodeset, '/')"/>
        <xsl:variable name="min-occurs" select="if (@minOccurs) then @minOccurs else 0"/>
        <xsl:variable name="max-occurs" select="if (@maxOccurs) then @maxOccurs else 'unbounded'"/>
        <xsl:variable name="readonly" as="xs:boolean" select="if (@readonly) then @readonly = 'true' else false()"/>
        <xsl:variable name="first-mandatory" as="xs:boolean" select="if (@first-mandatory) then @first-mandatory = 'true' else false()"/>
        <xsl:variable name="is-table-appearance" as="xs:boolean" select="@appearance = 'xxforms:table'"/>
        <xhtml:table class="fr-repeat {if ($is-table-appearance) then 'fr-repeat-table' else 'fr-repeat-sections'} {if (@columns) then concat('fr-grid-', @columns, '-columns') else ()}">
            <!-- Line with the "add" triggers -->
            <xsl:if test="false() and not($readonly)">
                <xforms:group ref=".[not(exforms:readonly(.))]">
                    <xhtml:tr>
                        <xhtml:td class="fr-repeat-column"/>
                        <!-- Add trigger (image) -->
                        <xhtml:td class="fr-repeat-column">
                            <xforms:trigger appearance="minimal" ref=".[{if ($max-occurs = 'unbounded') then 'true()' else concat('count(', @nodeset, ') lt ', $max-occurs)}]">
                                <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/add.png" alt="Add" title="Add"/></xforms:label>
                            </xforms:trigger>
                        </xhtml:td>
                        <!-- Add trigger (text) -->
                        <xhtml:td style="width: 100%" colspan="{max(for $tr in xhtml:tr return count($tr/xhtml:td))}">
                            <xforms:trigger appearance="minimal" ref=".[{if ($max-occurs = 'unbounded') then 'true()' else concat('count(', @nodeset, ') lt ', $max-occurs)}]">
                                <xforms:label>Add <xsl:value-of select="lower-case(xforms:label)"/></xforms:label>
                            </xforms:trigger>
                        </xhtml:td>
                    </xhtml:tr>
                    <xforms:insert ev:event="DOMActivate"
                                   origin="{if (@origin) then @origin else concat('instance(''templates'')/', $tokenized-path[last()])}"
                                   context="." nodeset="{if (@after) then @after else @nodeset}"/>
                    <!-- TODO: handle @at -->
                    <!-- at="index('{@id}')" position="after" -->
                </xforms:group>
            </xsl:if>
            <!-- Line with column headers -->
            <xhtml:tr>
                <xsl:if test="not($readonly)">
                    <xforms:group ref=".[not(exforms:readonly(.))]">
                        <!-- Try to create enough space for a 2-digit number on the left -->
                        <!--<xhtml:td class="fr-repeat-column">&#160;&#160;&#160;&#160;</xhtml:td>-->
                        <xhtml:td class="fr-repeat-column"><xhtml:div style="width: 1.5em"/></xhtml:td>
                    </xforms:group>
                </xsl:if>
                <xhtml:td class="fr-repeat-column">
                    <xsl:if test="not($readonly)">
                        <xforms:group ref=".[not(exforms:readonly(.))]">
                            <xforms:trigger appearance="minimal" ref=".[{if ($max-occurs = 'unbounded') then 'true()' else concat('count(', @nodeset, ') lt ', $max-occurs)}]">
                                <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/add.png" alt="Add" title="Add"/></xforms:label>
                            </xforms:trigger>
                            <xforms:insert ev:event="DOMActivate"
                                           origin="{if (@origin) then @origin else concat('instance(''templates'')/', $tokenized-path[last()])}"
                                           context="." nodeset="{if (@after) then @after else @nodeset}"/>
                            <!-- TODO: handle @at -->
                            <!-- at="index('{@id}')" position="after" -->
                        </xforms:group>
                    </xsl:if>
                </xhtml:td>
                <xsl:for-each select="xhtml:tr[1]/xhtml:td/xforms:*[1]">
                    <xhtml:th>
                        <xsl:if test="count(../following-sibling::xhtml:td) = 0">
                            <!-- Last th gets 100% with to push things to the left -->
                            <xsl:attribute name="width" select="'100%'"/>
                        </xsl:if>
                        <xforms:output value="''" class="fr-hidden"><!-- hide the actual output control -->
                            <xsl:copy-of select="xforms:label | xforms:help | xforms:hint"/>
                        </xforms:output>
                    </xhtml:th>
                </xsl:for-each>
            </xhtml:tr>
            <xforms:repeat nodeset="{@nodeset}" id="{@id}">
                <xxforms:variable name="repeat-position" select="position()"/>
                <!-- First line with data -->
                <xhtml:tr>
                    <xhtml:th class="fr-repeat-column">
                        <xforms:output value="position()"/>
                    </xhtml:th>
                    <xsl:if test="not($readonly)">
                        <xforms:group ref=".[not(exforms:readonly(.))]">
                            <xhtml:td class="fr-repeat-column">
                                <xforms:group>
                                    <!-- Remove trigger -->
                                    <xforms:trigger appearance="minimal" ref="if (
                                            {if ($first-mandatory) then '$repeat-position != 1 and ' else ''}
                                            count(xxforms:repeat-nodeset('{@id}')) gt {$min-occurs}) then . else ()">
                                        <xforms:label><xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/bin.png" alt="Remove" title="Remove"/></xforms:label>
                                    </xforms:trigger>
                                    <xforms:delete ev:event="DOMActivate" nodeset="."/>
                                </xforms:group>
                            </xhtml:td>
                        </xforms:group>
                    </xsl:if>
                    <xsl:apply-templates select="xhtml:tr[1]/xhtml:td"/>
                </xhtml:tr>
                <!-- Following lines with data if any -->
                <xsl:apply-templates select="xhtml:tr except xhtml:tr[1] | xhtml:td" mode="prepend-td"/>
            </xforms:repeat>
            <!-- IE display HACK -->
            <xhtml:tr class="fr-repeat-last-line"><xhtml:td/></xhtml:tr>
        </xhtml:table>
    </xsl:template>

    <!-- Add Form Runner models and scripts -->
    <xsl:template match="/xhtml:html/xhtml:head/xforms:model[1]">

        <!-- This model handles form sections -->
        <!-- State handling is set to "client" for the offline mode until the server supports switching back and forth between client and server -->
        <xforms:model id="fr-sections-model"
                      xxforms:external-events="fr-after-collapse {@xxforms:external-events}"
                      xxforms:readonly-appearance="{if (doc('input:instance')/*/mode = ('view', 'print', 'pdf')) then 'static' else 'dynamic'}"
                      xxforms:order="help label control alert hint"
                      xxforms:computed-binds="recalculate"
                      xxforms:offline="true">
            <xsl:copy-of select="@* except (@id, @xxforms:external-events)"/>
            <!-- Contain section being currently expanded/collapsed -->
            <!-- TODO: This probably doesn't quite work for sections within repeats -->
            <xforms:instance id="fr-current-section-instance">
                <section xmlns="">
                    <id/>
                    <repeat-indexes/>
                </section>
            </xforms:instance>

            <!-- Handle section collapse -->
            <xforms:action ev:event="fr-after-collapse">
                <xforms:toggle case="case-{{instance('fr-current-section-instance')/id}}-closed"/>
            </xforms:action>

            <!-- Close section -->
            <xforms:action ev:event="fr-collapse">
                <xxforms:script>frCollapse();</xxforms:script>
            </xforms:action>

            <!-- Open section -->
            <xforms:action ev:event="fr-expand">
                <xforms:toggle case="case-{{instance('fr-current-section-instance')/id}}-open"/>
                <xxforms:script>frExpand();</xxforms:script>
            </xforms:action>
        </xforms:model>

        <!-- This model handles help -->
        <xforms:model id="fr-help-model">
            <xforms:instance id="fr-help-instance">
                <help xmlns="">
                    <label/>
                    <help/>
                </help>
            </xforms:instance>

            <!-- Take action upon xforms-help on #fr-form-group -->
            <!--<xforms:action ev:observer="fr-form-group" ev:event="xforms-help" ev:defaultAction="cancel">-->
                <!--<xforms:setvalue ref="instance('fr-help-instance')/label" value="event('xxforms:label')"/>-->
                <!--<xforms:setvalue ref="instance('fr-help-instance')/help" value="event('xxforms:help')"/>-->
            <!--</xforms:action>-->

        </xforms:model>

        <!-- This model handles i18n resources -->
        <xi:include href="i18n/resources-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles offline functionality through Google Gears -->
        <xi:include href="offline/offline-model.xml" xxi:omit-xml-base="true"/>
        <!-- Handle error summary -->
        <xi:include href="includes/error-summary-model.xml" xxi:omit-xml-base="true"/>
        <!-- Handle document persistence -->
        <xi:include href="includes/persistence-model.xml" xxi:omit-xml-base="true"/>

        <!-- This model handles print functionality -->
        <xforms:model id="fr-print-model">
            <xforms:instance id="fr-print-instance"><dummy/></xforms:instance>
            <xxforms:variable name="parameters" select="xxforms:instance('fr-parameters-instance')"/>

            <xforms:submission id="fr-print-submission"
                resource="/fr/{{$parameters/app}}/{{$parameters/form}}/print?fr-language={{xxforms:instance('fr-language-instance')}}"
                method="post" ref="xxforms:instance('fr-form-instance')" replace="all" validate="false"
                xxforms:target="_blank" xxforms:show-progress="false"/>

            <xforms:submission id="fr-pdf-submission"
                resource="/fr/{{$parameters/app}}/{{$parameters/form}}/pdf?fr-language={{xxforms:instance('fr-language-instance')}}"
                method="post" ref="xxforms:instance('fr-form-instance')" replace="all" validate="false"
                xxforms:target="_blank" xxforms:show-progress="false"/>

            <xforms:submission id="fr-pdf-template-submission"
                resource="/fr/{{$parameters/app}}/{{$parameters/form}}/pdf-template?document={{xxforms:instance('fr-parameters-instance')/document}}&amp;fr-language={{xxforms:instance('fr-language-instance')}}"
                method="post" ref="xxforms:instance('fr-form-instance')" replace="all" validate="false"
                xxforms:target="_blank" xxforms:show-progress="false"/>
        </xforms:model>

        <!-- Handle import/export -->
        <xi:include href="import-export/import-export-model.xml" xxi:omit-xml-base="true"/>

        <!-- Copy existing main model -->
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>

        <!-- Copy other models present in the UI -->
        <xsl:copy-of select="/xhtml:html/xhtml:body//xforms:model"/>

        <!-- Handle collapsible sections -->
        <xi:include href="includes/collapse-script.xhtml" xxi:omit-xml-base="true"/>
        <!-- Handle checking dirty status -->
        <xi:include href="includes/check-dirty-script.xhtml" xxi:omit-xml-base="true"/>

    </xsl:template>

    <!-- Filter out models in the UI as they are copied separately -->
    <xsl:template match="xhtml:body//xforms:model"/>

    <!-- Add a default xforms:alert for those fields which don't have one -->
    <xsl:template match="xhtml:body//xforms:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload') and not(xforms:alert) and not(@appearance = 'fr:in-place')]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xforms:alert ref="$fr-resources/detail/labels/alert"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xhtml:td" mode="grid-content">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:attribute name="class" select="string-join(('fr-grid-td', @class), ' ')"/>
            <xhtml:div class="fr-grid-content">
                <xsl:apply-templates/>
            </xhtml:div>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="xhtml:tr" mode="prepend-td">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xhtml:td/>
            <xhtml:td/>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
