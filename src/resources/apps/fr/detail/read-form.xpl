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
<p:config xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- Parameters (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the from obtained from persistence layer -->
    <p:param type="output" name="data"/>

    <!-- Call up persistence layer to obtain XHTML+XForms -->
    <p:processor name="oxf:xforms-to-xhtml">
        <p:input name="annotated-document" transform="oxf:xinclude" href="aggregate('dummy')">
            <xhtml:html>
                <xhtml:head>
                    <xhtml:title/>
                    <xforms:model xxforms:optimize-get-all="false">
                        <xxforms:variable name="app" select="instance('fr-parameters-instance')/app"/>
                        <xxforms:variable name="form" select="instance('fr-parameters-instance')/form"/>
                        <xforms:instance id="fr-parameters-instance" src="input:instance"/>
                        <xforms:submission id="get-source-form-submission" method="get" serialization="none"
                                resource="/fr/service/persistence/crud/{$app}/{$form}/form/form.xhtml{
                                    if (instance('fr-parameters-instance')/document != '') then concat('?document=', instance('fr-parameters-instance')/document) else ''}"
                                replace="all" xxforms:xinclude="true">
                        </xforms:submission>
                        <xforms:send ev:event="xforms-model-construct-done" submission="get-source-form-submission"/>
                    </xforms:model>
                </xhtml:head>
                <xhtml:body/>
            </xhtml:html>
        </p:input>
        <p:input name="data"><null xsi:nil="true"/></p:input>
        <p:input name="instance" href="#instance"/>
        <p:output name="document" id="binary-document"/>
    </p:processor>

    <p:choose href="#binary-document">
        <p:when test="contains(/*/@content-type, 'xml')">
            <!-- Convert binary document to XML -->
            <p:processor name="oxf:to-xml-converter">
                <p:input name="config">
                    <!-- Don't handle XInclude as this is done down the line -->
                    <config><handle-xinclude>false</handle-xinclude></config>
                </p:input>
                <p:input name="data" href="#binary-document"/>
                <p:output name="data" id="document"/>
            </p:processor>

            <!-- Handle XInclude (mainly for "resource" type of persistence) -->
            <p:processor name="oxf:xinclude">
                <p:input name="config" href="#document"/>
                <p:output name="data" id="after-xinclude" ref="data"/>
            </p:processor>

            <!-- Store document in the request for further access down the line -->
            <p:processor name="oxf:scope-serializer">
                <p:input name="config">
                    <config>
                        <key>fr-form-definition</key>
                        <scope>request</scope>
                    </config>
                </p:input>
                <p:input name="data" href="#after-xinclude"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Null document -->
            <p:processor name="oxf:identity">
                <p:input name="data"><null xsi:nil="true"/></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
