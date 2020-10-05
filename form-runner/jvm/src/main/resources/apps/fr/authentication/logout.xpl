<!--
  Copyright (C) 2018 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:session-invalidator"/>

    <p:processor name="oxf:redirect">
        <p:input name="data" transform="oxf:unsafe-xslt" href="aggregate('dummy')">
            <redirect-url xsl:version="2.0">
                <path-info>
                    <xsl:text>/fr/logout-done?source=</xsl:text>
                    <xsl:value-of select="p:get-request-parameter('source')"/>
                </path-info>
            </redirect-url>
        </p:input>
    </p:processor>

</p:config>
