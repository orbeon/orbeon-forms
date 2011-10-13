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
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"
        xmlns:form-runner="java:org.orbeon.oxf.fr.FormRunner">

    <!-- Parameters (app, form, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the from obtained from persistence layer -->
    <p:param type="output" name="data"/>

    <!-- Call up persistence layer to obtain XHTML+XForms -->
    <!-- NOTE: We used to use oxf:url-generator, then switched to oxf:xforms-submission for more header support. We use
         oxf:url-generator again as it is much faster and is enough now that the persistence proxy is in place. -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#instance">
            <config xsl:version="2.0">

                <!-- /*/document is available e.g. when editing or viewing a document -->
                <xsl:variable name="document" select="/*/document" as="xs:string"/>

                <!-- Create URI to persistence layer -->
                <xsl:variable name="resource" select="concat('/fr/service/persistence/crud/', /*/app, '/', /*/form, '/form/form.xhtml', if ($document != '') then concat('?document=', $document) else '')"/>
                <url>
                    <xsl:value-of select="xpl:rewriteServiceURI($resource, true())"/>
                </url>
                <!-- Forward the same headers that the XForms engine forwards -->
                <forward-headers><xsl:value-of select="xpl:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
                <!-- Produce binary so we do our own XML parsing -->
                <mode>binary</mode>
                <!-- Enable conditional GET -->
                <handle-xinclude>false</handle-xinclude>
                <cache-control><conditional-get>true</conditional-get></cache-control>
            </config>
        </p:input>
        <p:output name="data" id="binary-document"/>
    </p:processor>

    <p:choose href="#binary-document">
        <!-- HACK: we test on the request path to make this work for the toolbox, but really we should handle this in a different way -->
        <p:when test="if (not(matches(p:get-request-path(), '/fr/service/custom/orbeon/(new)?builder/toolbox'))
                          and (for $c in /*/@status-code return $c castable as xs:integer and (not((xs:integer($c) ge 200 and xs:integer($c) lt 300) or xs:integer($c) = 304))))
                      then form-runner:sendError(/*/@status-code)
                      else false()">
            <!-- Null document to keep XPL happy. The error has already been sent in the XPath expression above. -->
            <p:processor name="oxf:identity">
                <p:input name="data"><null xsi:nil="true"/></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
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
