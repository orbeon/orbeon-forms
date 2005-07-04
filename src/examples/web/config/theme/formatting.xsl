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

    <xsl:variable name="blue" select="'#283c68'"/>
    <xsl:variable name="light-blue" select="'#88acc8'"/>
    <xsl:variable name="orange" select="'#FF9900'"/>
    <xsl:variable name="light-gray" select="'#c8dce8'"/>
    <xsl:variable name="dark-gray" select="'#88acc8'"/>

    <xsl:output name="html" method="html"/>

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

    <xsl:template name="horizontal-tab">
        <xsl:param name="caption"/>
        <xsl:param name="selected" select="true()"/>
        <xsl:param name="ref"/>
        <xsl:param name="form-value"/>
        <xsl:param name="width"/>
        <xsl:variable name="bgcolor">
            <xsl:choose>
                <xsl:when test="$selected"><xsl:value-of select="$blue"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="$light-blue"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <table cellpadding="0" cellspacing="0" border="0" bgcolor="{$bgcolor}">
            <xsl:if test="not($selected) and $ref and $form-value">
                <xsl:attribute name="onmousedown">top.document.forms['wsrp_rewrite_xforms']['form/<xsl:value-of select="$ref"/>'].value = '<xsl:value-of select="$form-value"/>'; return top.document.forms['wsrp_rewrite_xforms'].submit();</xsl:attribute>
            </xsl:if>
            <xsl:attribute name="style">
                <xsl:choose>
                    <xsl:when test="$width and not($selected)">cursor: pointer; cursor: hand; width: <xsl:value-of select="$width"/></xsl:when>
                    <xsl:when test="$width">width: <xsl:value-of select="$width"/></xsl:when>
                    <xsl:when test="not($selected)">cursor: pointer; cursor: hand</xsl:when>
                    <xsl:otherwise></xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <tr>
                <xsl:choose>
                    <xsl:when test="$selected">
                        <td valign="top"><img alt="top left curve" src="images/curve-tl-s.gif" width="8" height="8"/></td>
                        <td colspan="1" style="padding: 8px; color: white" align="center">
                            <b><xsl:value-of select="$caption"/></b>
                        </td>
                        <td><img src="images/spacer.gif" alt="spacer"/></td>
                        <td valign="top" align="right"><img alt="top right curve" src="images/curve-tr-s.gif" width="8" height="8"/></td>
                    </xsl:when>
                    <xsl:otherwise>
                        <td valign="top"><img alt="top left curve" src="images/curve-tl-s-2.gif" width="8" height="8"/></td>
                        <td colspan="1" style="padding: 8px; color: white" align="center">
                            <b><xsl:value-of select="$caption"/></b>
                        </td>
                        <td><img src="images/spacer.gif" alt="spacer"/></td>
                        <td valign="top" align="right"><img alt="top right curve" src="images/curve-tr-s-2.gif" width="8" height="8"/></td>
                    </xsl:otherwise>
                </xsl:choose>
            </tr>
        </table>
    </xsl:template>

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

    <xsl:template match="f:global-errors">
        <xsl:variable name="display-as-popup" select="true()"/>

        <xsl:apply-templates select="xhtml:input"/>
        <xsl:variable name="error-table">
            <table border="0" cellspacing="0" cellpadding="0" style="margin-bottom: 1em">
                <xsl:for-each select="xforms:alert">
                    <tr>
                        <td><img src="../images/error.gif"/></td>
                        <td style="padding-left: .2em; color: red" width="100%">
                            <span><xsl:value-of select="@xxforms:error"/></span>
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
<!--                        <xsl:variable name="error-string" select="xmlutils:domToString($error-table/*)"/>-->
                        <xsl:variable name="error-string" select="saxon:serialize($error-table/*, 'html')"/>
                        <xsl:variable name="error-one-line" select="replace(replace($error-string, '&#xd;', ''), '&#xa;', '')"/>
<!--                        <xsl:variable name="error-string">111</xsl:variable>-->
<!--                        <xsl:variable name="error-one-line" select="replace(replace($error-string, '&#xd;', ''), '&#xa;', '')"/>-->
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

</xsl:stylesheet>
