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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform" >

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance#xpointer(/form/xquery)"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:saxon="http://saxon.sf.net/">
                <xsl:template match="/">
                    <xsl:copy-of select="saxon:parse(concat('&lt;xquery>', string(/), '&lt;/xquery>'))"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="xquery"/>
    </p:processor>

    <p:processor name="oxf:xquery">
        <p:input name="config" href="#xquery"/>
        <p:input name="data" href="input.xml"/>
        <p:output name="data" id="output"/>
    </p:processor>

    <p:processor name="oxf:identity">
        <p:input name="data" href="aggregate('root', aggregate('input', input.xml),
            aggregate('output', #output), #instance)"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
