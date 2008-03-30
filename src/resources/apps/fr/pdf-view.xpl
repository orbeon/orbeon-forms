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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the form -->
    <p:param type="input" name="data"/>

    <p:param type="output" name="data"/>

    <!-- Apply components -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="components.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="xhtml-document"/>
    </p:processor>

    <!-- Call XForms epilogue -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/ops/pfc/xforms-epilogue.xpl"/>
        <p:input name="data" href="#xhtml-document"/>
        <p:input name="model-data"><null xsi:nil="true"/></p:input>
        <p:input name="instance" href="#instance"/>
        <p:input name="xforms-model"><null xsi:nil="true"/></p:input>
        <p:output name="xformed-data" id="xformed-data"/>
    </p:processor>

    <!-- Remove XHTML prefixes because of a bug in Flying Saucer -->
    <p:processor name="oxf:qname-converter">
        <p:input name="config">
            <config>
                <match>
                    <uri>http://www.w3.org/1999/xhtml</uri>
                </match>
                <replace>
                    <prefix/>
                </replace>
            </config>
        </p:input>
        <p:input name="data" href="#xformed-data"/>
        <p:output name="data" id="xhtml-data"/>
    </p:processor>

    <!-- Serialize -->
    <p:processor name="oxf:xhtml-to-pdf">
        <p:input name="data" href="#xhtml-data"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
