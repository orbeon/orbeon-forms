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
<xhtml:html xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xmlns:quote="http://ws.cdyne.com/"
            xsl:version="2.0">
    <xhtml:head>
        <xhtml:title>Stock Quote</xhtml:title>
    </xhtml:head>
    <xhtml:body>
        <xforms:group>
            <xhtml:p>
                Enter stock symbol (e.g. IBM):
                <xforms:input ref="symbol"/>
                <xsl:text>&#160;</xsl:text>
                <xforms:submit>
                    <xforms:label>Get Stock Price</xforms:label>
                </xforms:submit>
            </xhtml:p>
        </xforms:group>
        <xsl:if test="/quote:GetQuoteResult/quote:StockSymbol != ''">
            <xsl:choose>
                <xsl:when test="/quote:GetQuoteResult/quote:QuoteError = 'true'">
                    <font color="red">Invalid symbol</font>
                </xsl:when>
                <xsl:otherwise>
                    <table class="gridtable">
                        <tr>
                            <th>Stock symbol</th>
                            <td><xsl:value-of select="/quote:GetQuoteResult/quote:StockSymbol"/></td>
                        </tr>
                        <tr>
                            <th>Company name</th>
                            <td><xsl:value-of select="/quote:GetQuoteResult/quote:CompanyName"/></td>
                        </tr>
                        <tr>
                            <th>Last trade amount</th>
                            <td><xsl:value-of select="/quote:GetQuoteResult/quote:LastTradeAmount"/></td>
                        </tr>
                        <tr>
                            <th>Stock change</th>
                            <td><xsl:value-of select="/quote:GetQuoteResult/quote:StockChange"/></td>
                        </tr>
                        <tr>
                            <th>Open amount</th>
                            <td><xsl:value-of select="/quote:GetQuoteResult/quote:OpenAmount"/></td>
                        </tr>
                        <tr>
                            <th>Change percent</th>
                            <td><xsl:value-of select="/quote:GetQuoteResult/quote:ChangePercent"/></td>
                        </tr>
                    </table>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:if>
    </xhtml:body>
</xhtml:html>
