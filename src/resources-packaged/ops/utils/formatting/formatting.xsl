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

    <!-- - - - - - - Nicely format XML - - - - - - -->

    <xsl:template match="f:xml-source">
        <xsl:choose>
            <xsl:when test="@ignore-root-element = 'true'">
                <xsl:apply-templates mode="xml-formatting" select="*[1]/node()">
                    <xsl:with-param name="show-namespaces" select="not(@show-namespaces = 'false')" tunnel="yes"/>
                </xsl:apply-templates>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates mode="xml-formatting">
                    <xsl:with-param name="show-namespaces" select="not(@show-namespaces = 'false')" tunnel="yes"/>
                </xsl:apply-templates>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
