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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns:gzip-input-stream="java:java.util.zip.GZIPInputStream"
    xmlns:byte-array-output-stream="java:java.io.ByteArrayOutputStream"
    xmlns:byte-array-input-stream="java:java.io.ByteArrayInputStream"
    xmlns:byte="java:java.lang.Byte"
    xmlns:array="java:java.lang.reflect.Array"
    xmlns:array-list="java:java.util.ArrayList"
    xmlns:string="java:java.lang.String"
    xmlns:base64="java:org.orbeon.oxf.util.Base64">

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter[name = 'xml']/value</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <xsl:variable name="gzip-array" select="base64:decode(/request/parameters/parameter/value)"/>
                    <xsl:variable name="input-stream" select="byte-array-input-stream:new($gzip-array)"/>
                    <xsl:variable name="gzip-input-input-stream" select="gzip-input-stream:new($input-stream)"/>
                    <xsl:variable name="buffer-output-stream" select="byte-array-output-stream:new(8192)"/>
                    <xsl:value-of select="for $i in 1 to 8192 return byte-array-output-stream:write($buffer-output-stream, $i)"/>
                    <xsl:variable name="buffer" select="byte-array-output-stream:to-byte-array($buffer-output-stream)"/>
                    <xsl:variable name="buffer-size" select="gzip-input-stream:read($gzip-input-input-stream, $buffer, 0, 8192)"/>
                    <xsl:variable name="xml-base64" select="string:new($buffer, 0, $buffer-size)"/>
                    <xsl:copy-of select="saxon:parse($xml-base64)"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="xml"/>
    </p:processor>

    <p:processor name="oxf:xml-serializer">
        <p:input name="config"><config/></p:input>
        <p:input name="data" href="#xml"/>
    </p:processor>

</p:config>
