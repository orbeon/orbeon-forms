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
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the from obtained from persistence layer -->
    <p:param type="output" name="data"/>

    <!-- Call up persistence layer to obtain XHTML+XForms -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#instance" debug="instance app">
            <config xsl:version="2.0" xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

                <xsl:variable name="prefix" select="'oxf.fr.persistence.app'" as="xs:string"/>
                <xsl:variable name="app" select="/*/app" as="xs:string"/>
                <xsl:variable name="form" select="/*/form" as="xs:string"/>
                <xsl:variable name="suffix" select="'uri'" as="xs:string"/>

                <!-- List of properties from specific to generic -->
                <xsl:variable name="names"
                                  select="(string-join(($prefix, $app, $form, 'form', $suffix), '.'),
                                           string-join(($prefix, $app, $form, $suffix), '.'),
                                           string-join(($prefix, $app, $suffix), '.'),
                                           string-join(($prefix, '*', $suffix), '.'))" as="xs:string+"/>

                <!-- Find all values -->
                <xsl:variable name="values" select="for $name in $names return pipeline:property($name)" as="xs:string*"/>

                <!-- Create URI with first non-empty value -->
                <xsl:variable name="resource" select="concat($values[normalize-space() != ''][1], '/crud/', /*/app, '/', /*/form, '/form/form.xhtml')" as="xs:string"/>
                <url>
                    <xsl:value-of select="pipeline:rewriteResourceURI($resource, true())"/>
                </url>
                <mode>binary</mode>
            </config>
        </p:input>
        <p:output name="data" id="binary-document"/>
    </p:processor>

    <!-- Convert binary document to XML -->
    <p:processor name="oxf:to-xml-converter">
        <p:input name="config">
            <!-- Don't handle XInclude as this is done down the line -->
            <config><handle-xinclude>false</handle-xinclude></config>
        </p:input>
        <p:input name="data" href="#binary-document"/>
        <p:output name="data" id="document" ref="data"/>
    </p:processor>

    <!-- Store document in the request for further access down the line -->
    <p:processor name="oxf:scope-serializer">
        <p:input name="config">
            <config>
                <key>oxf.fr.form</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:input name="data" href="#document"/>
    </p:processor>

</p:config>
