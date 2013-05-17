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
    xmlns:func="http://exslt.org/functions"
    xmlns:java="http://xml.apache.org/xslt/java"
    xmlns:struts="http://www.orbeon.com/oxf/struts">


    <func:function name="struts:message">
        <xsl:param name="key"/>
        <xsl:param name="bundle"/>
        <xsl:param name="arg0" select="/.."/>
        <xsl:param name="arg1" select="/.."/>
        <xsl:param name="arg2" select="/.."/>
        <xsl:param name="arg3" select="/.."/>
        <xsl:param name="arg4" select="/.."/>

        <xsl:variable name="result" select="java:org.orbeon.oxf.util.StrutsUtils.messageTag($key, $bundle, $arg0, $arg1, $arg2, $arg3, $arg4)"/>
        <func:result select="$result"/>
    </func:function>

    <func:function name="struts:javascript">
        <xsl:param name="formName"/>
        <xsl:param name="dynamicJavascript" select="'true'"/>
        <xsl:param name="staticJavascript" select="'true'"/>
        <xsl:param name="method" select="/.."/>
        <xsl:param name="page" select="/.."/>

        <xsl:variable name="result" select="java:org.orbeon.oxf.util.StrutsUtils.javaScriptTag($formName, $dynamicJavascript, $staticJavascript, $method, $page)"/>
        <func:result select="$result"/>
    </func:function>

</xsl:stylesheet>
