<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2011 Orbeon, Inc.

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
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the form -->
    <p:param type="input" name="data"/>
    <!-- XHTML+XForms -->
    <p:param type="output" name="data"/>

    <!-- Apply project-specific theme -->
    <p:choose href="#instance">
        <p:when test="doc-available(concat('oxf:/forms/', /*/app, '/theme.xsl'))">
            <!-- TODO: Also fetch theme from persistence layer -->
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="aggregate('config', aggregate('url', #instance#xpointer(concat(
                                                'oxf:/forms/', /*/app, '/theme.xsl'))))"/>
                <p:output name="data" id="theme"/>
            </p:processor>
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#data"/>
                <p:input name="config" href="#theme"/>
                <p:output name="data" id="themed-data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data" href="#data"/>
                <p:output name="data" id="themed-data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- NOTE: First pass of XInclude is handled when reading the form from the persistence layer -->

    <!-- Get request information -->
    <!-- Noscript parameter -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter[starts-with(name, 'fr-noscript')]</include>
                <include>/request/request-path</include>
                <include>/request/request-uri</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!--
        DO NOT REMOVE THIS UNLESS YOU REALLY KNOW WHAT YOU ARE DOING! This is in place to make sure we read the
        #request output above. components.xsl below may not read it at times, which causes oxf:request to never cache
        its output, leading to oxf:xforms-to-xhtml's input to not be cacheable. Tricky.
    -->
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#request"/>
    </p:processor>

    <!-- Apply UI components -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#themed-data"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="config" href="components/components.xsl"/>
        <p:input name="request" href="#request"/>

        <p:output name="data" id="after-components"/>

        <!-- This is here just so that we can reload the form when the properties or the resources change -->
        <p:input name="properties-xforms" href="oxf:/config/properties-xforms.xml"/>
        <p:input name="properties-form-runner" href="oxf:/config/properties-form-runner.xml"/>
        <p:input name="properties-local" href="oxf:/config/properties-local.xml"/>
    </p:processor>

    <!-- Handle XInclude -->
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="#after-components"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
