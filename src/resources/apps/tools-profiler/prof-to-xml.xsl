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
<hprof xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:xforms="http://www.w3.org/2002/xforms"
            xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
            xmlns:xs="http://www.w3.org/2001/XMLSchema"
            xmlns:f="http://orbeon.org/oxf/xml/formatting"
            xmlns:xhtml="http://www.w3.org/1999/xhtml"
            xsl:version="2.0">

    <!-- Parse text file in lines -->
    <xsl:variable name="lines" as="element(line)*">
        <xsl:analyze-string select="/*" regex="\n">
            <xsl:non-matching-substring>
                <line><xsl:value-of select="."/></line>
            </xsl:non-matching-substring>
        </xsl:analyze-string>
    </xsl:variable>

    <!-- Location section with CPU samples -->
    <xsl:variable name="samples-with-sentinels" as="element(group)*">
        <xsl:for-each-group select="$lines" group-starting-with="line[starts-with(., 'CPU SAMPLES BEGIN')]">
            <group>
                <xsl:copy-of select="current-group()[position() > 1]"/>
            </group>
        </xsl:for-each-group>
    </xsl:variable>

    <!-- Lines that contains the CPU samples -->
    <xsl:variable name="samples-lines" as="element(line)*" select="$samples-with-sentinels[position() = 2]/line[position() > 1 and position() != last()]"/>

    <xsl:variable name="samples" as="element(sample)*">
        <xsl:for-each select="$samples-lines">
            <xsl:variable name="tokens" as="xs:string*" select="tokenize(., '\s+')"/>
            <sample trace="{$tokens[5]}" count="{$tokens[4]}"/>
        </xsl:for-each>
    </xsl:variable>

    <!-- Group lines related to the same trace in a trace element -->
    <xsl:variable name="traces-with-sentinels" as="element(trace)*">
        <xsl:for-each-group select="$lines" group-starting-with="line[(starts-with(., 'TRACE') and contains(., ':')) or starts-with(., 'CPU SAMPLES BEGIN')]">
            <trace>
                <xsl:copy-of select="current-group()"/>
            </trace>
        </xsl:for-each-group>
    </xsl:variable>
    <xsl:variable name="traces-with-lines" as="element(trace)*" select="$traces-with-sentinels[position() > 1 and position() != last()]"/>

    <!-- Parse content of a line into arguments: class.method, file, line -->
    <xsl:variable name="traces-all" as="element(trace)*">
        <xsl:for-each select="$traces-with-lines">
            <xsl:variable name="trace-id" as="xs:string" select="substring-before(substring-after(line[1], ' '), ':')"/>
            <xsl:variable name="sample" as="element(sample)?" select="$samples[@trace = $trace-id]"/>
            <!-- We have cases where there are traces but no corresponding sample, e.g. if multiple dumps
                 are done and some of the traces from previous dumps have sample in the current dump. -->
            <xsl:if test="exists($sample)">
                <trace count="{$sample/@count}">
                    <xsl:for-each select="line[position() > 1]">
                        <call method="{substring(substring-before(., '('), 2)}"
                                file="{substring-before(substring-after(., '('), ':')}"
                                line="{substring-before(substring-after(., ':'), ')')}"/>
                    </xsl:for-each>
                </trace>
            </xsl:if>
        </xsl:for-each>
    </xsl:variable>

    <!-- Remove traces that do not contain any org.orbeon methods -->
    <xsl:variable name="traces-orbeon" as="element(trace)*" select="$traces-all[call[starts-with(@method, 'org.orbeon')]]"/>

    <xsl:copy-of select="$traces-orbeon"/>
</hprof>
