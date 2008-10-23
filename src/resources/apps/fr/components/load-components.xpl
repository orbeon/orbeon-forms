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
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xbl="http://www.w3.org/ns/xbl"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- XBL document -->
    <p:param type="output" name="data"/>

    <!-- TODO: for now use orbeon/library, but must be configurable -->
    <p:processor name="oxf:identity">
        <p:input name="data">
            <request>
                <app>orbeon</app>
                <form>library</form>
                <document/>
                <mode/>
            </request>
        </p:input>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <!-- Read template form -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../detail/read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="data" id="template-form"/>
    </p:processor>

    <!-- Convert template to XBL -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#template-form"/>
        <p:input name="config" href="form-to-xbl.xsl"/>
        <p:output name="data" id="template-xbl"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config"  transform="oxf:unsafe-xslt" href="#parameters">
            <config xsl:version="2.0">
                <url>
                    <!-- Create URI based on properties -->
                    <xsl:value-of select="pipeline:property(string-join(('oxf.fb.components.uri', /*/app, /*/form), '.'))"/>
                </url>
                <!-- Forward the same headers that the XForms engine forwards -->
                <forward-headers><xsl:value-of select="pipeline:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
            </config>
        </p:input>
        <p:output name="data" id="standard-xbl"/>
    </p:processor>

    <!-- Aggregate results -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#template-xbl"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="config">
            <xbl:xbl xsl:version="2.0">
                <xsl:copy-of select="/*/xbl:binding"/>
                <xsl:copy-of select="doc(pipeline:property(string-join(('oxf.fb.components.uri', doc('input:parameters')/*/app, doc('input:parameters')/*/form), '.')))/*/xbl:binding"/>
            </xbl:xbl>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
