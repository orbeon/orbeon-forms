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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:d="http://orbeon.org/oxf/xml/document"
    xmlns:i18n="http://www.example.com/i18n">

    <xsl:template match="/">
        <html>
            <header><title><xsl:copy-of select="/root/title/node()"/></title></header>
            <body>
                <xsl:apply-templates select="/root/body/*"/>
                <br/><hr size="2" width="200" align="left" noshade="true"/>
                <a href="./"><i18n:text key="home"/></a>
                <br/>
                <a href="set-target?form/target=web" d:url-type="action"><i18n:text key="web"/></a> |
                <a href="set-target?form/target=pda" d:url-type="action"><i18n:text key="pda"/></a>
                <br/>
                <a href="logout" d:url-type="action"><i18n:text key="sign-off"/></a> |
                <a href="login"><i18n:text key="sign-in"/></a>
                <p style="margin-top: 0.5em">
                <a href="set-locale?form/locale=en_US" d:url-type="action">
                    <img src="/examples/petstore/images/us_flag.gif" border="0" valign="bottom"/>
                </a>
                &#160;
                <a href="set-locale?form/locale=ja_JP" d:url-type="action">
                    <img src="/examples/petstore/images/ja_flag.gif" border="0"/>
                </a>
                </p>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="home">
        <h2><i18n:text key="welcome"/></h2>
        <a href="/petstore/category?category-id[1]=FISH"><i18n:text key="fish"/></a><br/>
        <a href="/petstore/category?category-id[1]=DOGS"><i18n:text key="dogs"/></a><br/>
        <a href="/petstore/category?category-id[1]=REPTILES"><i18n:text key="reptiles"/></a><br/>
        <a href="/petstore/category?category-id[1]=CATS"><i18n:text key="cats"/></a><br/>
        <a href="/petstore/category?category-id[1]=BIRDS"><i18n:text key="birds"/></a><br/>
    </xsl:template>

    <xsl:template match="page-title">
        <h2><xsl:copy-of select="node()"/></h2>
    </xsl:template>

    <xsl:template match="checkout">
        <p><xsl:copy-of select="node()"/></p>
    </xsl:template>

    <xsl:template match="box-table">
        <table cellpadding="5">
            <xsl:apply-templates/>
        </table>
    </xsl:template>

    <xsl:template match="input[@class = 'quantity']">
        <input size="3" maxlength="3">
            <xsl:copy-of select="@*[name() != 'quantity']"/>
            <xsl:apply-templates/>
        </input>
    </xsl:template>

    <xsl:template match="input[@type = 'submit']">
        <input>
            <xsl:attribute name="value" namespace="http://www.example.com/i18n">
                <xsl:value-of select="@value"/>
            </xsl:attribute>
            <xsl:copy-of select="@*[name() != 'value']"/>
            <xsl:apply-templates/>
        </input>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
