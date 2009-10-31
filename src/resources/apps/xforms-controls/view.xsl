<?xml version="1.0" encoding="UTF-8"?>
<!--
  Copyright (C) 2009 Orbeon, Inc.

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
        xmlns:xbl="http://www.w3.org/ns/xbl"
        
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
        xmlns:component="http://orbeon.org/oxf/xml/form-builder/component/orbeon/library">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
    <xsl:import href="oxf:/apps/fr/components/repeat.xsl"/>

    <!-- Add styles to head -->
    <xsl:template match="xhtml:head">
        <xsl:copy>
            <xsl:apply-templates/>
            <!-- Reuse Form Runner base CSS -->
            <xhtml:link rel="stylesheet" href="/ops/yui/reset-fonts-grids/reset-fonts-grids.css" type="text/css" media="all"/>
            <xhtml:link rel="stylesheet" href="/apps/fr/style/form-runner-base.css" type="text/css" media="all"/>
            <!-- Add our own CSS tweaks -->
            <xhtml:style type="text/css">
                /* Not sure why the YUI resets this to center */
                body { text-align: left }
                #maincontent { line-height: 1.231 }
                /* More padding within tabs */
                .yui-skin-sam .yui-navset .yui-content, .yui-skin-sam .yui-navset .yui-navset-top .yui-content {
                    padding-top: .5em; padding-bottom: 1em;
                    /*min-height: 40em;*/
                }

                .ygtvfocus { background-color: transparent }
                .edit-items-trigger { font-size: smaller }
                #fb-itemset-editor-dialog .xbl-fr-link-select1 { margin-bottom: .5em }
                #fb-itemset-editor-dialog .xbl-fr-link-select1 .xforms-label { display: none }

                .fr-grid-content .xforms-label,
                    .fr-grid-content .xbl-fr-button,
                    .fr-grid .fr-grid-content button.xforms-trigger  { margin-top: .7em }
                .fr-grid .fr-grid-content .xbl-fr-button button.xforms-trigger  { margin-top: 0 }
                .fr-grid-content textarea.xforms-textarea, .xforms-textarea textarea { height: 9em }

                .xbl-fr-link-select1 { display: block; text-align: right }
                .xbl-fr-link-select1 .xforms-label { margin-right: .5em }

                /* ***** Styles from form-runner-orbeon.css ***********************************************************/
                .xforms-input input, textarea.xforms-textarea, input.xforms-secret, .xforms-textarea textarea, .xforms-secret input {
                    border: 1px solid #ccc;
                }

                .xforms-label { font-weight: bold; }

                /* Colored border for invalid fields */
                /* NOTE: style required-empty as well to show user which are the required fields */
                .xforms-invalid-visited .xforms-input-input, textarea.xforms-invalid-visited,
                    .xforms-required-empty .xforms-input-input, textarea.xforms-required-empty,
                    input.xforms-required-empty, .xforms-required-empty input
                        { border-color: #DF731B }
                
                .xforms-alert-active {
                    font-weight: bold;
                    font-size: smaller;
                    background-color: #DF731B;
                    color: white;
                    border-radius: 3px; -moz-border-radius: 2px; -webkit-border-radius: 3px; -ms-border-radius: 2px; /* radius appears differently w/ Safari 3.1 vs. Firefox 3.0b4*/
                }

                .xforms-hint {
                    display: block;
                    font-size: smaller;
                    margin-top: .2em;
                    margin-left: 0;/* used to have margin here but with new colors no margin seems better */
                    width: 100%;
                    color: #6E6E6E;
                    font-style: italic
                }
            </xhtml:style>
        </xsl:copy>
    </xsl:template>

    <!-- Put our own title -->
    <xsl:template match="xhtml:head/xhtml:title">
        <xhtml:title>Orbeon Forms Controls</xhtml:title>
    </xsl:template>

    <!-- Annotate xforms:model -->
    <xsl:template match="xhtml:head/xforms:model">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates/>

            <!-- Current language -->
            <xforms:instance id="fr-language-instance">
                <language>en</language>
            </xforms:instance>

            <!-- List of languages and associated names -->
            <xforms:instance id="fr-languages-instance">
                <languages>
                    <language code="en" native-name="English"/>
                    <language code="fr" native-name="Français"/>
                </languages>
            </xforms:instance>

            <!-- Add minimal resources -->
            <xforms:instance id="fr-fr-resources" xxforms:readonly="true">
                <resources>
                    <resource xml:lang="en">
                        <detail>
                            <labels>
                                <language>Language:</language>
                                <alert>Invalid value</alert>
                            </labels>
                        </detail>
                    </resource>
                    <resource xml:lang="fr">
                        <detail>
                            <labels>
                                <language>Langue:</language>
                                <alert>Valeur invalide</alert>
                            </labels>
                        </detail>
                    </resource>
                </resources>
            </xforms:instance>

            <!-- Form Builder resources for itemset editor -->
            <xforms:instance id="fb-resources" resource="oxf:/forms/orbeon/builder/form/resources.xml" xxforms:readonly="true" xxforms:cache="true"/>

            <!-- Initial instance -->
            <xforms:send ev:event="xforms-model-construct-done" submission="load-submission"/>
            <xforms:submission id="load-submission" serialization="none"
                               method="get" resource="oxf:/apps/xforms-controls/initial-instance.xml"
                               replace="instance" targetref="instance('fr-form-instance')"/>

            <!-- Perform background submission upon xforms-select for xforms:upload -->
            <xforms:action ev:observer="fr-form-group" ev:event="xforms-select"
                    if="for $b in xxforms:event('xxforms:binding') return $b/(@filename and @mediatype and @size)">
                <xforms:send submission="fr-upload-attachment-submission"/>
            </xforms:action>

            <xforms:submission id="fr-upload-attachment-submission"
                               ref="xxforms:instance('fr-form-instance')" validate="false" relevant="false"
                               method="post" replace="none" resource="test:"/>
        </xsl:copy>
    </xsl:template>

    <!-- Swallow readonly attribute on form resources, so we can edit itemsets -->
    <xsl:template match="xhtml:head/xforms:model/xforms:instance[@id = 'fr-form-resources']/@xxforms:readonly"/>

    <!-- Annotate body -->
    <xsl:template match="xhtml:body">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            <xsl:attribute name="class" select="string-join((tokenize(@class, '\s+'), 'xforms-disable-hint-as-tooltip'), ' ')"/>


            <xhtml:div id="doc4">
                <!-- Expose resources variables -->
                <xxforms:variable name="fr-resources" select="instance('fr-fr-resources')/resource[@xml:lang = instance('fr-language-instance')]"/>
                <xxforms:variable name="form-resources" select="instance('fr-form-resources')/resource[@xml:lang = instance('fr-language-instance')]"/>

                <!-- Language selector -->
                <fr:link-select1 ref="instance('fr-language-instance')">
                    <xforms:label ref="$fr-resources/detail/labels/language"/>
                    <xforms:itemset nodeset="instance('fr-form-resources')/resource">
                        <xforms:label value="xxforms:instance('fr-languages-instance')/language[@code = context()/@xml:lang]/@native-name"/>
                        <xforms:value ref="@xml:lang"/>
                    </xforms:itemset>
                </fr:link-select1>
                
                <!-- Form -->
                <xforms:group id="fr-form-group" appearance="xxforms:internal">
                    <xsl:apply-templates/>
                </xforms:group>
            </xhtml:div>

            <!-- Import Form Builder itemset editor -->
            <xsl:variable name="itemset-dialog" as="element()">
                <xsl:copy-of select="doc('oxf:/forms/orbeon/builder/form/dialog-itemsets.xml')/*"/>
            </xsl:variable>
            <xxforms:variable name="form-resources" select="instance('fb-resources')//resource[@xml:lang = instance('fr-language-instance')]"/>
            <xxforms:variable name="fb-lang" select="instance('fr-language-instance')"/>
            <xxforms:variable name="resources" select="instance('fr-form-resources')"/>
            <xxforms:variable name="current-resources" select="instance('fr-form-resources')/resource[@xml:lang = instance('fr-language-instance')]"/>
            <xsl:apply-templates select="$itemset-dialog"/>

            <!--<widget:xforms-instance-inspector xmlns:widget="http://orbeon.org/oxf/xml/widget"/>-->
        </xsl:copy>
    </xsl:template>

    <!-- Place tabview -->
    <xsl:template match="fr:view/fr:body">
        <fr:tabview>
            <xsl:apply-templates/>
        </fr:tabview>
    </xsl:template>

    <!-- Each section becomes a tab -->
    <xsl:template match="fr:section">
        <fr:tab>
            <fr:label>
                <xsl:copy-of select="xforms:label/@*"/>
            </fr:label>
            <xsl:apply-templates select="node() except (xforms:label, xforms:help)"/>
        </fr:tab>
    </xsl:template>

    <!-- Simple grids -->
    <xsl:template match="fr:grid">
        <xhtml:table class="fr-grid fr-grid-{@columns}-columns">
            <xsl:for-each select="xhtml:tr | fr:tr">
                <xhtml:tr>
                    <xsl:for-each select="xhtml:td | fr:td">
                        <xhtml:td class="fr-grid-td">
                            <div class="fr-grid-content">
                                <xsl:apply-templates/>
                            </div>
                        </xhtml:td>
                    </xsl:for-each>
                </xhtml:tr>
            </xsl:for-each>
        </xhtml:table>
    </xsl:template>

    <!-- Add "Edit Items" button to each control with an itemset -->
    <xsl:template match="fr:grid//*[xforms:itemset]">
        <xsl:next-match/>
        <xforms:trigger appearance="minimal" class="edit-items-trigger">
            <xforms:label>Edit Items</xforms:label>
            <xforms:action ev:event="DOMActivate">
                <xxforms:show dialog="fb-itemset-editor-dialog">
                    <xxforms:context name="fb:resource-id" select="'{substring-before(@id, '-control')}'"/>
                </xxforms:show>
            </xforms:action>
        </xforms:trigger>
    </xsl:template>

    <!-- Filter out what we don't want -->
    <xsl:template match="fr:view | fr:view/xforms:label | fr:section/xforms:label | fr:section/xforms:help | fr:section[exists(component:*)]">
        <xsl:apply-templates/>
    </xsl:template>

</xsl:stylesheet>
