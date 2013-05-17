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
  <xsl:import href="book2menu.xsl"/>

  <xsl:template match="menu">
    <li>
      <xsl:choose>
        <xsl:when test="not(@href)">
          <font color="#FF9933"><xsl:value-of select="@label"/></font>
        </xsl:when>
        <xsl:when test="@href = string(/navigation/page)">
          <span class="sel"><font color="#ffcc00"><xsl:value-of select="@label"/></font></span>
        </xsl:when>
        <xsl:otherwise>
          
          <xsl:choose>
            <xsl:when test="not(contains(@href, '.pdf'))">
              <a href="{@href}.html"><xsl:value-of select="@label"/></a>
            </xsl:when>
            <xsl:otherwise>
              <a href="{@href}"><xsl:value-of select="@label"/></a>
            </xsl:otherwise>
          </xsl:choose>
          
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
            <xsl:choose>
              <xsl:when test="not(contains(@href, '.pdf'))">
                <a href="{@href}.html"><xsl:value-of select="@label"/></a>
              </xsl:when>
              <xsl:otherwise>
                <a href="{@href}"><xsl:value-of select="@label"/></a>
              </xsl:otherwise>
            </xsl:choose>
        </xsl:otherwise>
      </xsl:choose>
    </li>
  </xsl:template>


</xsl:stylesheet>