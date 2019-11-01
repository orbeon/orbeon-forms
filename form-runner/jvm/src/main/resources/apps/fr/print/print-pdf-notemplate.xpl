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
<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <!-- Unrolled XHTML+XForms -->
    <p:param type="input" name="xforms"/>
    <!-- Request parameters -->
    <p:param type="input" name="parameters"/>
    <!-- PDF document -->
    <p:param type="output" name="data"/>

    <!-- Call XForms epilogue -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="/ops/pfc/xforms-epilogue.xpl"/>
        <p:input name="data" href="#xforms"/>
        <p:input name="model-data"><null xsi:nil="true"/></p:input>
        <p:input name="instance" href="#parameters"/>
        <p:output name="xformed-data" id="xformed-data"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config><include>/request/container-namespace</include></config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!--
        Rewrite using servlet strategy. The idea is that even in a portlet environment, URLs produced by the XForms
        engine and epilogue are entirely expected to be rewritten further down the pipeline. There might be small
        exceptions, such as URLs in JSON and the like, but JavaScript is not relevant to PDF production so that
        is not expected to be an issue.  -->
    <p:processor name="oxf:xhtml-servlet-rewrite">
        <p:input name="data" href="#xformed-data"/>
        <p:output name="data" id="rewritten-data"/>
    </p:processor>

    <!-- Prepare XHTML before conversion to PDF -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config"     href="print-pdf-notemplate.xsl"/>
        <p:input name="data"       href="#rewritten-data"/>
        <p:input name="request"    href="#request"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="xforms"     href="#xforms"/>
        <p:output name="data" id="xhtml-data"/>
    </p:processor>

    <!-- Serialize HTML to PDF -->
    <!-- Convert HTML to PDF -->
    <p:processor name="oxf:xhtml-to-pdf">
        <p:input name="data" href="#xhtml-data"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
