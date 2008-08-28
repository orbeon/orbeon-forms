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
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:pipeline="org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <xsl:template match="xhtml:body//fr:view">

        <xsl:if test="@width and not(@width = ('750px', '950px', '974px', '1154px'))">
            <xsl:message terminate="yes">Value of fr:view/@view is not valid</xsl:message>
        </xsl:if>
        <xhtml:div id="fr-view">
            <xhtml:div id="{if (@width = '750px') then 'doc' else if (@width = '950px') then 'doc2' else if (@width = '1154px') then 'doc-fb' else 'doc4'}"
                       class="{if (doc('input:instance')/*/mode = 'print') then ' fr-print-mode' else ''}">
                <xhtml:div class="fr-header">
                    <xsl:if test="not(doc('input:instance')/*/mode = ('print', 'pdf'))">
                        <!-- Switch script/noscript -->
                        <xsl:if test="not($has-noscript-link = false()) and not($is-form-builder)">
                            <xhtml:div class="fr-noscript-choice" style="float: left">
                                <xforms:group ref=".[not(property('xxforms:noscript'))]">
                                    <xhtml:a title="{{$fr-resources/summary/titles/refresh}}" href="?fr-noscript=true"><xforms:output value="$fr-resources/summary/labels/noscript"/></xhtml:a>
                                    <!--<xhtml:img class="fr-noscript-icon" width="16" height="16" src="/apps/fr/style/images/silk/script_delete.png" alt="Noscript Mode" title="Noscript Mode"/>-->
                                </xforms:group>
                                <xforms:group ref=".[property('xxforms:noscript')]">
                                    <xhtml:a title="{{$fr-resources/summary/titles/refresh}}" href="?"><xforms:output value="$fr-resources/summary/labels/script"/></xhtml:a>
                                </xforms:group>
                            </xhtml:div>
                        </xsl:if>
                        <!-- Switch language -->
                        <xhtml:div class="fr-language-choice">
                            <xxforms:variable name="default-language" select="xxforms:instance('fr-default-language-instance')"/>
                            <!-- Put default language first, then other languages -->
                            <xxforms:variable name="available-languages"
                                              select="(xxforms:instance('fr-form-resources')/resource[@xml:lang = $default-language]/@xml:lang,
                                                        xxforms:instance('fr-form-resources')/resource[not(@xml:lang = $default-language)]/@xml:lang)"/>

                            <!-- Group below implements a sort of xforms:select1[@appearance = 'xxforms:full']. Should be componentized, e.g.: -->
                            <!--<fb:select1 appearance="xxforms:full" ref="instance('fr-language-instance')">-->
                                <!--<xforms:itemset nodeset="$available-languages">-->
                                    <!--<xforms:label ref="(instance('fr-languages-instance')/language[@code = context()]/@native-name, context())[1]"/>-->
                                    <!--<xforms:value ref="."/>-->
                                <!--</xforms:itemset>-->
                            <!--</fb:select1>-->

                            <xforms:group id="fr-language-selector">
                                <xforms:repeat model="fr-resources-model" nodeset="$available-languages">
                                    <xxforms:variable name="position" select="position()"/>
                                    <xxforms:variable name="label" select="(instance('fr-languages-instance')/language[@code = context()]/@native-name, context())[1]"/>
                                    <xxforms:variable name="value" select="context()"/>
                                    <xforms:group ref=".[$position > 1]"> | </xforms:group>
                                    <xforms:trigger ref=".[$value != instance('fr-language-instance')]" appearance="minimal">
                                        <xforms:label value="$label"/>
                                        <xforms:action ev:event="DOMActivate">
                                            <xforms:setvalue ref="instance('fr-language-instance')" value="$value"/>
                                        </xforms:action>
                                    </xforms:trigger>
                                    <xforms:output ref=".[$value = instance('fr-language-instance')]" value="$label"/>
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
                            <!-- description in chosen language or first one if not found -->
                            <xxforms:variable name="description"
                                              select="($source-form-metadata/description[@xml:lang = $metadata-lang],
                                                        $source-form-metadata/description[1],
                                                        instance('fr-form-metadata')/description[@xml:lang = $metadata-lang],
                                                        instance('fr-form-metadata')/description[1])[1]"/>

                            <!--xxx noscript xxx-->
                            <!--<xforms:output value="property('xxforms:noscript')"/>                            -->

                            <!--<xforms:output value="string-join(($source-form-metadata/description[@xml:lang = $metadata-lang], $source-form-metadata/description[1]), ' - ')"/>-->
                            <!--xxx-->
                            <!--<xforms:output value="string-join($source-form-metadata/(description[@xml:lang = $metadata-lang], description[1]), ' - ')"/>-->
                            <xhtml:div class="yui-g fr-metadata">
                                <xsl:choose>
                                    <!-- If custom logo section is provided, use that -->
                                    <xsl:when test="fr:logo">
                                        <xsl:apply-templates select="fr:logo/node()"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xforms:group model="fr-form-model" appearance="xxforms:internal">
                                            <xhtml:table class="fr-layout-table">
                                                <xhtml:tr>
                                                    <xhtml:td rowspan="2">
                                                        <xsl:if test="$default-logo-uri">
                                                            <xforms:output class="fr-logo" value="((instance('fr-form-metadata')/logo, concat(pipeline:property('oxf.fr.appserver.uri'), '{$default-logo-uri}'))[normalize-space() != ''])[1]" mediatype="image/*"/>
                                                        </xsl:if>
                                                    </xhtml:td>
                                                    <xhtml:td>
                                                        <xhtml:h1 class="fr-form-title">
                                                            <xforms:output value="$title"/>
                                                        </xhtml:h1>
                                                    </xhtml:td>
                                                </xhtml:tr>
                                                <xhtml:tr>
                                                    <xhtml:td>
                                                        <xforms:output class="fr-form-description" value="$description"/>
                                                    </xhtml:td>
                                                </xhtml:tr>
                                            </xhtml:table>
                                        </xforms:group>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xhtml:div>
                            <xhtml:div class="yui-g fr-separator">&#160;</xhtml:div>
                            <xhtml:div class="yui-g fr-body">

                                <!-- Table of contents -->
                                <xsl:call-template name="fr-toc"/>

                                <!-- Optional description-->
                                <!--<xforms:group model="fr-form-model" ref=".[normalize-space($description) != '']">-->
                                    <!--<xforms:switch>-->
                                        <!--<xforms:case id="fr-form-description-case-on">-->
                                            <!--<xhtml:div class="fr-form-description">-->
                                                <!--<xforms:output value="$description"/>-->
                                                <!--<xforms:trigger appearance="minimal" class="fr-close">-->
                                                    <!--<xforms:label ref="$fr-resources/summary/labels/close-box"/>-->
                                                    <!--<xforms:toggle ev:event="DOMActivate" case="fr-form-description-case-off"/>-->
                                                <!--</xforms:trigger>-->
                                            <!--</xhtml:div>-->
                                        <!--</xforms:case>-->
                                        <!--<xforms:case id="fr-form-description-case-off"/>-->
                                    <!--</xforms:switch>-->
                                <!--</xforms:group>-->

                                <!-- Error summary (if at top) -->
                                <xsl:if test="normalize-space($error-summary) = ('top', 'both')">
                                    <xsl:call-template name="fr-error-summary">
                                        <xsl:with-param name="position" select="'top'"/>
                                    </xsl:call-template>
                                </xsl:if>

                                <!-- Form content. Set context on form instance and define this group as #fr-form-group as observers will refer to it. -->
                                <xforms:group id="fr-form-group" model="fr-form-model" ref="instance('fr-form-instance')">
                                    <!-- Main form content -->
                                    <xsl:apply-templates select="fr:body/node()"/>
                                </xforms:group>

                                <!-- Error summary (if at bottom) -->
                                <xsl:if test="normalize-space($error-summary) = ('', 'bottom', 'both')">
                                    <xsl:call-template name="fr-error-summary">
                                        <xsl:with-param name="position" select="'bottom'"/>
                                    </xsl:call-template>
                                </xsl:if>
                            </xhtml:div>
                            <xhtml:div class="yui-g fr-separator">&#160;</xhtml:div>
                            <xhtml:div class="yui-g fr-buttons-block">
                                <xforms:group model="fr-persistence-model" appearance="xxforms:internal">

                                    <!-- Status icons for detail page -->
                                    <xsl:if test="$is-detail">
                                        <xhtml:div class="fr-status-icons">
                                            <xforms:group model="fr-error-summary-model" ref=".[instance('fr-form-valid-instance') = 'false']">
                                                <!-- Form is invalid -->
                                                <!-- TODO: i18n of title -->
                                                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/exclamation.png" alt="Errors on Form" title="Errors on Form"/>
                                            </xforms:group>
                                            <xforms:group model="fr-error-summary-model" ref=".[instance('fr-form-valid-instance') = 'true']">
                                                <!-- Form is valid -->
                                                <!-- TODO: i18n of title -->
                                                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/tick.png" alt="No Errors on Form" title="No Errors on Form"/>
                                            </xforms:group>
                                            <xforms:group ref="instance('fr-persistence-instance')[data-status = 'dirty']">
                                                <!-- Data is dirty -->
                                                <!-- TODO: i18n of title -->
                                                <xhtml:img width="16" height="16" src="/apps/fr/style/images/silk/disk.png" alt="Unsaved Changes" title="Unsaved Changes"/>
                                                <!--<xhtml:p class="fr-unsaved-data">-->
                                                    <!-- TODO: i18n -->
                                                    <!--Your document contains unsaved changes.-->
                                                <!--</xhtml:p>-->
                                            </xforms:group>
                                        </xhtml:div>
                                    </xsl:if>

                                    <!-- Messages -->
                                    <xforms:group class="fr-messages" model="fr-persistence-model" ref=".[instance('fr-persistence-instance')/message != '']">
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
                                                    <!-- We can't show the dialog in noscript mode so don't show the trigger then -->
                                                    <xforms:trigger ref=".[not(property('xxforms:noscript')) and normalize-space(instance('fr-persistence-instance')/error) != '']" appearance="minimal">
                                                        <!-- TODO: i18n -->
                                                        <xforms:label>[Details]</xforms:label>
                                                        <xxforms:show ev:event="DOMActivate" dialog="fr-error-details-dialog"/>
                                                    </xforms:trigger>
                                                </xhtml:p>
                                            </xforms:case>
                                        </xforms:switch>
                                    </xforms:group>

                                    <!-- Buttons -->
                                    <xhtml:div class="fr-buttons">
                                        <xsl:choose>
                                            <!-- In print and test modes, only include a close button -->
                                            <xsl:when test="doc('input:instance')/*/mode = ('print', 'test')">
                                                <xsl:variable name="default-buttons" as="element(fr:buttons)">
                                                    <fr:buttons>
                                                        <xsl:if test="$has-button-close">
                                                            <fr:close-button/>
                                                        </xsl:if>
                                                    </fr:buttons>
                                                </xsl:variable>
                                                <xsl:apply-templates select="$default-buttons/*"/>
                                            </xsl:when>
                                            <!-- In view mode  -->
                                            <xsl:when test="doc('input:instance')/*/mode = ('view')">
                                                <xsl:variable name="default-buttons" as="element(fr:buttons)">
                                                    <fr:buttons>
                                                        <xsl:if test="$has-button-close">
                                                            <fr:back-button/>
                                                        </xsl:if>
                                                        <xsl:if test="$has-button-pdf">
                                                            <fr:pdf-button/>
                                                        </xsl:if>
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
                                                        <fr:refresh-button/>
                                                        <xsl:if test="$has-button-close">
                                                            <fr:back-button/>
                                                        </xsl:if>
                                                        <xsl:if test="$has-button-clear">
                                                            <fr:clear-button/>
                                                        </xsl:if>
                                                        <xsl:if test="$has-button-print">
                                                            <fr:print-button/>
                                                        </xsl:if>
                                                        <xsl:if test="$has-button-pdf">
                                                            <fr:pdf-button/>
                                                        </xsl:if>
                                                        <!-- These buttons are disabled until we can save initial changes to the DOM in store and
                                                              replay them when the form is first loaded offline -->
                                                        <!--<fr:take-offline/>-->
                                                        <!--<fr:take-online/>-->
                                                        <!--<fr:save-offline/>-->
                                                        <xsl:if test="$has-button-save-locally">
                                                            <fr:save-locally-button/>
                                                        </xsl:if>
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
                        <xsl:when test="not($has-version = false())">
                            <xsl:variable xmlns:version="java:org.orbeon.oxf.common.Version" name="orbeon-forms-version" select="version:getVersion()" as="xs:string"/>
                            <xhtml:div class="fr-orbeon-version">Orbeon Forms <xsl:value-of select="$orbeon-forms-version"/></xhtml:div>
                        </xsl:when>
                    </xsl:choose>
                </xhtml:div>
            </xhtml:div>

            <xsl:if test="fr:left">
                <xhtml:div>
                    <xsl:apply-templates select="fr:left/node()"/>
                </xhtml:div>
            </xsl:if>

        </xhtml:div>
        <xi:include href="../import-export/import-export-dialog.xml" xxi:omit-xml-base="true"/>
        <xi:include href="../includes/clear-dialog.xhtml" xxi:omit-xml-base="true"/>

        <!-- Error Details dialog -->
        <xxforms:dialog id="fr-error-details-dialog">
            <xforms:label>Error Details</xforms:label>
            <xhtml:div>
                <xhtml:div class="fr-dialog-message">
                    <xforms:output mediatype="text/html" model="fr-persistence-model" value="instance('fr-persistence-instance')/error"/>
                </xhtml:div>
            </xhtml:div>
            <xhtml:div class="fr-dialog-buttons">
                <xforms:group>
                    <xxforms:hide ev:event="DOMActivate" dialog="fr-error-details-dialog"/>
                    <xforms:trigger>
                        <xforms:label>
                            <xhtml:img src="/apps/fr/style/close.gif" alt=""/>
                            <xhtml:span><xforms:output value="$fr-resources/detail/labels/close"/></xhtml:span>
                        </xforms:label>
                    </xforms:trigger>
                </xforms:group>
            </xhtml:div>
        </xxforms:dialog>

        <xhtml:span class="fr-hidden">
            <!-- Hidden field to communicate to the client the current section to collapse or expand -->
            <xforms:input model="fr-sections-model" ref="instance('fr-current-section-instance')/id" id="fr-current-section-id-input" class="xforms-disabled"/>
            <xforms:input model="fr-sections-model" ref="instance('fr-current-section-instance')/repeat-indexes" id="fr-current-section-repeat-indexes-input" class="xforms-disabled"/>
            <!-- Hidden field to communicate to the client whether the data is clean or dirty -->
            <xforms:input model="fr-persistence-model" ref="instance('fr-persistence-instance')/data-status" id="fr-data-status-input" class="xforms-disabled"/>
        </xhtml:span>

    </xsl:template>

    <!-- Error summary UI -->
    <xsl:template name="fr-error-summary">
        <xsl:param name="position" as="xs:string"/>
        <xforms:group class="fr-error-summary fr-error-summary-{$position}" model="fr-persistence-model" ref=".[xxforms:instance('fr-errors-instance')/error]">
            <xsl:if test="$position = 'bottom'">
                <xhtml:div class="fr-separator">&#160;</xhtml:div>
            </xsl:if>
            <xforms:group class="fr-error-summary-body" model="fr-error-summary-model" ref="instance('fr-errors-instance')[error[@id = instance('fr-visited-instance')/control/@id]]">
                <xforms:output class="fr-error-title" value="$fr-resources/summary/titles/errors"/>
                <xhtml:ol class="fr-error-list">
                    <xforms:repeat nodeset="error">
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
            <!--<xforms:group model="fr-error-summary-model" ref="instance('fr-errors-instance')[not(error)]">-->
            <!--</xforms:group>-->
            <xsl:if test="$position = 'top'">
                <xhtml:div class="fr-separator">&#160;</xhtml:div>
            </xsl:if>
        </xforms:group>
    </xsl:template>

    <!-- Table of contents UI -->
    <xsl:template name="fr-toc">
        <!-- This is statically built in XSLT instead of using XForms -->
        <xsl:if test="$has-toc and $is-detail and not($is-form-builder)">
            <xhtml:div class="fr-toc">
                <xhtml:h2>
                    <xforms:output value="$fr-resources/summary/titles/toc"/>
                </xhtml:h2>
                <xhtml:ol>
                    <xsl:for-each select="/xhtml:html/xhtml:body//fr:section">
                        <xhtml:li>
                            <xhtml:a href="#{@id}"><xforms:output value="{xforms:label/@ref}"/></xhtml:a>
                            <!-- Will have to add sub-sections when necessary -->
                        </xhtml:li>
                    </xsl:for-each>
                </xhtml:ol>
            </xhtml:div>
            <xhtml:div class="fr-separator">&#160;</xhtml:div>
        </xsl:if>
    </xsl:template>

    <!-- Add a default xforms:alert for those fields which don't have one -->
    <xsl:template match="xhtml:body//xforms:*[local-name() = ('input', 'textarea', 'select', 'select1', 'upload') and not(xforms:alert) and not(@appearance = 'fr:in-place')]">
        <xsl:copy>
            <xsl:apply-templates select="@* | node()"/>
            <xforms:alert ref="$fr-resources/detail/labels/alert"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
