<?xml version="1.0"?>
<!--
    Copyright (C) 2005 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:stylesheet 
  version='2.0' 
  xmlns:xsl='http://www.w3.org/1999/XSL/Transform' 
  xmlns:xsd='http://www.w3.org/2001/XMLSchema' 
  xmlns:xalan='http://xml.apache.org/xalan'
  exclude-result-prefixes='xalan'
>
  
  <xsl:template match='/*' >
    <xsl:text>
    </xsl:text>
    <xsl:comment> 08/31/2005 d : OPS uses MSV for schema validation and MSV doesn't support XSD's keyref.  To deal with    </xsl:comment>
    <xsl:text>
    </xsl:text>
    <xsl:comment>                this we have the OPS build process produce this file, a 'runtime' version if you will,    </xsl:comment>
    <xsl:text>
    </xsl:text>
    <xsl:comment>                which has the key/id stuff commented out.                                                 </xsl:comment>
    <xsl:text>
    </xsl:text>
    <xsl:copy>
      <xsl:apply-templates select='@*|node()'/>
    </xsl:copy>
  </xsl:template>
  
   <xsl:template match='@*|*|text()|processing-instruction()|comment()'>
    <xsl:copy>
      <xsl:apply-templates select='@*|node()'/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match='xsd:keyref' >
    <xsl:text disable-output-escaping='yes' >&lt;!--
        </xsl:text>
    <xsl:copy-of select='.' />
    <xsl:text disable-output-escaping='yes' >
        --&gt;</xsl:text>
  </xsl:template>

</xsl:stylesheet>

