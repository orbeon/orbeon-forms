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

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../database/database.xpl"/>
        <p:output name="data" id="database"/>
    </p:processor>

    <!-- Check if login/password is valid -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="aggregate('root', #instance, #database)"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0">
                <xsl:template match="/">
                    <xsl:choose>
                        <xsl:when test="/root/form/action != 'login'">
                            <nothing/>
                        </xsl:when>
                        <xsl:when test="/root/Populate/Users/User[@id = /root/form/login]/Password = /root/form/password">
                            <success/>
                        </xsl:when>
                        <xsl:otherwise>
                            <failure/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="result"/>
    </p:processor>

    <p:choose href="#result">
        <!-- Success: redirect to to uri in instance -->
        <p:when test="/success">
            <p:processor name="oxf:session-serializer">
                <p:input name="data"><logged>true</logged></p:input>
            </p:processor>
            <p:processor name="oxf:redirect">
                <p:input name="data" href="aggregate('redirect-url', #instance#xpointer(/form/path-info))"/>
            </p:processor>
            <p:processor name="oxf:identity">
                <p:input name="data"><dummy/></p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>
        <!-- Send result to view -->
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data" href="aggregate('root', #result, #instance)"/>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

</p:config>
