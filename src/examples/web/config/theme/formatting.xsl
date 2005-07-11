<!--
    Copyright 2004 Orbeon, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:saxon="http://saxon.sf.net/"
    xmlns="http://www.w3.org/1999/xhtml">

    <!-- - - - - - - XML formatting - - - - - - -->

    <xsl:include href="xml-formatting.xsl"/>

    <!-- - - - - - - Default colors - - - - - - -->

    <xsl:variable name="light-gray" select="'#c8dce8'"/>

    <!-- - - - - - - Nicely format XML - - - - - - -->

    <xsl:template match="f:xml-source">
        <xsl:apply-templates mode="xml-formatting">
            <xsl:with-param name="show-namespaces" select="not(@show-namespaces = 'false')"/>
        </xsl:apply-templates>
    </xsl:template>

    <!-- - - - - - Other templates - - - - - - -->

    <!-- This is used by some examples -->
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
                                <a href="/goto-source/{$page/path}/{escape-uri(string(.), true())}">
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

    <!-- Display a dashed box -->
    <xsl:template match="f:box">
        <table border="0" cellpadding="0" cellspacing="0" style="margin-bottom: 2em; width: 100%">
            <tr>
                <td class="dashedbox">
                    <xsl:apply-templates/>
                </td>
            </tr>
        </table>
    </xsl:template>

    <!-- Display tabs -->
    <xsl:template match="f:tabs">
        <xsl:if test="f:tab">
            <div class="subtabs">
                <xsl:for-each select="f:tab">
                    <xsl:apply-templates select="."/>
                </xsl:for-each>
            </div>
        </xsl:if>
    </xsl:template>

    <xsl:template match="f:tab">
        <xsl:variable name="tab" select="."/>
        <xsl:variable name="selected" select="$tab/@selected = 'true'"/>
        <xsl:variable name="bgcolor">
            <xsl:choose>
                <xsl:when test="$selected"><xsl:value-of select="$light-gray"/></xsl:when>
                <xsl:otherwise>white</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <xsl:choose>
            <xsl:when test="$tab/@href">
                <a class="subtab" href="{$tab/@href}" f:url-type="action"><xsl:value-of select="$tab/@label"/></a>
            </xsl:when>
            <xsl:otherwise>
                <span class="subtab"><xsl:value-of select="$tab/@label"/></span>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- - - - - - - Legacy XForms templates - - - - - - -->

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

</xsl:stylesheet>
