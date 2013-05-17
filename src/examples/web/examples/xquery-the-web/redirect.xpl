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
    
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <redirect-url xsl:version="2.0">
                <path-info>/direct/xquery-the-web</path-info>
                <server-side>false</server-side>
                <parameters>
                    <parameter>
                        <name>url</name>
                        <value><xsl:value-of select="/instance/url"/></value>
                    </parameter>
                    <xsl:choose>
                        <xsl:when test="/instance/xquery-type = 'inline'">
                            <parameter>
                                <name>xquery</name>
                                <value><xsl:value-of select="/instance/xquery"/></value>
                            </parameter>
                        </xsl:when>
                        <xsl:otherwise>
                            <parameter>
                                <name>xquery-url</name>
                                <value><xsl:value-of select="/instance/xquery-url"/></value>
                            </parameter>
                        </xsl:otherwise>
                    </xsl:choose>
                    <parameter>
                        <name>output</name>
                        <value><xsl:value-of select="/instance/output"/></value>
                    </parameter>
                </parameters>
            </redirect-url>
        </p:input>
        <p:output name="data" id="redirect"/>
    </p:processor>

    <p:processor name="oxf:redirect">
        <p:input name="data" href="#redirect"/>
    </p:processor>
    
</p:config>
