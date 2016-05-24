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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">

    <p:param name="search" type="input"/>
    <p:param name="search" type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/service/([^/]+)/search/([^/^.]+)/([^/^.]+)</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#matcher-groups"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#search"/>
        <p:input name="matcher-groups" href="#matcher-groups"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>
                <xsl:template match="/search/app | /search/form"/>      <!-- Remove deprecated app/form from input, if present -->
                <xsl:template match="/search">
                    <xsl:variable name="groups" select="doc('input:matcher-groups')/result/group" as="element(group)+"/>
                    <xsl:copy>
                        <provider><xsl:value-of select="$groups[1]"/></provider>
                        <app><xsl:value-of select="$groups[2]"/></app>
                        <form><xsl:value-of select="$groups[3]"/></form>
                        <xsl:apply-templates/>
                    </xsl:copy>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="search"/>
    </p:processor>

</p:config>
