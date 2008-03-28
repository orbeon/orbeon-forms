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
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- fr-form-instance -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+XForms -->
    <p:param type="output" name="data"/>
    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="output" name="instance"/>

    <!-- Extract page detail (app, form, document, and mode) from URL -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>
    <p:processor name="oxf:perl5-matcher">
        <p:input name="config"><config>/fr/([^/]+)/([^/]+)/(print)(/)?</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <!-- Put app, form, and mode in format understood by read-form.xpl -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#matcher-groups"/>
        <p:input name="config">
            <request xsl:version="2.0">
                <app><xsl:value-of select="/result/group[1]"/></app>
                <form><xsl:value-of select="/result/group[2]"/></form>
                <document/>
                <mode><xsl:value-of select="/result/group[3]"/></mode>
            </request>
        </p:input>
        <p:output name="data" id="page-detail"/>
    </p:processor>
    
    <!-- Generate the page -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="detail/read-form.xpl"/>
        <p:input name="instance" href="#page-detail"/>
        <p:output name="data" id="xhtml-fr-xforms"/>
    </p:processor>

    <!-- Generate the page -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="components.xpl"/>
        <p:input name="instance" href="#page-detail"/>
        <p:input name="data" href="#xhtml-fr-xforms"/>
        <p:output name="data" id="xhtml-xforms"/>
    </p:processor>

    <!-- Insert the instance posted to this URL in the XForms -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#xhtml-xforms"/>
        <p:input name="form-data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy-modes.xsl"/>
                <xsl:template match="xforms:instance[@id = 'fr-form-instance']">
                    <xsl:copy>
                        <xsl:copy-of select="@*"/>
                        <xsl:copy-of select="doc('input:form-data')/*"/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data" debug="final-xforms"/>
    </p:processor>

    <!-- Return page detail as instance -->
    <p:processor name="oxf:identity">
        <p:input name="data" href="#page-detail"/>
        <p:output name="data" ref="instance"/>
    </p:processor>
</p:config>
