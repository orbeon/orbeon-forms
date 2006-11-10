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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template match="navigation">
    <xsl:apply-templates select="book"/>
  </xsl:template>
  <xsl:template match="book">
    <div class="menu">
      <ul>
        <xsl:apply-templates select="menu"/>
      </ul>
    </div>
  </xsl:template>
  <xsl:template match="menu">
    <li>
      <xsl:choose>
        <xsl:when test="not(@href)">
          <font color="#333333"><xsl:value-of select="@label"/></font>
        </xsl:when>
        <xsl:when test="@href = string(/navigation/page)">
          <span class="sel"><font color="#ffcc00"><xsl:value-of select="@label"/></font></span>
        </xsl:when>
        <xsl:otherwise>
          <a href="{@href}"><xsl:value-of select="@label"/></a>
        </xsl:otherwise>
      </xsl:choose>
      <ul>
        <xsl:apply-templates/>
      </ul>
      <br/>
    </li>
  </xsl:template>
  <xsl:template match="menu-item">
    <li>
       <xsl:choose>
         <xsl:when test="@href = string(/navigation/page)">
          <span class="sel"><font color="#ffcc00"><xsl:value-of select="@label"/></font></span>
        </xsl:when>
        <xsl:otherwise>
          <a href="{@href}"><xsl:value-of select="@label"/></a>
        </xsl:otherwise>
      </xsl:choose>
    </li>
  </xsl:template>
  <xsl:template match="external">
    <li>
       <xsl:choose>
         <xsl:when test="@href = string(/navigation/page)">
          <font color="#ffcc00"><xsl:value-of select="@label"/></font>
        </xsl:when>
        <xsl:otherwise>
          <a href="{@href}"><xsl:value-of select="@label"/></a>
        </xsl:otherwise>
      </xsl:choose>
    </li>
  </xsl:template>
  <xsl:template match="menu-item[@type='hidden']"/>
  <xsl:template match="external[@type='hidden']"/>
</xsl:stylesheet>
