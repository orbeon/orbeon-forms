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
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:d="http://orbeon.org/oxf/xml/document">
  <xsl:template match="/">
    <xsl:variable name="title" select="'OXF Pipeline Inspector'"/>
    <d:document>
      <d:head><d:title><xsl:value-of select="$title"/></d:title></d:head>
      <d:body>
        <d:h1><xsl:value-of select="$title"/></d:h1>
        <xsl:choose>
          <xsl:when test="/inspector/run">
            <xsl:for-each select="/inspector/run">
              <d:table>
                <d:tr>
                  <d:th colspan="15"><xsl:value-of select="request-uri"/></d:th>
                </d:tr>
                <d:tr>
                  <d:th>Data</d:th>
                  <d:th>Output Name</d:th>
                  <d:th>Output Processor Id</d:th>
                  <d:th>Output URI</d:th>
                  <d:th>Input Name</d:th>
                  <d:th>Input Processor Id</d:th>
                  <d:th>Input URI</d:th>
                  <d:th>Received Start Timestamp</d:th>
                  <d:th>After Start Timestamp</d:th>
                  <d:th>Delta</d:th>
                  <d:th>Received End Timestamp</d:th>
                  <d:th>Delta</d:th>
                  <d:th>After End Timestamp</d:th>
                  <d:th>Delta</d:th>
                  <d:th>Total</d:th>
                </d:tr>
                <xsl:for-each select="entry">
                  <xsl:variable name="position" select="position()"/>
                  <xsl:variable name="first" select="../entry[position() = 1]"/>
                  <xsl:variable name="previous" select="../entry[position() = ($position - 1)]"/>
                  <d:tr>
                    <d:td>
                      <a href="inspector?run={../id}&amp;entry={position() - 1}"><img src="image/icon-select.gif" border="0" width="28" height="25"/></a>
                    </d:td>
                    <d:td><xsl:value-of select="output-name"/></d:td>
                    <d:td><xsl:value-of select="output-processor-id"/></d:td>
                    <d:td><xsl:value-of select="output-uri"/></d:td>
                    <d:td><xsl:value-of select="input-name"/></d:td>
                    <d:td><xsl:value-of select="input-processor-id"/></d:td>
                    <d:td><xsl:value-of select="input-uri"/></d:td>
                    <d:td><xsl:value-of select="received-start-document - $first/received-start-document"/></d:td>
                    <d:td><xsl:value-of select="after-start-document - $first/received-start-document"/></d:td>
                    <d:td><xsl:value-of select="after-start-document - received-start-document"/></d:td>
                    <d:td><xsl:value-of select="received-end-document - $first/received-start-document"/></d:td>
                    <d:td><xsl:value-of select="received-end-document - after-start-document"/></d:td>
                    <d:td><xsl:value-of select="after-end-document - $first/received-start-document"/></d:td>
                    <d:td><xsl:value-of select="after-end-document - received-end-document"/></d:td>
                    <d:td><xsl:value-of select="after-end-document - received-start-document"/></d:td>
                  </d:tr>
                </xsl:for-each>
                <d:tr>
                  <d:td colspan="14">Total</d:td>
                  <d:td><xsl:value-of select="entry[position() = last()]/after-end-document - entry[position() = 1]/received-start-document"/></d:td>
                </d:tr>
              </d:table>
              <br/>
            </xsl:for-each>
          </xsl:when>
          <xsl:otherwise>
            <p>Inspector is empty.</p>
          </xsl:otherwise>
        </xsl:choose>
      </d:body>
    </d:document>
  </xsl:template>
</xsl:stylesheet>
