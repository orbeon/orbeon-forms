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
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- Request parameters document -->
    <p:param type="output" name="data"/>

    <!-- Extract page detail (app, form, document, and mode) from URL -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
                <include>/request/parameters/parameter[name = 'form-version']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/(service/)?([^/]+)/([^/]+)/(new|edit|view|pdf|tiff|controls|email|validate|import|schema|duplicate|attachments|publish|compile)(/([^/]+))?(/([0-9A-Za-z\-]+)(?:/[^/]+)?\.(?:pdf|tiff))?</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <!-- Put app, form, and mode in format understood by read-form.xpl -->
    <p:processor name="oxf:xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="matcher-groups" href="#matcher-groups"/>
        <p:input name="request"        href="#request"/>
        <p:input name="config">
            <request xsl:version="2.0">
                <xsl:variable name="groups"  select="doc('input:matcher-groups')/result/group"/>
                <xsl:variable name="request" select="doc('input:request')/request"/>
                <app><xsl:value-of select="$groups[2]"/></app>
                <form><xsl:value-of select="$groups[3]"/></form>
                <form-version>
                    <!-- If not provided as a request parameter, will be populated by read-form.xpl -->
                    <xsl:value-of select="$request/parameters/parameter[name = 'form-version']/value"/>
                </form-version>
                <document><xsl:value-of select="$groups[6]"/></document>
                <mode><xsl:value-of select="$groups[4]"/></mode>
                <uuid><xsl:value-of select="$groups[8]"/></uuid>
            </request>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
