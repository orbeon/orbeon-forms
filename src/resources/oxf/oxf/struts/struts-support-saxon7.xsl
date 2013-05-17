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
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:java="java:org.orbeon.oxf.util.StrutsUtils"
    xmlns:struts="http://www.orbeon.com/oxf/struts"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">


    <xsl:function name="struts:message" as="xs:string">
        <xsl:param name="key" as="xs:string"/>

        <xsl:variable name="result" select="java:messageTag($key)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:message"  as="xs:string">
        <xsl:param name="key" as="xs:string"/>
        <xsl:param name="bundle" as="xs:string"/>

        <xsl:variable name="result" select="java:messageTag($key, $bundle)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:message"  as="xs:string">
        <xsl:param name="key" as="xs:string"/>
        <xsl:param name="bundle" as="xs:string"/>
        <xsl:param name="arg0" as="xs:string"/>

        <xsl:variable name="result" select="java:messageTag($key, $bundle, $arg0)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:message" as="xs:string">
        <xsl:param name="key" as="xs:string"/>
        <xsl:param name="bundle" as="xs:string"/>
        <xsl:param name="arg0" as="xs:string"/>
        <xsl:param name="arg1" as="xs:string"/>

        <xsl:variable name="result" select="java:messageTag($key, $bundle, $arg0, $arg1)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:message" as="xs:string">
        <xsl:param name="key" as="xs:string"/>
        <xsl:param name="bundle" as="xs:string"/>
        <xsl:param name="arg0" as="xs:string"/>
        <xsl:param name="arg1" as="xs:string"/>
        <xsl:param name="arg2" as="xs:string"/>

        <xsl:variable name="result" select="java:messageTag($key, $bundle, $arg0, $arg1, $arg2)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:message" as="xs:string">
        <xsl:param name="key" as="xs:string"/>
        <xsl:param name="bundle" as="xs:string"/>
        <xsl:param name="arg0" as="xs:string"/>
        <xsl:param name="arg1" as="xs:string"/>
        <xsl:param name="arg2" as="xs:string"/>
        <xsl:param name="arg3" as="xs:string"/>

        <xsl:variable name="result" select="java:messageTag($key, $bundle, $arg0, $arg1, $arg2, $arg3)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:message" as="xs:string">
        <xsl:param name="key" as="xs:string"/>
        <xsl:param name="bundle" as="xs:string"/>
        <xsl:param name="arg0" as="xs:string"/>
        <xsl:param name="arg1" as="xs:string"/>
        <xsl:param name="arg2" as="xs:string"/>
        <xsl:param name="arg3" as="xs:string"/>
        <xsl:param name="arg4" as="xs:string"/>

        <xsl:variable name="result" select="java:messageTag($key, $bundle, $arg0, $arg1, $arg2, $arg3, $arg4)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>




    <xsl:function name="struts:javascript"  as="xs:string">
        <xsl:param name="formName" as="xs:string"/>
        <xsl:param name="dynamicJavascript" as="xs:string"/>
        <xsl:param name="staticJavascript" as="xs:string"/>
        <xsl:param name="method" as="xs:string"/>
        <xsl:param name="page" as="xs:string"/>

        <xsl:variable name="result" select="java:javaScriptTag($formName, $dynamicJavascript, $staticJavascript, $method, $page)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:javascript"  as="xs:string">
        <xsl:param name="formName" as="xs:string"/>
        <xsl:param name="dynamicJavascript" as="xs:string"/>
        <xsl:param name="staticJavascript" as="xs:string"/>
        <xsl:param name="method" as="xs:string"/>

        <xsl:variable name="result" select="java:javaScriptTag($formName, $dynamicJavascript, $staticJavascript, $method)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:javascript"  as="xs:string">
        <xsl:param name="formName" as="xs:string"/>
        <xsl:param name="dynamicJavascript" as="xs:string"/>
        <xsl:param name="staticJavascript" as="xs:string"/>

        <xsl:variable name="result" select="java:javaScriptTag($formName, $dynamicJavascript, $staticJavascript)"/>
        <xsl:value-of select="$result"/>
     </xsl:function>

    <xsl:function name="struts:javascript"  as="xs:string">
        <xsl:param name="formName" as="xs:string"/>
        <xsl:param name="dynamicJavascript" as="xs:string"/>

        <xsl:variable name="result" select="java:javaScriptTag($formName, $dynamicJavascript)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

    <xsl:function name="struts:javascript"  as="xs:string">
        <xsl:param name="formName" as="xs:string"/>

        <xsl:variable name="result" select="java:javaScriptTag($formName)"/>
        <xsl:value-of select="$result"/>
    </xsl:function>

</xsl:stylesheet>
