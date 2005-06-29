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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="default-submission" type="input"/>
    <p:param name="matcher-result" type="input"/>
    <p:param name="setvalues" type="input" debug="xxxsetvalues"/>
    <p:param name="instance" type="output" debug="xxxsubmitted-instance"/>

    <!-- Check content type -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/container-type</include>
                <include>/request/content-type</include>
                <include>/request/method</include>
                <include>/request/parameters</include>
            </config>
        </p:input>
        <p:output name="data" id="request-info"/>
    </p:processor>

    <p:choose href="#request-info">
        <!-- Check for XML post -->
        <p:when test="lower-case(/*/method) = 'post' and (/*/content-type = ('application/xml', 'text/xml') or ends-with(/*/content-type, '+xml'))">

            <!-- Extract request body -->
            <p:processor name="oxf:request">
                <p:input name="config">
                    <config stream-type="xs:anyURI">
                        <include>/request/body</include>
                    </config>
                </p:input>
                <p:output name="data" id="request-body"/>
            </p:processor>

            <!-- Dereference URI and return XML instance -->
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="aggregate('config', aggregate('url', #request-body#xpointer(string(/request/body))))"/>
                <p:output name="data" id="raw-instance" debug="xxxraw-instance"/>
            </p:processor>

            <!-- Update instance if needed -->
            <p:choose href="#setvalues">
                <p:when test="/null/@xsi:nill = 'true'">
                    <!-- No updates to perform on the instance -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#raw-instance"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Updates to do on the instance -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#raw-instance"/>
                        <p:input name="setvalues" href="#setvalues"/>
                        <p:input name="matcher-result" href="#matcher-result"/>
                        <p:input name="request-info" href="#request-info"/>
                        <p:input name="config" href="request-params.xsl"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>

        </p:when>
        <p:when test="(lower-case(/*/method) = 'get' or /*/container-type = 'portlet') and /*/parameters/parameter[name = '$instance']">
            <!-- Check for XML GET with $instance parameter -->

            <!-- Decode parameter -->
            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data" href="#request-info"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0" xmlns:context="java:org.orbeon.oxf.pipeline.StaticExternalContext">
                        <xsl:template match="/">
                            <xsl:copy-of select="context:decodeXML(normalize-space(/*/parameters/parameter[name = '$instance']/value))"/>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="raw-instance" debug="xxxraw-instance"/>
            </p:processor>

            <!-- Update instance if needed -->
            <p:choose href="#setvalues">
                <p:when test="/null/@xsi:nill = 'true'">
                    <!-- No updates to perform on the instance -->
                    <p:processor name="oxf:identity">
                        <p:input name="data" href="#raw-instance"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Updates to do on the instance -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#raw-instance"/>
                        <p:input name="setvalues" href="#setvalues"/>
                        <p:input name="matcher-result" href="#matcher-result"/>
                        <p:input name="request-info" href="#request-info"/>
                        <p:input name="config" href="request-params.xsl"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>

        </p:when>
        <p:otherwise>
            
            <p:choose href="#default-submission">
                <p:when test="/null/@xsi:nill = 'true'">
                    <!-- No submission and no default submission, return null document -->
                    <p:processor name="oxf:identity">
                        <p:input name="data">
                            <null xsi:nil="true"/>
                        </p:input>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- No submission but there is a default submission which may have to be updated -->
                    <p:processor name="oxf:unsafe-xslt">
                        <p:input name="data" href="#default-submission"/>
                        <p:input name="setvalues" href="#setvalues"/>
                        <p:input name="matcher-result" href="#matcher-result"/>
                        <p:input name="request-info" href="#request-info"/>
                        <p:input name="config" href="request-params.xsl"/>
                        <p:output name="data" ref="instance"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:otherwise>
    </p:choose>

</p:config>
