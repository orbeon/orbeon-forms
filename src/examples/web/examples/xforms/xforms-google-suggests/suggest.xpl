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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!--<p:processor name="oxf:identity">-->
        <!--<p:input name="data">-->
            <!--<query>orb</query>-->
        <!--</p:input>-->
        <!--<p:output name="data" id="instance"/>-->
    <!--</p:processor>-->

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="config">
            <config xsl:version="2.0">
                <url>http://www.google.com/complete/search?qu=<xsl:value-of select="/query"/>&amp;hl=en&amp;js=false</url>
            </config>
        </p:input>
        <p:output name="data" id="url-config" debug="config"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#url-config"/>
        <p:output name="data" id="google-response"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#google-response"/>
        <p:input name="config">
            <suggestions xsl:version="2.0">
                <xsl:variable name="suggestions-list" as="xs:string" select="
                        substring-before(substring-after(/html/head/script, 'new Array(&quot;'), '&quot;), new Array(')"/>
                <xsl:for-each select="tokenize($suggestions-list, '&quot;, &quot;')">
                    <suggestion>
                        <xsl:value-of select="."/>
                    </suggestion>
                </xsl:for-each>
            </suggestions>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
