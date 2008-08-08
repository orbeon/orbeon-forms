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

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- Import components -->
    <xsl:import href="view.xsl"/>
    <xsl:import href="buttons.xsl"/>
    <xsl:import href="grid.xsl"/>
    <xsl:import href="repeat.xsl"/>
    <xsl:import href="inplace.xsl"/>
    <xsl:import href="section.xsl"/>

    <!-- Global variables -->
    <xsl:variable name="app" select="doc('input:instance')/*/app" as="xs:string"/>
    <xsl:variable name="form" select="doc('input:instance')/*/form" as="xs:string"/>
    <xsl:variable name="mode" select="doc('input:instance')/*/mode" as="xs:string?"/>
    <xsl:variable name="is-detail" select="doc('input:instance')/*/mode != ''" as="xs:boolean"/>
    <xsl:variable name="is-form-builder" select="$app = 'orbeon' and $form = 'builder'" as="xs:boolean"/>
    <xsl:variable name="is-noscript" select="not($is-form-builder) and doc('input:request')/request/parameters/parameter[name = 'fr-noscript']/value = 'true'"/>

    <!-- Properties -->
    <xsl:variable name="has-noscript-link" select="pipeline:property(string-join(('oxf.fr.noscript-link', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="has-toc" select="pipeline:property(string-join(('oxf.fr.detail.toc', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="error-summary" select="pipeline:property(string-join(('oxf.fr.detail.error-summary', $app, $form), '.'))" as="xs:string?"/>
    <xsl:variable name="is-noscript-table" select="not(string-join(('oxf.fr.detail.noscript.table', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="default-logo-uri" select="pipeline:property(string-join(('oxf.fr.default-logo.uri', $app, $form), '.'))" as="xs:string?"/>

    <xsl:variable name="has-button-save-locally" select="pipeline:property(string-join(('oxf.fr.detail.button.save-locally', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="has-button-close" select="pipeline:property(string-join(('oxf.fr.detail.button.close', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="has-button-clear" select="pipeline:property(string-join(('oxf.fr.detail.button.clear', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="has-button-print" select="pipeline:property(string-join(('oxf.fr.detail.button.print', $app, $form), '.'))" as="xs:boolean?"/>
    <xsl:variable name="has-button-pdf" select="pipeline:property(string-join(('oxf.fr.detail.button.pdf', $app, $form), '.'))" as="xs:boolean?"/>
    
    <xsl:variable name="components-uri" select="pipeline:property(string-join(('oxf.fb.components.uri', $app, $form), '.'))" as="xs:string?"/>

    <xsl:template match="/xhtml:html/xhtml:body">
        <xsl:copy>
            <xsl:attribute name="class" select="string-join(('xforms-disable-hint-as-tooltip', 'xforms-disable-alert-as-tooltip', @class), ' ')"/>
            <xsl:apply-templates select="@* except @class"/>
            <xforms:group model="fr-form-model" appearance="xxforms:internal">
                <xsl:apply-templates select="node()"/>
                <!--<widget:xforms-instance-inspector xmlns:widget="http://orbeon.org/oxf/xml/widget"/>-->
            </xforms:group>
        </xsl:copy>
    </xsl:template>

    <!-- Handle document language -->
    <xsl:template match="/xhtml:html">
        <xhtml:html lang="{{xxforms:instance('fr-language-instance')}}"
                    xml:lang="{{xxforms:instance('fr-language-instance')}}">
            <xsl:apply-templates select="@*"/>

            <!-- Global XForms variables -->
            <xxforms:variable name="metadata-lang" select="xxforms:instance('fr-language-instance')"/>
            <xxforms:variable name="source-form-metadata" select="xxforms:instance('fr-source-form-instance')/xhtml:head/xforms:model/xforms:instance[@id = 'fr-form-metadata']/*"/>
            <!-- Scope variable with Form Runner resources -->
            <xxforms:variable name="fr-resources" select="xxforms:instance('fr-fr-current-resources')"/>
            <!-- Scope form resources -->
            <xxforms:variable name="form-resources" select="xxforms:instance('fr-current-form-resources')"/>

            <!-- Title in chosen language from metadata, view, or HTML title -->
            <!-- Title is used later  -->
            <xsl:variable name="view-label" select="(/xhtml:html/xhtml:body//fr:view)[1]/xforms:label" as="element(xforms:label)?"/>
            <xxforms:variable name="title"
                              select="(($source-form-metadata/title[@xml:lang = $metadata-lang],
                                        $source-form-metadata/title[1],
                                        instance('fr-form-metadata')/title[@xml:lang = $metadata-lang],
                                        instance('fr-form-metadata')/title[1],
                                        ({$view-label/@ref}),
                                        '{$view-label}',
                                        /xhtml:html/xhtml:head/xhtml:title)[normalize-space() != ''])[1]"/>

            <xsl:apply-templates select="node()"/>
        </xhtml:html>
    </xsl:template>

    <!-- Insert stylesheets -->
    <xsl:template match="xhtml:head">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- Form Runner CSS stylesheets -->
            <xhtml:link rel="stylesheet" href="/ops/css/yui/reset-fonts-grids.css" type="text/css"/>
            <xhtml:link rel="stylesheet" href="/ops/css/yui/base-min.css" type="text/css"/>
            <xhtml:link rel="stylesheet" href="/apps/fr/style/form-runner-base.css" type="text/css"/>
            <xhtml:link rel="stylesheet" href="/apps/fr/style/form-runner-orbeon.css" type="text/css"/>

            <!-- Handle existing stylesheets -->
            <xsl:for-each select="xhtml:link | xhtml:style">
                <xsl:element name="xhtml:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:apply-templates select="@*|node()"/>
                </xsl:element>
            </xsl:for-each>

            <!-- Process the rest -->
            <xsl:apply-templates select="node() except (xhtml:link | xhtml:style)"/>
        </xsl:copy>
    </xsl:template>

    <!-- Set XHTML title -->
    <xsl:template match="xhtml:head/xhtml:title">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <!-- Display localized errors count and form title -->
            <xxforms:variable name="errors" select="count(xxforms:instance('fr-errors-instance')/error)" as="xs:integer"/>
            <xforms:output value="if ($errors > 0) then concat($errors, ' ', $fr-resources/summary/titles/(if ($errors = 1) then error-count else errors-count), ' - ', $title) else $title"/>
        </xsl:copy>
    </xsl:template>

    <!-- Add Form Runner models and scripts -->
    <xsl:template match="/xhtml:html/xhtml:head/xforms:model[1]">
        <!-- Load components -->
        <xsl:if test="$components-uri">
            <xi:include href="{$components-uri}" xxi:omit-xml-base="true"/>
        </xsl:if>

        <!-- This model handles help -->
        <xforms:model id="fr-help-model"
                      xxforms:external-events="fr-after-collapse {@xxforms:external-events}"
                      xxforms:readonly-appearance="{if ($mode = ('view', 'print', 'pdf')) then 'static' else 'dynamic'}"
                      xxforms:order="help label control alert hint"
                      xxforms:computed-binds="recalculate"
                      xxforms:offline="false"
                      xxforms:noscript="{$is-noscript}">
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

        <!-- This model handles form sections -->
        <xi:include href="../includes/sections-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles i18n resources -->
        <xi:include href="../i18n/resources-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles offline functionality through Google Gears -->
        <xi:include href="../offline/offline-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles error summary -->
        <xi:include href="../includes/error-summary-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles document persistence -->
        <xi:include href="../includes/persistence-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles print functionality -->
        <xi:include href="../includes/print-model.xml" xxi:omit-xml-base="true"/>
        <!-- This model handles import/export -->
        <xi:include href="../import-export/import-export-model.xml" xxi:omit-xml-base="true"/>

        <!-- Copy existing main model -->
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>

            <!-- Special bind to set the form instance read-only when necessary -->
            <xforms:bind nodeset="instance('fr-form-instance')" readonly="xxforms:instance('fr-parameters-instance')/mode = ('view', 'print', 'pdf')"/>

        </xsl:copy>

        <xsl:if test="not($is-noscript)">
            <!-- Handle collapsible sections -->
            <xi:include href="../includes/collapse-script.xhtml" xxi:omit-xml-base="true"/>
            <!-- Handle checking dirty status -->
            <xi:include href="../includes/check-dirty-script.xhtml" xxi:omit-xml-base="true"/>
        </xsl:if>

    </xsl:template>

</xsl:stylesheet>
