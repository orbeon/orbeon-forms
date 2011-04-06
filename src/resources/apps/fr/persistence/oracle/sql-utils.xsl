<!--
  Copyright (C) 2011 Orbeon, Inc.

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
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:f="http//www.orbeon.com/function">

    <!-- Transforms an id like "personal-information" to "personal_info" -->
    <!-- NOTE: No need to test/replace single quote, as those aren't legal in XForms ids -->
    <xsl:function name="f:xml-to-sql-id" as="xs:string">
        <xsl:param name="id" as="xs:string"/>
        <xsl:param name="max-length" as="xs:integer"/>
        <xsl:variable name="r1" as="xs:string" select="substring(replace($id, '-', '_'), 1, $max-length)"/>
        <!-- Replace dash by underscore and truncate at max-length -->
        <xsl:variable name="r" as="xs:string" select="substring(replace($id, '-', '_'), 1, $max-length)"/>
        <!-- Remove trailing underscore, if there is one -->
        <xsl:sequence select="replace($r, '(.*[^_]).*', '$1')"/>
    </xsl:function>

    <xsl:function name="f:escape-lang">
        <xsl:param name="text" as="xs:string"/>
        <xsl:param name="lang" as="xs:string"/>
        <xsl:value-of select="replace($text, '\[@xml:lang = \$fb-lang\]', concat('[@xml:lang = ''', f:escape-sql($lang), ''']'))"/>
    </xsl:function>

    <xsl:function name="f:escape-sql">
        <xsl:param name="text" as="xs:string"/>
        <xsl:value-of select="replace($text, '''', '''''')"/>
    </xsl:function>

    <xsl:function name="f:namespaces">
        <xsl:param name="query" as="element(query)"/>
        <xsl:for-each select="in-scope-prefixes($query)">
            <xsl:text>xmlns:</xsl:text>
            <xsl:value-of select="."/>
            <xsl:text>="</xsl:text>
            <xsl:value-of select="namespace-uri-for-prefix(., $query)"/>
            <xsl:text>" </xsl:text>
        </xsl:for-each>
    </xsl:function>

</xsl:stylesheet>