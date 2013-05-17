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
    xmlns:java="http://xml.apache.org/xslt/java"
    xmlns:math="http://orbeon.com/oxf/xslt/math"
    xmlns:date="http://orbeon.com/oxf/xslt/date"
    xmlns:net="http://orbeon.com/oxf/xslt/net"
    xmlns:string="http://orbeon.com/oxf/xslt/string"
    xmlns:func="http://exslt.org/functions">

    <func:function name="math:max">
        <xsl:param name="p1"/>
        <xsl:param name="p2"/>
        <xsl:variable name="result">
            <xsl:choose>
                <xsl:when test="$p1 > $p2"><xsl:value-of select="$p1"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="$p2"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <func:result select="number($result)"/>
    </func:function>

    <func:function name="math:min">
        <xsl:param name="p1"/>
        <xsl:param name="p2"/>
        <xsl:variable name="result">
            <xsl:choose>
                <xsl:when test="$p1 > $p2"><xsl:value-of select="$p2"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="$p1"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <func:result select="number($result)"/>
    </func:function>

<!--    <func:function name="date:format">-->
<!--        <xsl:param name="date"/>-->
<!--        <xsl:variable name="result">-->
<!--            <xsl:value-of select="substring($date, 1, 10)"/>-->
<!--        </xsl:variable>-->
<!--        <func:result select="string($result)"/>-->
<!--    </func:function>-->

    <func:function name="net:encode-url">
        <xsl:param name="value"/>
        <func:result select="java:java.net.URLEncoder.encode($value)"/>
    </func:function>

    <func:function name="string:replace">
        <xsl:param name="text"/>
        <xsl:param name="old"/>
        <xsl:param name="new"/>
        <func:result select="java:org.apache.commons.lang.StringUtils.replace($text, $old, $new)"/>
    </func:function>

    <!-- FIXME: fixed-size padding string -->
    <func:function name="string:pad">
        <xsl:param name="text"/>
        <xsl:param name="length"/>
        <xsl:variable name="blank" select="'                                                  '"/>
        <xsl:variable name="text-length" select="math:min($length, string-length($text))"/>
        <func:result select="concat(substring($text, 1, $text-length), substring($blank, 1, $length - $text-length))"/>
    </func:function>

    <func:function name="string:string-compare">
        <xsl:param name="left"/>
        <xsl:param name="right"/>
        <xsl:variable name="left-string" select="java:java.lang.String.new(string($left))"/>
        <func:result select="java:compareTo($left-string, string($right))"/>
    </func:function>

    <func:function name="date:format-time-no-seconds">
        <xsl:param name="value"/>
        <func:result select="substring($value, 12, 5)"/>
    </func:function>

    <func:function name="date:format-date-time-no-seconds">
        <xsl:param name="value"/>
        <func:result select="concat(substring($value, 1, 10), ' ', substring($value, 12, 5))"/>
    </func:function>

    <func:function name="date:format-date">
        <xsl:param name="value"/>
        <func:result select="substring($value, 1, 10)"/>
    </func:function>

    <func:function name="date:format-time-delay-no-seconds">
        <xsl:param name="value"/>
        <xsl:variable name="minutes" select="$value mod 60"/>
        <xsl:variable name="minutes-string">
            <xsl:choose>
                <xsl:when test="$minutes > 9"><xsl:value-of select="$minutes"/></xsl:when>
                <xsl:otherwise>0<xsl:value-of select="$minutes"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="hours" select="floor($value div 60)"/>
        <func:result select="concat($hours, ':', $minutes-string)"/>
    </func:function>

    <func:function name="date:validate">
        <xsl:param name="year"/>
        <xsl:param name="month"/>
        <xsl:param name="day"/>
        <xsl:variable name="result" select="java:org.orbeon.oxf.util.ISODateUtils.checkDate(number($year), number($month), number($day))"/>
        <func:result select="boolean($result)"/>
    </func:function>

</xsl:stylesheet>
