<!--
  Copyright (C) 2010 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->

<!--
    This pipeline performs a submission using the XForms server. Do not use it directly, but can call it using the
    oxf:xforms-submission processor:

    <p:processor name="oxf:xforms-submission">
        <p:input name="submission">
            <xf:submission method="post" action="/direct/xforms-translate/post"/>
        </p:input>
        <p:input name="request">
            <translation>
                <source>This is a table.</source>
                <language-pair>en|fr</language-pair>
            </translation>
        </p:input>
        <p:output name="response" id="response"/>
    </p:processor>
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:ev="http://www.w3.org/2001/xml-events"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml">

    <p:param name="submission" type="input"/>
    <p:param name="request" type="input"/>
    <p:param name="response" type="output"/>

    <!-- Create XHTML+XForms document -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#submission"/>
        <p:input name="config">
            <xh:html xsl:version="2.0">
                <xh:head>
                    <xf:model id="fr-model" xxf:optimize-get-all="false" xxf:no-updates="true" xxf:local-submission-forward="false">
                        <!-- Instance containing the request document, and response document in case of success -->
                        <xf:instance id="fr-instance" src="input:data"/>
                        <xf:submission id="fr-default-submission" replace="instance">
                            <xsl:copy-of select="/xf:submission/(@* except (@id, @replace))"/>
                            <xsl:copy-of select="/xf:submission/namespace::*"/>
                            <xsl:copy-of select="/xf:submission/*"/>
                            <!-- Upon completion (successful or not), send the resulting document to the output -->
                            <xf:send ev:event="xforms-submit-done xforms-submit-error" submission="fr-send-all-submission"/>
                        </xf:submission>
                        <xf:submission id="fr-send-all-submission" action="echo:" method="post" replace="all"/>
                        <xf:send ev:event="xforms-ready" submission="fr-default-submission"/>
                    </xf:model>
                </xh:head>
            </xh:html>
        </p:input>
        <p:output name="data" id="xhtml-xforms"/>
    </p:processor>

    <!-- Native XForms Initialization -->
    <p:processor name="oxf:xforms-to-xhtml">
        <p:input name="annotated-document" href="#xhtml-xforms"/>
        <p:input name="data" href="#request"/>
        <p:input name="instance"><null xsi:nil="true"/></p:input>
        <p:output name="document" id="binary-document"/>
    </p:processor>

    <p:choose href="#binary-document">
        <p:when test="contains(/*/@content-type, 'xml')">
            <!-- Convert binary document to XML -->
            <p:processor name="oxf:to-xml-converter">
                <p:input name="config">
                    <config><handle-xinclude>false</handle-xinclude></config>
                </p:input>
                <p:input name="data" href="#binary-document"/>
                <p:output name="data" ref="response"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- Don't convert document -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#binary-document"/>
                <p:output name="data" ref="response"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
