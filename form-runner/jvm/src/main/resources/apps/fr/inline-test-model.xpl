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
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events">

    <!-- Form in XHTML+XForms format -->
    <p:param type="input" name="instance"/>
    <!-- Form in XHTML+XForms format -->
    <p:param type="output" name="data"/>
    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="output" name="instance"/>

    <!-- NOTE: It's disappointing that we have to use oxf:request/oxf:regexp rather than using the page flow
         directly, but because we want to support the PUT and POST methods, this is currently the only solution. -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
                <include>/request/method</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/(?:service/)?([^/]+)/([^/]+)/(test(-pdf)?)/?</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <!-- Incoming submission goes to model's data output -->
    <p:processor name="oxf:identity">
        <p:input name="data" href="#instance"/>
        <p:output name="data" ref="data"/>
    </p:processor>

    <!-- And we recreate an instance usable by the view -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#matcher-groups"/>
        <p:input name="config">
            <request xsl:version="2.0">
                <app><xsl:value-of select="/*/group[1]"/></app>
                <form><xsl:value-of select="/*/group[2]"/></form>
                <document/>
                <mode><xsl:value-of select="/*/group[3]"/></mode>
            </request>
        </p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

</p:config>
