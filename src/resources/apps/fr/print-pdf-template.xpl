<?xml version="1.0" encoding="utf-8"?>
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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <!-- Obtain the form and data -->
    <!-- TODO: print-html.xpl does too much, we should just get what is necessary -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="print-html.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:output name="instance" id="updated-instance"/>
        <p:output name="data" id="unrolled-form-document"/>
    </p:processor>

    <!-- Extract data only -->
    <p:processor name="oxf:identity">
        <p:input name="data" href="#unrolled-form-document#xpointer((//xforms:instance[@id = 'fr-form-instance'])[1]/*)"/>
        <p:output name="data" id="form-data"/>
    </p:processor>

    <!-- Obtain original form document -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>oxf.fr.form</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="form-document"/>
    </p:processor>

    <!-- Call up persistence layer to obtain the PDF file -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#form-document">
            <config xsl:version="2.0" xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">
                <url>
                    <xsl:value-of select="pipeline:rewriteResourceURI(//xforms:instance[@id = 'fr-form-attachments']/*/pdf, true())"/>
                </url>
                <mode>binary</mode>
            </config>
        </p:input>
        <p:output name="data" id="pdf-template"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Create mapping file -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#form-data"/>
        <p:input name="xhtml" href="#form-document"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <config xsl:version="2.0"
                    xmlns:xforms="http://www.w3.org/2002/xforms"
                    xmlns:xhtml="http://www.w3.org/1999/xhtml"
                    xmlns:fr="http://orbeon.org/oxf/xml/form-runner">


                <xsl:variable name="data" select="/*" as="element()"/>
                <xsl:variable name="xhtml" select="doc('input:xhtml')/*" as="element(xhtml:html)"/>
                <xsl:variable name="request" select="doc('input:request')/*" as="element(request)"/>

                <!-- Get current resources -->
                <xsl:variable name="request-language" select="$request/parameters/parameter[name = 'fr-language']/value" as="xs:string"/>
                <xsl:variable name="resources-instance" select="$xhtml/xhtml:head/xforms:model[@id = 'fr-form-model']/xforms:instance[@id = 'fr-form-resources']/resources" as="element(resources)"/>
                <xsl:variable name="current-resources" select="$resources-instance/resource[@xml:lang = $request-language]" as="element(resource)"/>

                <xsl:variable name="fr-resources" select="doc('oxf:/apps/fr/i18n/resources.xml')/*" as="element(resources)"/>
                <xsl:variable name="fr-current-resources" select="($fr-resources/(resource[xml:lang = $request-language], resource[1]))[1]" as="element(resource)"/>

                <!-- Template -->
                <template href="input:template" show-grid="false"/>

                <!-- Barcode -->
                <xsl:variable name="barcode-value" select="$request/parameters/parameter[name = 'document']/value" as="xs:string?"/>
                <xsl:if test="normalize-space($barcode-value) != ''">
                    <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="12">
                        <barcode left="50" top="780" height="15" value="'{$barcode-value}'"/>
                    </group>
                </xsl:if>

                <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="14">
                    <xsl:for-each select="$xhtml/xhtml:body//fr:body//fr:section">
                        <xsl:variable name="section-name" select="@context" as="xs:string"/>
                        <group ref="{$section-name}" >
                            <xsl:for-each select=".//xforms:*[(@ref or @bind) and ends-with(@id, '-control')]">
                                <xsl:variable name="control" select="." as="element()"/>
                                <xsl:variable name="control-name" select="substring-before($control/@id, '-control')" as="xs:string"/>
                                <!-- Obtain the value directly from the data (makes it easier to deal with itemsets) -->
                                <!--<xsl:message>-->
                                    <!--aaa <xsl:value-of select="$section-name"/>-->
                                    <!--aaa <xsl:value-of select="$control-name"/>-->
                                    <!--aaa <xsl:value-of select="$data/*[local-name() = $section-name]/*[local-name() = $control-name]"/>-->
                                <!--</xsl:message>-->
                                <xsl:variable name="control-value" select="$data/*[local-name() = $section-name]/*[local-name() = $control-name]" as="xs:string?"/>

                                <xsl:variable name="bind" select="$xhtml/xhtml:head/xforms:model//xforms:bind[@id = $control/@bind]" as="element(xforms:bind)?"/>
                                <xsl:variable name="path" select="if ($bind)
                                    then string-join(($bind/ancestor-or-self::xforms:bind/@nodeset)[position() gt 1], '/')
                                    else string-join(($control/ancestor-or-self::*/(@ref | @context)), '/')" as="xs:string"/>

                                <xsl:choose>
                                    <xsl:when test="local-name($control) = ('select', 'select1')">
                                        <!-- Selection control -->
                                        <xsl:variable name="control-resources" select="$current-resources/*[local-name() = $control-name]" as="element()"/>

                                        <xsl:choose>
                                            <xsl:when test="local-name($control) = 'select' and $control/@appearance = 'full'">
                                                <!-- Checkboxes -->
                                                <field acro-field-name="'{$control-name}.{$control-value}'" value="'X'"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <!-- Other selection controls -->
                                                <field acro-field-name="'{$control-name}'" value="'{$control-resources/item[value = $control-value]/label}'"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:when>
                                    <xsl:when test="$bind/@type and substring-after($bind/@type, ':') = 'date'">
                                        <!-- Date -->
                                        <field acro-field-name="'{$control-name}'" value="'{if ($control-value castable as xs:date) then format-date(xs:date($control-value), $fr-current-resources/summary/formats/date, $request-language, (), ()) else $control-value}'"/>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <!-- Other control -->
                                        <field acro-field-name="'{$control-name}'" value="'{$control-value}'"/>
                                    </xsl:otherwise>
                                </xsl:choose>

                            </xsl:for-each>
                        </group>
                    </xsl:for-each>
                </group>
            </config>
        </p:input>
        <p:output name="data" id="mapping"/>
    </p:processor>

    <!-- Produce PDF document -->
    <p:processor name="oxf:pdf-template">
        <p:input name="data" href="#form-data"/>
        <p:input name="model" href="#mapping"/>
        <p:input name="template" href="#pdf-template"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
