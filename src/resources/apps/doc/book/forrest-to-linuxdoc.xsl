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
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:formatting="http://www.orbeon.com/oxf/doc/formatting"
    extension-element-prefixes="formatting"
    xmlns:xalan="http://xml.apache.org/xalan">

    <xalan:component prefix="formatting" functions="wrapSource escapeAmps img removeLineBreaks">
        <xalan:script lang="javascript">

            var imagesDirectory = "/home/avernet/tmp";
            var originalIndent = 4;
            var resultIndent = 2;

            // Remove trailing and tailing \n
            function removeTailTrail(text) {
                while (text[0] == "\n" || text[0] == " ")
                    text = text.substr(1);
                while (text[text.length - 1] == "\n" || text[text.length - 1] == " ")
                    text = text.substr(0, text.length - 1);
                return text;
            }

            function countLeadingSpaces(text) {
                var leadingSpacesRegExp = /^\n?([ ]*)/;
                var spacesMatch = leadingSpacesRegExp.exec(text);
                return spacesMatch == null ? 0 : spacesMatch[1].length;
            }

            function wrapSource(text, maxLength) {

                var result = text;
                var firstLineIndent = countLeadingSpaces(result);

                // Iterate over lines
                var lines = result.split("\n");
                result = "";
                for (var i = 0; i &lt; lines.length; i++) {
                    var line = lines[i];
                    line = line.substr(firstLineIndent);
                    line = wrapLine(line, maxLength);
                    result += line + "\n";
                }

                result = escapeAmps(result);
                result = changeIndent(result);
                result = removeTailTrail(result);
                return result;
            }

            // Wraps a line to make is shorter that maxLength if possible
            function wrapLine(line, maxLength) {
                if (line.length &lt;= maxLength) {
                    return line;
                } else {

                    // Compute indentation
                    var leadingSpaces = countLeadingSpaces(line);
                    var indentLevel = countLeadingSpaces(line) + originalIndent;
                    var indent = "\n" + mult(" ", indentLevel);

                    // Find wrap point
                    var wrapPoint = -1;

                    // First before the maxLength, then after
                    var lastSpace = line.substr(0, maxLength).lastIndexOf(" ");
                    if (lastSpace &gt; leadingSpaces) {
                        wrapPoint = lastSpace;
                    } else {
                        var firstSpace = line.substr(leadingSpaces).indexOf(" ");
                        if (firstSpace != -1) {
                            wrapPoint = leadingSpaces + firstSpace;
                        }
                    }

                    // Do wrapping if we found a wrap point
                    if (wrapPoint == -1) {
                        // Can't do anything
                        return line;
                    } else {
                        // Can wrap before maxLength
                        return line.substr(0, wrapPoint) + indent
                            + wrapLine(mult(" ", leadingSpaces) + line.substr(wrapPoint + 1), maxLength);
                    }
                }
            }

            // Change indentation from originalIndent to resultIndent
            function changeIndent(text) {
                var lines = text.split("\n");
                var result = "";
                for (var i = 0; i &lt; lines.length; i++) {
                    var line = lines[i];
                    var indent = countLeadingSpaces(line);
                    result += mult(" ", indent/originalIndent*resultIndent) + line.substr(indent) + "\n";
                }
                return result;
            }

            function mult(text, n) {
                var result = "";
                for (var i = 0; i &lt; n; i++ )
                    result += text;
                return result;
            }

            // Double escape lt and gt and remove (tm)
            function escapeAmps(text) {
                text = text.replace(/&#8482;/g, "");
                text = text.replace(/&lt;/g, "&amp;lt;");
                text = text.replace(/&gt;/g, "&amp;gt;");
                return text;
            }

            function removeLeading(text) {
                return text.replace(/^[\n ]+/, " ");
            }

            function removeTrailing(text) {
                return text.replace(/[\n ]+$/, " ");
            }

            function img(src, printFormat) {
                var fileNameRegExp = /(.*\.)[a-z]+/;
                var fileNameMatch = fileNameRegExp.exec(src);
                return imagesDirectory + "/" + fileNameMatch[1] + printFormat;
            }

        </xalan:script>
    </xalan:component>

    <xsl:template match="/">
        <book>
            <titlepag>
                <title>Orbeon Forms User Manual version 2.8.0</title>
                <author>Orbeon, Inc.</author>
                <date>March 3 2005</date>
                <abstract>
                    Orbeon Forms is the most advanced XML transformation framework built on top of proven J2EE
                    technologies. It stands out in a crowd of existing J2EE frameworks by fully leveraging the
                    ubiquity and the flexibility of XML. Although Orbeon Forms was designed primarily to develop Web
                    applications, it can be used to build all sorts of XML applications, including Enterprise
                    Application Integration (EAI), Content Management Systems (CMS), and more.
                </abstract>
            </titlepag>
            <toc/>
            <xsl:apply-templates/>
        </book>
    </xsl:template>

    <xsl:template match="section">
        <xsl:variable name="level" select="count(ancestor::section)"/>
        <xsl:variable name="element-name">
            <xsl:choose>
                <xsl:when test="$level = 0">chapt</xsl:when>
                <xsl:when test="$level = 1">sect</xsl:when>
                <xsl:when test="$level = 2">sect1</xsl:when>
                <xsl:when test="$level = 3">sect2</xsl:when>
            </xsl:choose>
        </xsl:variable>
        <xsl:element name="{$element-name}">
            <xsl:apply-templates/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="title">
        <xsl:if test="count(preceding-sibling::title) = 0">
            <heading><xsl:apply-templates/></heading>
        </xsl:if>
    </xsl:template>

    <xsl:template match="p">
        <xsl:if test="name(./*[1]) != 'li'">
            <p/>
        </xsl:if>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="img">
        <p/>
        <figure loc="h!">
            <img src="{formatting:img(string(@src), string(@print-format))}"/>
        </figure>
    </xsl:template>

    <xsl:template match="ul">
        <p/><itemize><xsl:apply-templates/></itemize>
    </xsl:template>

    <xsl:template match="ol">
        <p/><enum><xsl:apply-templates/></enum>
    </xsl:template>

    <xsl:template match="li">
        <item><xsl:apply-templates/></item>
    </xsl:template>

    <xsl:template match="table">
        <p/>
        <xsl:variable name="ca">
            <xsl:choose>
                <xsl:when test="@ca"><xsl:value-of select="@ca"/></xsl:when>
                <xsl:otherwise>
                    <xsl:text>|</xsl:text>
                    <xsl:for-each select="tr[1]/*">
                        <xsl:text>l|</xsl:text>
                    </xsl:for-each>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="pdf-part">
                <xsl:for-each select="pdf-part">
                    <tabular ca="{$ca}">
                        <hline/>
                        <xsl:apply-templates select="./*"/>
                    </tabular>
                </xsl:for-each>
            </xsl:when>
            <xsl:otherwise>
                <tabular ca="{$ca}">
                    <hline/>
                    <xsl:apply-templates/>
                </tabular>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="tr">
        <xsl:apply-templates/>
        <rowsep/><hline/>
    </xsl:template>

    <xsl:template match="td|pdf-td">
        <xsl:if test="count(preceding-sibling::*) > 0">
            <xsl:text>|</xsl:text>
        </xsl:if>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="th">
        <xsl:if test="count(preceding-sibling::*) > 0">
            <xsl:text>|</xsl:text>
        </xsl:if>
        <bf><xsl:apply-templates/></bf>
    </xsl:template>

    <xsl:template match="note">
        <p/>
        <bf>Note: </bf>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="warning">
        <p/>
        <bf>Warning: </bf>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="source|xml-source">
        <xsl:variable name="max-length">
            <xsl:choose>
                <xsl:when test="@max-length != ''">
                    <xsl:value-of select="@max-length"/>
                </xsl:when>
                <xsl:otherwise>80</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <p/>
        <tscreen>
            <verb>
                <xsl:variable name="text-source">
                    <xsl:apply-templates mode="source"/>
                </xsl:variable>
                <xsl:value-of select="formatting:wrapSource(string($text-source), string($max-length))"/>
            </verb>
        </tscreen>
    </xsl:template>

    <xsl:template match="code">
        <tt>
            <xsl:variable name="text-source">
                <xsl:apply-templates mode="source"/>
            </xsl:variable>
            <xsl:value-of select="formatting:escapeAmps(string($text-source))"/>
        </tt>
    </xsl:template>

    <xsl:template match="link">
        <xsl:choose>
            <xsl:when test="starts-with(@href, 'http://')">
                <url url="{@href}" name="{string(.)}"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:apply-templates/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="*" mode="source">
        <xsl:text>&lt;</xsl:text>
        <xsl:value-of select="name()"/>

        <xsl:variable name="prefix" select="substring-before(name(), ':')"/>
        <xsl:if test="$prefix != ''">
            <xsl:variable name="same-prefix-ancestors" select="ancestor::*[starts-with(name(), concat($prefix, ':'))]"/>
            <xsl:if test="not($same-prefix-ancestors)">
                <xsl:text> </xsl:text>
                <xsl:value-of select="concat('xmlns:', $prefix)"/>
                <xsl:text>="</xsl:text>
                <xsl:value-of select="namespace-uri()"/>
                <xsl:text>"</xsl:text>
            </xsl:if>
        </xsl:if>

        <xsl:for-each select="@*">
            <xsl:text> </xsl:text>
            <xsl:value-of select="name()"/>
            <xsl:text>="</xsl:text>
            <xsl:value-of select="."/>
            <xsl:text>"</xsl:text>
        </xsl:for-each>

        <xsl:choose>
            <xsl:when test="count(./node()) > 0">
                <xsl:text>&gt;</xsl:text>
                <xsl:apply-templates mode="source"/>
                <xsl:text>&lt;/</xsl:text>
                <xsl:value-of select="name()"/>
                <xsl:text>&gt;</xsl:text>
            </xsl:when>
            <xsl:otherwise>
                <xsl:text>/&gt;</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="text()">
        <xsl:variable name="first" select="formatting:escapeAmps(string(.))"/>
        <xsl:variable name="second">
            <xsl:choose>
                <xsl:when test="following-sibling::link">
                    <xsl:value-of select="formatting:removeTrailing(string($first))"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$first"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:choose>
            <xsl:when test="preceding-sibling::link">
                <xsl:value-of select="formatting:removeLeading(string($second))"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$second"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
