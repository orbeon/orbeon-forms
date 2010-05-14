<!--
  Copyright (C) 2010 Orbeon, Inc.

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
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#instance" schema-href="license-request.rng"/>
        <p:input name="config">
            <license xsl:version="2.0" xsl:exclude-result-prefixes="p oxf xsl xs">
                <licensor>Orbeon, Inc.</licensor>
                <licensee><xsl:value-of select="normalize-space(/*/licensee)"/></licensee>
                <organization><xsl:value-of select="normalize-space(/*/organization)"/></organization>
                <email><xsl:value-of select="normalize-space(/*/email)"/></email>
                <issued><xsl:value-of select="adjust-date-to-timezone(current-date(), ())"/></issued>
                <version><xsl:value-of select="normalize-space(/*/version)"/></version>
                <expiration>
                    <xsl:choose>
                        <xsl:when test="/*/type = '30day'">
                            <xsl:value-of select="adjust-date-to-timezone(current-date() + xs:dayTimeDuration('P30D'), ())"/>
                        </xsl:when>
                        <xsl:when test="/*/type = '90day'">
                            <xsl:value-of select="adjust-date-to-timezone(current-date() + xs:dayTimeDuration('P90D'), ())"/>
                        </xsl:when>
                        <xsl:when test="/*/type = '1year'">
                            <xsl:value-of select="adjust-date-to-timezone(current-date() + xs:yearMonthDuration('P1Y'), ())"/>
                        </xsl:when>
                        <xsl:when test="/*/type = 'perpetual'"/>
                        <xsl:otherwise>
                            <!-- Shouldn't happen -->
                            <xsl:value-of select="adjust-date-to-timezone(current-date(), ())"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </expiration>
            </license>
        </p:input>
        <p:output name="data" id="unsigned-license"/>
    </p:processor>

    <p:processor name="oxf:signature">
        <p:input name="data" href="#unsigned-license"/>
        <p:input name="private-key" href="orbeon-private.xml"/>
        <p:output name="data" ref="data" schema-href="license-response.rng"/>
    </p:processor>

</p:config>
