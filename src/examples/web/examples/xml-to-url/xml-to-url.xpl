<!--
    Copyright (C) 2004 Orbeon, Inc.
  
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
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:gzip-output-stream="java:java.util.zip.GZIPOutputStream"
    xmlns:byte-array-output-stream="java:java.io.ByteArrayOutputStream"
    xmlns:array="java:java.lang.reflect.Array"
    xmlns:string="java:java.lang.String"
    xmlns:base64="java:org.orbeon.oxf.util.Base64"
    xmlns:env="http://www.w3.org/2003/05/soap-envelope"
    xmlns:orbeon="http://www.orbeon.com/">

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/*</include>
                <exclude>/request/body</exclude>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="soap-response.xml"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:variable name="xml-string" select="doc('input:instance')"/>
                <xsl:variable name="xml-array" select="string:get-bytes($xml-string)"/>
                <xsl:variable name="output-stream" select="byte-array-output-stream:new()"/>
                <xsl:variable name="gzip" select="gzip-output-stream:new($output-stream)"/>
                <xsl:variable name="base64-string-multi-line" select="base64:encode(byte-array-output-stream:to-byte-array($output-stream))"/>
                <xsl:variable name="base64-string" select="replace(replace($base64-string-multi-line, '&#xa;', ''), '&#xd;', '')"/>
                <xsl:variable name="parameter-length" select="string-length($base64-string)"/>
                <xsl:template match="/">
                    <xsl:value-of select="gzip-output-stream:write($gzip, $xml-array, 0, string:length($xml-string))"/>
                    <xsl:value-of select="gzip-output-stream:finish($gzip)"/>
                    <xsl:apply-templates/>
                </xsl:template>
                <!-- Output URL -->
                <xsl:template match="orbeon:original-url">
                    <xsl:copy>
                        <xsl:if test="2048 >= $parameter-length">
                            <xsl:variable name="request" as="element()" select="doc('input:request')/request"/>
                            <xsl:value-of select="concat(
                                'http://',
                                $request/server-name,
                                if ($request/server-port = '80') then '' else concat(':', $request/server-port),
                                $request/context-path,
                                '/direct/xml-to-url/doc?xml=',
                                escape-uri($base64-string, true()))"/>
                        </xsl:if>
                    </xsl:copy>
                </xsl:template>
                <!-- Output error if necessary -->
                <xsl:template match="orbeon:error">
                    <xsl:copy>
                        <xsl:if test="$parameter-length > 2048">
                            <xsl:text>Document is too large. Generated URL would have a length of </xsl:text>
                            <xsl:value-of select="$parameter-length"/>
                            <xsl:text>. The maximum supported URL length is 2048.</xsl:text>
                        </xsl:if>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="urls"/>
    </p:processor>

    <p:choose href="#urls">

        <!-- Compute tiny URL if there are no error -->
        <p:when test="/env:Envelope/env:Body/orbeon:urls/orbeon:error = ''">
            <!-- Get HTML for page -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#urls"/>
                <p:input name="config">
                    <config xsl:version="2.0">
                        <url>http://tinyurl.com/create.php?url=<xsl:value-of select="/env:Envelope/env:Body/orbeon:urls/orbeon:original-url"/></url>
                        <content-type>text/xml</content-type>
                        <validating>true</validating>
                    </config>
                </p:input>
                <p:output name="data" id="url-config"/>
            </p:processor>
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="#url-config"/>
                <p:output name="data" id="html"/>
            </p:processor>

            <!-- Add short URL -->
            <p:processor uri="oxf/processor/xslt-2.0">
                <p:input name="data" href="#urls"/>
                <p:input name="html" href="#html"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0">
                        <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                        <xsl:template match="orbeon:tiny-url">
                            <xsl:copy>
                                <xsl:value-of select="doc('input:html')//blockquote/b[starts-with(., 'http://tinyurl.com/')]"/>
                            </xsl:copy>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="response"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data" href="#urls"/>
                <p:output name="data" id="response"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- Send response -->
    <p:processor name="oxf:xml-serializer">
        <p:input name="data" href="#response"/>
        <p:input name="config">
            <config>
                <content-type>application/xml</content-type>
            </config>
        </p:input>
    </p:processor>

</p:config>
