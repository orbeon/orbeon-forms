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
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <!-- Empty, or current form data -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the from obtained from persistence layer -->
    <p:param type="output" name="data"/>
    <!-- Request parameters (app, form, document, and mode) from URL -->
    <p:param type="output" name="instance"/>

    <!-- Extract request parameters (app, form, document, and mode) from URL -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../request-parameters.xpl"/>
        <p:output name="data" id="parameters" ref="instance"/>
    </p:processor>

    <!-- If data is posted, store as request attribute -->
    <p:choose href="#instance">
        <p:when test="not(/null/@xsi:nil='true')">
            <p:processor name="oxf:scope-serializer">
                <p:input name="config">
                    <config>
                        <key>fr-form-data</key>
                        <scope>request</scope>
                    </config>
                </p:input>
                <p:input name="data" href="#instance"/>
            </p:processor>
        </p:when>
    </p:choose>

    <!-- Obtain the form definition -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="data" id="xhtml-fr-xforms" ref="data"/>
    </p:processor>

</p:config>
