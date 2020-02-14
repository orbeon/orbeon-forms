<!--
  Copyright (C) 2009 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<!--
    Embeddable theme for Form Runner.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:f="http://orbeon.org/oxf/xml/formatting"
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <xsl:template match="/">
        <xh:div>
            <!-- Copy `id` and `lang` from the root element, see https://github.com/orbeon/orbeon-forms/issues/3787. -->
            <xsl:copy-of select="/*/(@id | @lang)"/>
            <!-- Copy `xforms-[dis|en]able-hint-as-tooltip` and `xforms-[dis|en]able-alert-as-tooltip` from the body to the div -->
            <!-- NOTE: Since Orbeon Forms 2016.2, the XForms engine places the hint classes on the `<xh:form>` element. -->
            <xsl:variable  name="classes-to-copy" select="p:split(/xh:html/xh:body/@class)[matches(., '^xforms-(dis|en)able-[^-]+-as-tooltip$')]"/>
            <xsl:attribute name="class"           select="string-join(('orbeon orbeon-portlet-div', $classes-to-copy), ' ')"/>
            <!-- Handle head elements except scripts -->
            <xsl:for-each select="/xh:html/xh:head/(xh:meta | xh:link | xh:style)">
                <xsl:element name="xh:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:copy-of select="@*"/>
                    <xsl:apply-templates/>
                </xsl:element>
            </xsl:for-each>
            <!-- Try to get a title and set it on the portlet -->
            <xsl:if test="normalize-space(/xh:html/xh:head/xh:title)">
                <xsl:value-of select="xpl:setTitle(normalize-space(/xh:html/xh:head/xh:title))"/>
            </xsl:if>
            <!-- Handle head scripts if present -->
            <xsl:for-each select="/xh:html/xh:head/xh:script">
                <xsl:element name="xh:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:copy-of select="@* except @defer"/>
                    <xsl:apply-templates/>
                </xsl:element>
            </xsl:for-each>
            <!-- Body -->
            <xh:div class="orbeon-portlet-body">
                <xsl:apply-templates select="/xh:html/xh:body/(node() except xh:script)"/>
            </xh:div>
            <!-- Handle body scripts if present. They can be placed here by `oxf:assets-aggregator` -->
            <xsl:for-each select="/xh:html/xh:body/xh:script">
                <xsl:element name="xh:{local-name()}" namespace="{namespace-uri()}">
                    <xsl:copy-of select="@* except @defer"/>
                    <xsl:apply-templates/>
                </xsl:element>
            </xsl:for-each>
        </xh:div>
    </xsl:template>

    <!-- Remember that we are embeddable -->
    <xsl:template match="xh:form">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xh:input type="hidden" name="orbeon-embeddable" value="true"/>
            <xsl:apply-templates select="node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- If the field is a checkbox, add "[]", remove it. This is to support PHP-based proxies, which might add the brackets. -->
    <xsl:template match="xh:input[@type = 'checkbox']">
        <xsl:copy>
            <xsl:attribute name="name" select="concat(@name, '[]')"/>
            <xsl:apply-templates select="@* except @name | node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Simply copy everything that's not matched -->
    <xsl:template match="@*|node()" priority="-2">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
