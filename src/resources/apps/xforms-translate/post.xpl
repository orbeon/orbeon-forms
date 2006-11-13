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
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="translate.xpl"/>
        <p:input name="source" href="#instance#xpointer(/translation/source)"/>
        <p:input name="language-pair" href="#instance#xpointer(/translation/language-pair)"/>
        <p:output name="data" id="target"/>
    </p:processor>
    
    <!-- Build response -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="target" href="#target"/>
        <p:input name="config">
            <translation xsl:version="2.0">
                <target>
                    <xsl:value-of select="doc('input:target')"/>
                </target>
            </translation>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
