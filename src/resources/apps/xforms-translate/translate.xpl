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

    <p:param name="source" type="input"/>
    <p:param name="language-pair" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="data"><dummy/></p:input>
        <p:input name="source" href="#source"/>
        <p:input name="language-pair" href="#language-pair"/>
        <p:input name="config">
            <config xsl:version="2.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <url>
                    <xsl:variable name="language-pair" as="xs:string" select="doc('input:language-pair')"/>
                    <xsl:text>http://translate.google.com/translate_a/t?client=t&amp;text=</xsl:text>
                    <xsl:value-of select="encode-for-uri(doc('input:source'))"/>
                    <xsl:text>&amp;sl=</xsl:text>
                    <xsl:value-of select="substring-before($language-pair, '|')"></xsl:value-of>
                    <xsl:text>&amp;tl=</xsl:text>
                    <xsl:value-of select="substring-after($language-pair, '|')"></xsl:value-of>
                    <xsl:text>&amp;otf=1&amp;pc=0</xsl:text>
                </url>
                <content-type>text/html</content-type>
                <header>
                    <name>User-Agent</name>
                    <value>Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.4.1) Gecko/20031008</value>
                </header>
            </config>
        </p:input>
        <p:output name="data" id="google-request"/>
    </p:processor>

    <p:processor name="oxf:url-generator">
        <p:input name="config" href="#google-request"/>
        <p:output name="data" id="google-translation"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#google-translation"/>
        <p:input name="config">
            <data xsl:version="2.0">
                <xsl:variable name="after-opening-quote" select="substring-after(., '[[[&quot;')"/>
                <xsl:variable name="before-closing-quote" select="substring-before($after-opening-quote, '&quot;,&quot;')"/>
                <xsl:variable name="replace-escaped-quotes" select="replace($before-closing-quote, '\\&quot;', '&quot;')"/>
                <xsl:value-of select="$replace-escaped-quotes"/>
            </data>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
