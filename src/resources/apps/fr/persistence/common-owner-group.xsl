<!--
    Copyright (C) 2013 Orbeon, Inc.

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
                xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <xsl:variable name="owner-group"        as="xs:boolean" select="xpl:property('oxf.fr.support-owner-group')"/>
    <xsl:variable name="last-modified-time" as="xs:string"  select="if ($owner-group) then 'last_modified_time' else 'last_modified'"/>
    <xsl:variable name="last-modified-by"   as="xs:string"  select="if ($owner-group) then 'last_modified_by' else 'username'"/>

</xsl:stylesheet>