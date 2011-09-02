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
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Page detail (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the form -->
    <p:param type="input" name="data"/>
    <!-- XHTML+XForms -->
    <p:param type="output" name="data"/>

    <!-- Unroll the form (theme, components, inclusions) -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../unroll-form.xpl"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="#data"/>
        <p:output name="data" id="unrolled-form"/>
    </p:processor>

    <p:choose href="#unrolled-form">
        <p:when test="normalize-space(/*/xhtml:head//xforms:instance[@id = 'fr-form-attachments']/*/pdf) != ''">
            <!-- A PDF template is attached to the form -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="print-pdf-template.xpl"/>
                <p:input name="xforms" href="#unrolled-form"/>
                <p:input name="parameters" href="#instance"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- No PDF template attached -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="print-pdf-notemplate.xpl"/>
                <p:input name="xforms" href="#unrolled-form"/>
                <p:input name="parameters" href="#instance"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
