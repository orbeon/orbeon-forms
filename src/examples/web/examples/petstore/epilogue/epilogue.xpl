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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="data"/>
	<p:param type="output" name="data"/>

    <p:processor name="oxf:session-generator">
        <p:input name="config"><key>target</key></p:input>
        <p:output name="data" id="target"/>
    </p:processor>

    <p:choose href="#target">
        <p:when test="/target = 'pda'">

            <!-- Simple layout for PDAs -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="#data"/>
                <p:input name="config" href="layout-pda.xsl"/>
                <p:output name="data" id="beautified-page"/>
            </p:processor>

        </p:when>
        <p:otherwise>

            <p:processor name="oxf:pipeline">
                <p:input name="config" href="banner.xpl"/>
                <p:output name="data" id="banner"/>
            </p:processor>

            <p:processor name="oxf:pipeline">
                <p:input name="config" href="sidebar.xpl"/>
                <p:output name="data" id="sidebar"/>
            </p:processor>

            <!-- Aggregate sidebar, body, etc to create the complete page -->
            <p:processor name="oxf:xslt">
                <p:input name="data" href="aggregate('root',
                        aggregate('banner', #banner),
                        aggregate('sidebar', #sidebar),
                        #data#xpointer(/root/title),
                        #data#xpointer(/root/body),
                        aggregate('footer', footer.xml))"/>
                <p:input name="config" href="template.xsl"/>
                <p:output name="data" id="aggregate-page"/>
            </p:processor>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="#aggregate-page"/>
                <p:input name="config" href="layout-web.xsl"/>
                <p:output name="data" id="beautified-page"/>
            </p:processor>

        </p:otherwise>
    </p:choose>

    <p:processor name="oxf:session-generator">
        <p:input name="config"><key>locale</key></p:input>
        <p:output name="data" id="locale"/>
    </p:processor>

    <!-- Internationalize -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="aggregate
                ('root', #locale, aggregate('page', #beautified-page), i18n-en_US.xml, i18n-ja_JP.xml)"/>
        <p:input name="config" href="i18n.xsl"/>
        <p:output name="data" id="themed-data"/>
    </p:processor>

    <!-- Rewrite all URLs in HTML and XHTML documents -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#themed-data"/>
        <p:input name="config" href="oxf:/ops/pfc/url--rewrite.xsl"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
