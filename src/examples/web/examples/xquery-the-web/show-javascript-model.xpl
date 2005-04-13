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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">
    
    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>
    
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/*</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>
    
    <p:processor name="oxf:xslt">
        <p:input name="data" href="aggregate('root', #instance, #request)"/>
        <p:input name="config">
            <javascript xsl:version="2.0">
                <xsl:variable name="url-start">
                    <xsl:text>http://</xsl:text>
                    <xsl:value-of select="/root/request/server-name"/>
                    <xsl:value-of select="if (/root/request/server-port != '80') then concat(':', /root/request/server-port) else ''"/>
                    <xsl:value-of select="/root/request/context-path"/>
                </xsl:variable>
                <xsl:text>&lt;script language="javascript" type="text/javascript" src="</xsl:text>
                <xsl:value-of select="$url-start"/>
                <xsl:text>/direct/xquery-the-web?url=</xsl:text>
                <xsl:value-of select="escape-uri(/root/instance/url, true())"/>
                <xsl:text>&amp;xquery=</xsl:text>
                <xsl:value-of select="escape-uri(/root/instance/xquery, true())"/>
                <xsl:text>&amp;output=javascript"></xsl:text>
            </javascript>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
