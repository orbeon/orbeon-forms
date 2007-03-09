<!--
    Copyright (C) 2006 Orbeon, Inc.

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
        xmlns:xforms="http://www.w3.org/2002/xforms"
        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xhtml="http://www.w3.org/1999/xhtml"
        xmlns:f="http//www.orbeon.com/function">

    <!--<xsl:template match="/"><xsl:copy-of select="/*"/></xsl:template>-->
    <xsl:template match="/">
        <analysis>
            <xsl:variable name="traces" as="element(trace)*" select="/hprof/trace"/>
            <xsl:variable name="top-calls" as="element(call)*"
                    select="$traces/call[starts-with(@method, 'org.orbeon')
                        and count(preceding-sibling::call[starts-with(@method, 'org.orbeon')]) = 0]"/>
            <xsl:variable name="functionalities-ungrouped" as="element(functionality)*">
                <xsl:for-each select="$top-calls">
                    <functionality name="{if (starts-with(@method, 'org.orbeon.saxon')) then 'Saxon (XSLT, XPath in XForms, ...)'
                        else if (starts-with(@method, 'org.orbeon.oxf.transformer.xupdate')) then 'XUpdate'
                        else if (starts-with(@method, 'org.orbeon.oxf.xforms')) then 'XForms'
                        else if (starts-with(@method, 'org.orbeon.oxf.cache')) then 'OPS cache'
                        else if (starts-with(@method, 'org.orbeon.oxf.processor.serializer')) then 'Serialization'
                        else if (starts-with(@method, 'org.orbeon.oxf.xml.dom4j')) then 'Dom4J'
                        else 'Other'}" count="{parent::trace/@count}"/>
                </xsl:for-each>
            </xsl:variable>
            <xsl:variable name="functionalities-unsorted" as="element(functionality)*">
                <xsl:for-each-group select="$functionalities-ungrouped" group-by="@name">
                   <functionality name="{current-group()[1]/@name}" count="{sum(current-group()/@count)}"/>
                </xsl:for-each-group>
            </xsl:variable>
            <xsl:variable name="call-count" as="xs:integer" select="xs:integer(sum($functionalities-unsorted/@count))"/>
            <xsl:for-each select="$functionalities-unsorted">
                <xsl:sort select="@count" order="descending" data-type="number"/>
                <xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:attribute name="frequency" select="@count div $call-count"/>
                </xsl:copy>
            </xsl:for-each>
        </analysis>
    </xsl:template>

</xsl:stylesheet>
