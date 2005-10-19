<!--
    Copyright (C) 2005 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <p:param type="input" name="data"/>
    <p:param type="input" name="instance"/>
    <p:param type="input" name="xforms-model"/>
    <p:param type="output" name="xformed-data"/>

    <!-- Get request information -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/container-type</include>
                <include>/request/request-path</include>
                <include>/request/context-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request-info"/>
    </p:processor>

    <!-- Annotate XForms elements and generate XHTML if necessary -->
    <p:choose href="#xforms-model">
        <!-- Test for legacy XForms engine -->
        <p:when test="/xforms:model">
            <p:processor name="oxf:xforms-output">
                <p:input name="model" href="#xforms-model"/>
                <p:input name="instance" href="#instance"/>
                <p:input name="data" href="#data"/>
                <p:output name="data" id="annotated-data"/>
            </p:processor>
            <!-- Transform annotated XForms to XHTML -->
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="config" href="/config/xforms-to-xhtml.xsl"/>
                <p:input name="model" href="#xforms-model"/>
                <p:input name="instance" href="#instance"/>
                <p:input name="data" href="#annotated-data"/>
                <p:output name="data" id="xhtml-data"/>
            </p:processor>
            <p:choose href="#request-info">
                <p:when test="/request/container-type = 'servlet'">
                    <!-- Handle portlet forms (you can skip this step if you are not including portlets in your page) -->
                    <p:processor name="oxf:xslt">
                        <p:input name="config" href="xforms-portlet-forms.xsl"/>
                        <p:input name="data" href="#xhtml-data"/>
                        <p:output name="data" ref="xformed-data"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Don't go through this step if we are implementing a portlet -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#xhtml-data"/>
                        <p:output name="data" ref="xformed-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:when>
        <p:otherwise>
            <!-- TODO: put here processor detecting XForms model -->
            <p:choose href="#data">
                <!-- Test for new XForms engine -->
                <p:when test="//xforms:model"><!-- TODO: test on result of processor above -->
                    <!-- Handle widgets -->
                    <p:processor name="oxf:xslt">
                        <p:input name="data" href="#data"/>
                        <p:input name="config" href="/config/xforms-widgets.xsl"/>
                        <p:output name="data" id="widgeted-view"/>
                    </p:processor>
                    <!-- Annotate elements in view with ids and alerts -->
                    <p:processor name="oxf:xforms-document-annotator">
                        <p:input name="data" href="#widgeted-view"/>
                        <p:output name="data" id="annotated-view"/>
                    </p:processor>
                    <!--<p:processor name="oxf:xslt">-->
                        <!--<p:input name="data" href="#widgeted-view"/>-->
                        <!--<p:input name="config" href="xforms-annotate-controls.xsl"/>-->
                        <!--<p:output name="data" id="annotated-view"/>-->
                    <!--</p:processor>-->
                    <!-- Extract models and controls -->
                    <p:processor name="oxf:xforms-extractor">
                        <!--<p:input name="data" href="#annotated-view" debug="xxxannotated-view"/>-->
                        <!--<p:output name="data" id="xforms-models-controls" debug="xxxcontrols"/>-->
                        <p:input name="data" href="#annotated-view"/>
                        <p:output name="data" id="xforms-models-controls"/>
                    </p:processor>
                    <!--<p:processor name="oxf:sax-debugger">-->
                        <!--<p:input name="data" href="#xforms-models-controls1"/>-->
                        <!--<p:output name="data" id="xforms-models-controls" debug="xxxcontrols"/>-->
                    <!--</p:processor>-->
                    <!--<p:processor name="oxf:unsafe-xslt">-->
                        <!--<p:input name="data" href="#annotated-view"/>-->
                        <!--<p:input name="config" href="xforms-extract-controls.xsl"/>-->
                        <!--<p:input name="request" href="#request-info"/>-->
                        <!--<p:output name="data" id="xforms-models-controls"/>-->
                    <!--</p:processor>-->
                    <!-- Build request to XForms server -->
                    <p:processor name="oxf:xforms-request-encoder">
                        <p:input name="data" href="#xforms-models-controls"/>
                        <p:output name="data" id="xforms-request"/>
                    </p:processor>
                    <!-- Get initial instances -->
                    <p:processor name="oxf:xforms-server">
                        <p:input name="request" href="#xforms-request"/>
                        <!--<p:output name="response" id="response" debug="xxxinitial-response"/>-->
                        <p:output name="response" id="response"/>
                    </p:processor>
                    <p:processor name="oxf:xslt">
                        <p:input name="config" href="xforms-to-ajax-xhtml.xsl"/>
                        <p:input name="data" href="#annotated-view"/>
                        <p:input name="request" href="#xforms-request"/>
                        <p:input name="response" href="#response"/>
                        <p:output name="data" ref="xformed-data"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#data"/>
                        <p:output name="data" ref="xformed-data"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:otherwise>
    </p:choose>

</p:config>
