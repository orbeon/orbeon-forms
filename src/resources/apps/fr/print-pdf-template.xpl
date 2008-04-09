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
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="print-html.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:output name="instance" id="updated-instance"/>
        <p:output name="data" id="xhtml-document"/>
    </p:processor>

    <!-- Extract data -->
    <p:processor name="oxf:identity">
        <p:input name="data" href="#xhtml-document#xpointer((//xforms:instance[@id = 'fr-form-instance'])[1]/*)"/>
        <p:output name="data" id="form-data"/>
    </p:processor>

    <!-- Create mapping file -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#form-data"/>
        <p:input name="xhtml" href="#xhtml-document" debug="ddddd"/>
        <p:input name="config">
            <config xsl:version="2.0"
                    xmlns:xforms="http://www.w3.org/2002/xforms"
                    xmlns:xhtml="http://www.w3.org/1999/xhtml"
                    xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
                
                <xsl:variable name="xhtml" select="doc('input:xhtml')/*" as="element(xhtml:html)"/>

                <!-- TODO: load template -->
                <!--<template href="input:template" show-grid="false"/>-->
                <template href="file:/Users/ebruchez/Desktop/bookcast.pdf" show-grid="false"/>

                <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="14">
                    <xsl:for-each select="$xhtml/xhtml:body//fr:body//fr:section">
                        <xsl:variable name="section-name" select="@context" as="xs:string"/>
                        <group ref="{$section-name}" >
                            <xsl:for-each select=".//xforms:*[@ref or @bind]">
                                <xsl:variable name="control" select="." as="element()"/>
                                <xsl:variable name="control-name" select="substring-before($control, '-control')" as="xs:string"/>

                                <xsl:variable name="bind" select="$xhtml/xhtml:head/xforms:model//xforms:bind[@id = $control/@bind]" as="element(xforms:bind)?"/>
                                <xsl:variable name="path" select="if ($bind)
                                    then string-join(($bind/ancestor-or-self::xforms:bind/@nodeset)[position() gt 1], '/')
                                    else string-join(($control/ancestor-or-self::*/(@ref | @context)), '/')" as="xs:string"/>

                                <field acro-field-name="'{$control-name}'" value="{$control-name}"/>

                            </xsl:for-each>
                        </group>
                    </xsl:for-each>
                </group>
            </config>
        </p:input>
        <p:output name="data" id="mapping" debug="zzzzz"/>
    </p:processor>

    <!-- Load PDF template -->
    <!-- TODO -->

    <!-- Produce PDF document -->
    <p:processor name="oxf:pdf-template">
        <p:input name="data" href="#form-data" debug="xxxxx"/>
        <p:input name="model" href="#mapping"/>
        <!--<p:input name="template" href="xxx"/>-->
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
