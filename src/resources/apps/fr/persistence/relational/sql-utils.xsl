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
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:f="http//www.orbeon.com/function">

    <!-- Compute sequence of tuples (path, column name) -->
    <!-- NOTE: For instance: personal-information/first-name, personal_infor_first_name, company/name, company_name, ... -->
    <xsl:function name="f:document-to-paths-ids" as="xs:string*">
        <xsl:param name="document" as="element(document)"/>
        <xsl:param name="metadata-column" as="xs:string+"/>

        <xsl:variable name="paths-ids-1" as="xs:string*">
            <!-- Go over sections -->
            <xsl:for-each select="$document//fr:section">
                <xsl:variable name="section-position" select="position()"/>
                <xsl:variable name="section-id" as="xs:string"
                              select="replace(@id, '(.*)(-section)|(-control)', '$1')"/>                                <!-- Also support '-section' for backward compatibility -->
                <!-- Go over controls -->
                <xsl:for-each select=".//*[exists(@bind)]">
                    <xsl:variable name="control-position" select="position()"/>
                    <xsl:variable name="control-id" as="xs:string" select="replace(@id, '(.*)-control', '$1')"/>
                    <!-- Extract value /*/section/control -->
                    <xsl:sequence select="concat(f:escape-sql($section-id), '/', $control-id)"/>
                    <!-- Name of the resulting column (total must not be longer than 30 characters) -->
                    <xsl:sequence select="concat(f:xml-to-sql-id($section-id, 14), '_', f:xml-to-sql-id($control-id, 14))"/>
                </xsl:for-each>
            </xsl:for-each>
        </xsl:variable>

        <!-- Get just the ids -->
        <xsl:variable name="ids" as="xs:string*" select="$paths-ids-1[position() mod 2 = 0]"/>
        <!-- If there there are duplicates, add prefix with number -->
        <xsl:variable name="ids-and-meta" as="xs:string+" select="$ids, for $c in $metadata-column return upper-case(concat('metadata_', replace($c, '-', '_')))"/>
        <xsl:variable name="paths-ids-2" as="xs:string*" select="if (count(distinct-values($ids-and-meta)) = count($ids-and-meta)) then $paths-ids-1 else
            for $i in (1 to count($paths-ids-1) div 2) return
            ($paths-ids-1[$i * 2 - 1], substring(concat(format-number($i, '000'), '_', $paths-ids-1[$i * 2]), 1, 30))"/>

        <xsl:sequence select="$paths-ids-2"/>
    </xsl:function>

    <!-- Transforms an id like "personal-information" to "personal_info" -->
    <xsl:function name="f:xml-to-sql-id" as="xs:string">
        <xsl:param name="id" as="xs:string"/>
        <xsl:param name="max-length" as="xs:integer"/>
        <!-- Replace dash by underscore -->
        <xsl:variable name="r1" as="xs:string" select="replace($id, '-', '_')"/>
        <!-- Make name upper case, as non-quoted name are uppercase by default -->
        <xsl:variable name="r2" as="xs:string" select="upper-case($r1)"/>
        <!-- Remove first character if not alphanumeric -->
        <xsl:variable name="r3" as="xs:string" select="replace($r2, '^[^A-Z0-9]', '')"/>
        <!-- Remove any character if not alphanumeric or underscore -->
        <xsl:variable name="r4" as="xs:string" select="replace($r3, '[^A-Z0-9_]', '')"/>
        <!-- Truncate at max-length -->
        <xsl:variable name="r5" as="xs:string" select="substring($r4, 1, $max-length)"/>
        <!-- Remove trailing underscore, if there is one -->
        <xsl:sequence select="replace($r5, '(.*[^_]).*', '$1')"/>
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

    <xsl:function name="f:namespaces" as="xs:string*">
        <xsl:param name="element" as="element(query)"/>
        <xsl:param name="xpath"   as="xs:string"/>
        <xsl:variable name="xpath-parts" select="tokenize($xpath, '/')" as="xs:string*"/>
        <xsl:for-each select="in-scope-prefixes($element)">
            <xsl:variable name="prefix" select="." as="xs:string"/>
            <xsl:variable name="output-ns" select="exists($xpath-parts[starts-with(., $prefix)])" as="xs:boolean"/>
            <xsl:if test="$output-ns">
                <xsl:sequence select="concat(., '=&quot;', namespace-uri-for-prefix(., $element), '&quot;')"/>
            </xsl:if>
        </xsl:for-each>
    </xsl:function>

    <xsl:function name="f:oracle-namespaces" as="xs:string">
        <xsl:param name="namespaces" as="xs:string*"/>
        <xsl:sequence select="string-join(for $n in $namespaces return concat('xmlns:', $n), ' ')"/>
    </xsl:function>

    <xsl:function name="f:db2-sqlserver-namespaces" as="xs:string">
        <xsl:param name="namespaces" as="xs:string*"/>
        <xsl:sequence select="string-join(for $n in $namespaces return concat('declare namespace ', $n, ';'), ' ')"/>
    </xsl:function>

    <xsl:function name="f:postgresql-namespaces" as="xs:string">
        <xsl:param name="namespaces" as="xs:string*"/>
        <xsl:variable name="nsarrays" as="xs:string*">
            <xsl:sequence select="for $n in $namespaces return concat('ARRAY[''', substring-before($n,'=&quot;'), ''',''', replace(substring-after($n,'=&quot;'), '&quot;$', ''), ''']')"/>
        </xsl:variable>
        <xsl:sequence select="concat('ARRAY[', string-join($nsarrays, ', '), ']::varchar[]')"/>
    </xsl:function>

</xsl:stylesheet>