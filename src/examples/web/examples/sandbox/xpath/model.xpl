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
          xmlns:xupdate="http://www.xmldb.org/xupdate"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:function="http://www.orbeon.com/xslt-function"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:xslt">
        <p:input name="instance" href="#instance"/>
        <p:input name="data" href="input.xml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:saxon="http://saxon.sf.net/">
                <xsl:import href="oxf:/oxf/xslt/utils/evaluate.xsl"/>
                <xsl:template match="/">
                    <result>
                        <xsl:copy-of select="function:evaluate(/*, xs:string(doc('oxf:instance')/form/xpath), ())"/>
                    </result>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
