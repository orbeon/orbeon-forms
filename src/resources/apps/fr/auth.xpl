<!--
  Copyright (C) 2015 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:fn="http://www.w3.org/2005/xpath-functions"
          >

    <p:param type="output" name="data"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/parameters/parameter[name = 'source']</include>
                <include>/request/remote-user</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:choose href="#request">
        <p:when test="fn:substring(/request/parameters/parameter[name = 'source']/value,1,1)='/'">
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#request"/>
                <p:input name="config">
                    <xsl:stylesheet version="1.0">
                        <xsl:template match="/">
                            <redirect-url>
                                <path-info><xsl:value-of select="/request/parameters/parameter[name = 'source']/value"/></path-info>
                            </redirect-url>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="redirect" ref="data"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:processor name="oxf:identity">
                <p:input name="data">
                    <redirect-url>
                        <path-info>/fr/</path-info>
                    </redirect-url>
                </p:input>
                <p:output name="data" id="redirect" ref="data"/>
            </p:processor>
        </p:otherwise>
    </p:choose>

    <p:processor xmlns:p="http://www.orbeon.com/oxf/pipeline" name="oxf:redirect">
        <p:input name="data" href="#redirect"/>
    </p:processor>

</p:config>