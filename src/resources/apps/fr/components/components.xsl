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
        xmlns:PipelineFunctionLibrary="org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>

    <!-- Import components -->
    <xsl:import href="view.xsl"/>
    <xsl:import href="buttons.xsl"/>
    <xsl:import href="grid.xsl"/>
    <xsl:import href="repeat.xsl"/>
    <xsl:import href="inplace.xsl"/>
    <xsl:import href="section.xsl"/>

    <!-- Global variables -->
    <xsl:variable name="is-detail" select="doc('input:instance')/*/mode != ''" as="xs:boolean"/>
    <xsl:variable name="is-form-builder" select="doc('input:instance')/*/app = 'orbeon' and doc('input:instance')/*/form = 'builder'" as="xs:boolean"/>
    <xsl:variable name="is-noscript" select="not($is-form-builder) and doc('input:request')/request/parameters/parameter[name = 'fr-noscript']/value = 'true'"/>

    <!-- Properties -->
    <xsl:variable name="error-summary" select="PipelineFunctionLibrary:property('oxf.fr.detail.error-summary')" as="xs:string"/>
    <xsl:variable name="has-button-save-locally" select="PipelineFunctionLibrary:property('oxf.fr.detail.button.save-locally') = 'true'" as="xs:boolean"/>
    <xsl:variable name="is-noscript-table" select="PipelineFunctionLibrary:property('oxf.fr.detail.noscript.table') = 'false'" as="xs:boolean"/>

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
            <xsl:apply-templates select="@* | node()"/>
        </xhtml:html>
    </xsl:template>

    <!-- Add Form Runner models and scripts -->
    <xsl:template match="/xhtml:html/xhtml:head/xforms:model[1]">

        <!-- This model handles form sections -->
        <xforms:model id="fr-sections-model"
                      xxforms:external-events="fr-after-collapse {@xxforms:external-events}"
                      xxforms:readonly-appearance="{if (doc('input:instance')/*/mode = ('view', 'print', 'pdf')) then 'static' else 'dynamic'}"
                      xxforms:order="help label control alert hint"
                      xxforms:computed-binds="recalculate"
                      xxforms:offline="false"
                      xxforms:noscript="{$is-noscript}">

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
                <!-- Different behavior depending on whether we support script or not -->
                <xxforms:script if="not(property('xxforms:noscript'))">frCollapse();</xxforms:script>
                <xforms:dispatch if="property('xxforms:noscript')" target="fr-sections-model" name="fr-after-collapse"/>
            </xforms:action>

            <!-- Open section -->
            <xforms:action ev:event="fr-expand">
                <xforms:toggle case="case-{{instance('fr-current-section-instance')/id}}-open"/>
                <!-- Only if we support script -->
                <xxforms:script if="not(property('xxforms:noscript'))">frExpand();</xxforms:script>
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

        <!-- Copy other models present in the UI (used for dialogs until we actually have native local models) -->
        <xsl:copy-of select="/xhtml:html/xhtml:body//xforms:model"/>

        <xsl:if test="not($is-noscript)">
            <!-- Handle collapsible sections -->
            <xi:include href="../includes/collapse-script.xhtml" xxi:omit-xml-base="true"/>
            <!-- Handle checking dirty status -->
            <xi:include href="../includes/check-dirty-script.xhtml" xxi:omit-xml-base="true"/>
        </xsl:if>

    </xsl:template>

    <!-- Filter out models in the UI as they are copied separately -->
    <xsl:template match="xhtml:body//xforms:model"/>

</xsl:stylesheet>
