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
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xmlutils="java:org.orbeon.oxf.xml.XMLUtils"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms">

    <xsl:include href="oxf:/inspector/xml-formatting.xsl"/>

    <xsl:template match="/">
        <html>
            <head>
                <title>
                    <xsl:choose>
                        <xsl:when test="/xhtml:html/xhtml:head/xhtml:title != ''">
                            <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:title"/>
                        </xsl:when>
                        <xsl:when test="/xhtml:html/xhtml:body/xhtml:example-header">
                            <xsl:variable name="title" select="/xhtml:html/xhtml:body/xhtml:example-header/*/title"/>
                            <xsl:value-of select="$title"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="/xhtml:html/xhtml:body/xhtml:h1"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </title>
                <link rel="stylesheet" href="/oxf-theme/orbeon-layout.cssd" type="text/css"/>
                <xsl:if test="/xhtml:html/xhtml:head/xhtml:style">
                    <style type="text/css">
                        <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:style"/>
                    </style>
                </xsl:if>
                <xsl:if test="/xhtml:html/xhtml:head/xhtml:script">
                    <script type="{/xhtml:html/xhtml:head/xhtml:script/@type}">
                        <xsl:value-of select="/xhtml:html/xhtml:head/xhtml:script/node()"/>
                    </script>
                </xsl:if>
                <script type="text/javascript" src="/oxf-theme/style/theme.js"/>
            </head>
            <!-- This gives a little nudge to IE, so IE displays all the borders -->
            <body onload="document.body.innerHTML += ''">
                <xsl:apply-templates select="/xhtml:html/xhtml:body/node()"/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="f:alerts">
        <table cellpadding="0" cellspacing="0" border="0" style="background: #FFCCCC; margin: 1em; padding: .5em">
            <tr>
                <td valign="top"><img src="/images/error-large.gif"/></td>
                <td valign="top" style="padding-left: 1em">
                    Please correct the errors on this page.
                    <xsl:if test="f:alert">
                        <ul>
                            <xsl:for-each select="f:alert">
                                <li><xsl:copy-of select="node()"/></li>
                            </xsl:for-each>
                        </ul>
                    </xsl:if>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="xhtml:p">
        <p class="gen">
            <xsl:copy-of select="@*[name() != 'class']"/>
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <xsl:template match="xhtml:ul">
        <ul class="gen">
            <xsl:apply-templates/>
        </ul>
    </xsl:template>

    <xsl:template match="xhtml:ol">
        <ol class="gen">
            <xsl:apply-templates/>
        </ol>
    </xsl:template>

    <xsl:template match="xhtml:textarea">
        <textarea cols="20" wrap="soft">
            <xsl:apply-templates select="@*|node()"/>
        </textarea>
    </xsl:template>

    <xsl:template match="xhtml:input[@type = 'text']">
        <input>
            <xsl:apply-templates select="@*|node()"/>
        </input>
    </xsl:template>

    <xsl:template match="f:source">
        <pre>
            <xsl:call-template name="ignore-first-empty-lines">
                <xsl:with-param name="text" select="."/>
            </xsl:call-template>
        </pre>
    </xsl:template>

    <xsl:template match="f:xml-source">
        <xsl:apply-templates mode="xml-formatting">
            <xsl:with-param name="show-namespaces" select="not(@show-namespaces = 'false')"/>
        </xsl:apply-templates>
    </xsl:template>

    <xsl:template name="ignore-first-empty-lines">
        <xsl:param name="text"/>
        <xsl:variable name="first-line" select="substring-before($text, '&#xA;')"/>
        <xsl:choose>
            <xsl:when test="normalize-space($first-line) = ''">
                <!-- First line empty, skip it -->
                <xsl:call-template name="ignore-first-empty-lines">
                    <xsl:with-param name="text" select="substring-after($text, '&#xA;')"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <!-- Start truncating the margin -->
                <xsl:call-template name="truncate-margin">
                    <xsl:with-param name="text" select="$text"/>
                    <xsl:with-param name="width">
                        <xsl:call-template name="leading-spaces-count">
                            <xsl:with-param name="text" select="substring-before($text, '&#xA;')"/>
                        </xsl:call-template>
                    </xsl:with-param>
                </xsl:call-template>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="leading-spaces-count">
        <xsl:param name="text"/>
        <xsl:choose>
            <xsl:when test="substring($text, 1, 1) = ' '">
                <xsl:variable name="recurse">
                    <xsl:call-template name="leading-spaces-count">
                        <xsl:with-param name="text" select="substring($text, 2, string-length($text) - 1)"/>
                    </xsl:call-template>
                </xsl:variable>
                <xsl:value-of select="$recurse + 1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="0"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="truncate-margin">
        <xsl:param name="text"/>
        <xsl:param name="width"/>
        <xsl:variable name="first-line" select="substring-before($text, '&#xA;')"/>
        <xsl:variable name="rest" select="substring-after($text, '&#xA;')"/>
        <xsl:value-of select="substring($first-line, $width + 1)"/>
        <xsl:if test="substring-after($rest, '&#xA;') != ''">
            <xsl:value-of select="'&#xA;'"/>
            <xsl:call-template name="truncate-margin">
                <xsl:with-param name="text" select="$rest"/>
                <xsl:with-param name="width" select="$width"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <xsl:template match="f:example-header">
        <xsl:variable name="title" select="@title"/>
        <xsl:variable name="page" select="*"/>

        <h1 style="margin-bottom: 1em">
            <xsl:value-of select="$page/title"/>
        </h1>
        <table border="0" cellpadding="0" cellspacing="0" style="margin-bottom: 2em"><tr><td class="dashedbox">
            <div class="rightbox" style="float: right">
                <xsl:for-each select="$page/source">
                    <nobr>
                        <xsl:choose>
                            <xsl:when test="@html = 'true'">
                                <a href="/{.}.html"><xsl:value-of select="."/></a>
                            </xsl:when>
                            <xsl:when test="@html = 'false'">
                                <a href="/{.}"><xsl:value-of select="."/></a>
                            </xsl:when>
                            <xsl:otherwise>
                                <a href="/examples/source?src={$page/path}/{escape-uri(string(.), true())}">
                                    <xsl:value-of select="string(.)"/>
                                </a>
                            </xsl:otherwise>
                        </xsl:choose>
                    </nobr>
                    <br/>
                </xsl:for-each>
            </div>
            <xsl:variable name="first-p" select="generate-id($page/description/xhtml:p[1])"/>
            <xsl:for-each select="$page/description/node()">
                <xsl:choose>
                    <xsl:when test="generate-id(.) = $first-p">
                        <p style="margin-top: 0">
                            <xsl:apply-templates select="node()"/>
                        </p>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="."/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:for-each>
        </td></tr></table>
    </xsl:template>

    <xsl:template match="f:box">
        <table border="0" cellpadding="0" cellspacing="0" style="margin-bottom: 2em; width: 100%">
            <tr>
                <td class="dashedbox">
                    <xsl:apply-templates/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <xsl:template match="f:global-errors">
        <xsl:variable name="display-as-popup" select="true()"/>
        
        <xsl:apply-templates select="xhtml:input"/>
        <xsl:variable name="error-table">
            <table border="0" cellspacing="0" cellpadding="0" style="margin-bottom: 1em">
                <xsl:for-each select="xforms:alert">
                    <tr>
                        <td><img src="../images/error.gif"/></td>
                        <td style="padding-left: .2em; color: red" width="100%">
                            <span class="gen"><xsl:value-of select="@xxforms:error"/></span>
                        </td>
                    </tr>
                </xsl:for-each>
            </table>
        </xsl:variable>

        <xsl:choose>
            <xsl:when test="$display-as-popup">
                <!-- Open popup with errors -->
                <xsl:if test="xforms:alert">
                    <script language="JavaScript">
                        <xsl:variable name="error-string" select="xmlutils:domToString($error-table/*)"/>
                        <xsl:variable name="error-one-line" select="replace(replace($error-string, '&#xd;', ''), '&#xa;', '')"/>
                        window.open('xforms-ubl-popup', '_blank', 'height=200,width=400,status=no,toolbar=no,menubar=no,location=no').error = '<xsl:value-of select="$error-one-line"/>';
                    </script>
                </xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <!-- Display error in current page -->
                <xsl:copy-of select="$error-table/*"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- Just copy other "xhtml" elements removing the namespace -->
    <xsl:template match="xhtml:*">
        <xsl:element name="{local-name()}">
            <xsl:apply-templates select="@*|node()"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="@xhtml:*">
        <xsl:attribute name="{local-name()}">
            <xsl:value-of select="."/>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
