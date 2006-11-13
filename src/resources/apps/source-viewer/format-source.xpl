<!--
    Copyright (C) 2006 Orbeon, Inc.

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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="input" name="instance"/>

    <!-- Get current file -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <xsl:variable name="application-id" select="/*/application-id" as="xs:string"/>
                <xsl:variable name="mediatype" select="/*/mediatype" as="xs:string"/>
                <xsl:variable name="url" select="concat('oxf:/', 'apps/', $application-id, '/', string(/*/source-url))" as="xs:string"/>
                <url><xsl:value-of select="$url"/></url>
                <content-type><xsl:value-of select="$mediatype"/></content-type>
            </config>
        </p:input>
        <p:output name="data" id="url-config"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" id="source-file"/>
    </p:processor>

    <!-- Format file -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#source-file"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:saxon="http://saxon.sf.net/">
                <xsl:include href="oxf:/ops/utils/formatting/xml-formatting.xsl"/>
                <xsl:output method="html" omit-xml-declaration="yes" name="html-output"/>
                <xsl:template match="/">
                    <document>
                        <xsl:variable name="formatted-xml">
                            <root>
                                <xsl:apply-templates mode="xml-formatting"/>
                            </root>
                        </xsl:variable>
                        <xsl:choose>
                            <xsl:when test="/document/@content-type = 'text/plain'">
                                &lt;span><xsl:value-of select="replace(replace(replace(/*/text(), '&lt;', '&amp;lt;'), '&#x0a;', '&lt;br>'), ' ', '&#160;')"/>&lt;/span>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="substring-before(substring-after(saxon:serialize($formatted-xml, 'html-output'), '>'), '&lt;/root>')"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </document>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="formatted-source"/>
    </p:processor>

    <!-- Convert and serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <indent>false</indent>
                <encoding>utf-8</encoding>
            </config>
        </p:input>
        <p:input name="data" href="#formatted-source"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config>
                <cache-control>
                    <use-local-cache>false</use-local-cache>
                </cache-control>
            </config>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
