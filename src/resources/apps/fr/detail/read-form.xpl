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
<p:config xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <!-- Parameters (app, form, form version, document, and mode) -->
    <p:param type="input" name="instance"/>
    <!-- XHTML+FR+XForms for the from obtained from persistence layer -->
    <p:param type="output" name="data"/>
    <!-- Parameters, to which we added the form version -->
    <p:param type="output" name="instance"/>

    <p:processor name="oxf:xinclude">
        <p:input name="config">
            <config xsl:version="2.0">

                <xsl:variable name="params" select="/*"/>

                <!-- Uses $params and exposes $use-document-id and $specific-form-version-requested -->
                <xi:include href="versioning-headers.xml" xpointer="xpath(/*/*)"/>

                <!-- Create URI to persistence layer -->
                <xsl:variable
                    name="resource"
                    select="
                        concat(
                            '/fr/service/persistence/crud/',
                            /*/app,
                            '/',
                            /*/form,
                            '/form/form.xhtml',
                            (: document is available e.g. when editing or viewing a document :)
                            if ($use-document-id) then concat('?document=', $params/document) else ''
                        )"/>
                <url>
                    <xsl:value-of select="xpl:rewriteServiceURI($resource, true())"/>
                </url>
                <!-- Forward the same headers that the XForms engine forwards -->
                <forward-headers><xsl:value-of select="xpl:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
                <!-- Form definitions are always in XML format -->
                <mode>xml</mode>
                <!-- Must disable XInclude here for conditional GET to work! -->
                <handle-xinclude>false</handle-xinclude>
                <!-- Enable conditional GET -->
                <cache-control><use-local-cache>true</use-local-cache><conditional-get>true</conditional-get></cache-control>

                <!-- Read the Orbeon-Form-Definition-Version, if provided -->
                <read-header>orbeon-form-definition-version</read-header>

            </config>
        </p:input>
        <p:output name="data" id="xslt-config"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input  name="config" href="#xslt-config"/>
        <p:input  name="data"   href="#instance"/>
        <p:output name="data"   id="url-generator-config"/>
    </p:processor>

    <!-- Call up persistence layer to obtain XHTML+XForms -->
    <!-- NOTE: We used to use oxf:url-generator, then switched to oxf:xforms-submission for more header support. We use
         oxf:url-generator again as it is much faster and is enough now that the persistence proxy is in place. -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-generator-config"/>
        <p:output name="data" id="document"/>
    </p:processor>

    <!-- Her we read the document to be sure the URL generator runs before the XSLT stylesheet below,
         which read a request attribute set by the URL generator -->
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#document"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="form-version">
                    <xsl:copy>
                        <xsl:variable name="version-from-persistence" select="p:get-request-attribute('oxf.url-generator.header.orbeon-form-definition-version', 'text/plain')"/>
                        <!-- Use 1 if no version was returned by the persistence layer -->
                        <xsl:value-of select="($version-from-persistence, 1)[1]"/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="instance"/>
    </p:processor>

    <!-- Handle XInclude (mainly for "resource" type of persistence) -->
    <p:processor name="oxf:xinclude">
        <p:input name="config" href="#document"/>
        <p:output name="data" id="after-xinclude" ref="data"/>
    </p:processor>

    <!-- Store document in the request for further access down the line. This is used by the Summary page. -->
    <p:processor name="oxf:scope-serializer">
        <p:input name="config">
            <config>
                <key>fr-form-definition</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:input name="data" href="#after-xinclude"/>
    </p:processor>

</p:config>
