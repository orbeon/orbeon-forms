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
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Parameters (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the from obtained from persistence layer -->
    <p:param type="output" name="data"/>
    <!-- Instance usable by the view -->
    <p:param type="output" name="instance"/>

    <!-- Call up persistence layer to obtain XHTML+XForms -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#instance">
            <config xsl:version="2.0">

                <!-- Create URI based on properties -->
                <xsl:variable name="resource" as="xs:string"
                              select="concat('/fr/service/persistence/crud/', /*/app, '/', /*/form, '/data/', /*/document, '/data.xml')"/>
                <url>
                    <xsl:value-of select="pipeline:rewriteServiceURI($resource, true())"/>
                </url>
                <!-- Forward the same headers that the XForms engine forwards -->
                <forward-headers><xsl:value-of select="pipeline:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
                <!-- Produce binary so we do our own XML parsing -->
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
        <p:output name="data" id="document"/>
    </p:processor>

    <!-- Handle XInclude (mainly for "resource" type of persistence) -->
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="#document"/>
        <p:output name="data" id="after-xinclude" ref="data"/>
    </p:processor>

    <!-- Store document in the request for further access down the line -->
    <p:processor name="oxf:scope-serializer">
        <p:input name="config">
            <config>
                <key>fr-form-definition</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:input name="data" href="#after-xinclude"/>
    </p:processor>

    <!-- And we recreate an instance usable by the view -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#after-xinclude"/>
        <p:input name="config">
            <request xsl:version="2.0">
                <xsl:variable name="metadata" select="/xhtml:html/xhtml:head/xforms:model[@id = 'fr-form-model']/xforms:instance[@id = 'fr-form-metadata']/*" as="element(metadata)"/>
                <app><xsl:value-of select="$metadata/application-name"/></app>
                <form><xsl:value-of select="$metadata/form-name"/></form>
                <document/>
                <mode>test</mode>
            </request>
        </p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

</p:config>
