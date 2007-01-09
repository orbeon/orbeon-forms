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
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <url><xsl:value-of select="/*/url"/></url>
                <content-type>text/html</content-type>
                <header>
                    <name>User-Agent</name>
                    <value>Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.4.1) Gecko/20031008</value>
                </header>
            </config>
        </p:input>
        <p:output name="data" id="url-generator"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-generator"/>
        <p:output name="data" id="page"/>
    </p:processor>
    
    <p:choose href="#instance">
        <p:when test="/*/xquery != ''">
            <!-- Inline XQuery -->
            <p:processor name="oxf:identity">
                <p:input name="data" href="#instance#xpointer(/*/xquery)"/>
                <p:output name="data" id="xquery-text"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <!-- XQuery loaded from URL -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#instance#xpointer(/*/xquery-url)"/>
                <p:input name="config">
                    <config xsl:version="2.0">
                        <url><xsl:value-of select="string(/)"/></url>
                        <content-type>text/plain</content-type>
                    </config>
                </p:input>
                <p:output name="data" id="url-generator-config"/>
            </p:processor>
            <p:processor name="oxf:url-generator">
                <p:input name="config" href="#url-generator-config"/>
                <p:output name="data" id="xquery-text"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <!-- "Parse" XQuery -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#xquery-text"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:saxon="http://saxon.sf.net/">
                <xsl:template match="/">
                    <xsl:copy-of select="saxon:parse(concat('&lt;xquery>', string(/), '&lt;/xquery>'))"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="xquery"/>
    </p:processor>
    
    <p:processor name="oxf:xquery">
        <p:input name="config" href="#xquery"/>
        <p:input name="data" href="#page"/>
        <p:output name="data" id="xquery-output"/>
    </p:processor>
    
    <p:choose href="#instance">
        <p:when test="/*/output = 'html'">
            <p:processor name="oxf:html-serializer">
                <p:input name="config"><config/></p:input>
                <p:input name="data" href="#xquery-output"/>
            </p:processor>
        </p:when>
        <p:when test="/*/output = 'javascript'">
            <p:processor name="oxf:html-converter">
                <p:input name="config"><config/></p:input>
                <p:input name="data" href="#xquery-output"/>
                <p:output name="data" id="html"/>
            </p:processor>
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#html"/>
                <p:input name="config">
                    <text xsl:version="2.0">
                        <xsl:variable name="text" as="xs:string" select="replace(/*, '&quot;', '\\&quot;')"/>
                        <xsl:text>document.write(</xsl:text>
                        <xsl:for-each select="tokenize($text, '&#x0a;')">
                            <xsl:if test="position() > 1"> +&#x0a;</xsl:if>
                            <xsl:text>"</xsl:text>
                            <xsl:value-of select="."/>
                            <xsl:text>"</xsl:text>
                        </xsl:for-each>
                        <xsl:text>);</xsl:text>
                    </text>
                </p:input>
                <p:output name="data" id="javascript"/>
            </p:processor>
            <p:processor name="oxf:text-serializer">
                <p:input name="config">
                    <config>
                        <content-type>text/plain</content-type>
                    </config>
                </p:input>
                <p:input name="data" href="#javascript"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:xml-serializer">
                <p:input name="config"><config/></p:input>
                <p:input name="data" href="#xquery-output"/>
            </p:processor>
        </p:otherwise>
    </p:choose>
</p:config>
