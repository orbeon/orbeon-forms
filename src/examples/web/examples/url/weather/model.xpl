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
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="data" type="output"/>

    <!-- Retrieve the HTML page -->
    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>http://weather.yahoo.com/</url>
                <content-type>text/html</content-type>
            </config>
        </p:input>
        <p:output name="data" id="page"/>
    </p:processor>

    <!-- The XQuery version -->
    <p:processor name="oxf:xquery">
        <p:input name="config">
            <xquery xmlns:xhtml="http://www.w3.org/1999/xhtml">
                <html>
                    <body>
                        <table>
                        {
                          for $d in //td[contains(a/small/text(), "New York, NY")]
                          return for $row in $d/parent::tr/parent::table/tr
                          where contains($d/a/small/text()[1], "New York")
                          return <tr><td>{data($row/td[1])}</td>
                                   <td>{data($row/td[2])}</td>
                                   <td>{$row/td[3]//img}</td></tr>
                        }
                        </table>
                    </body>
                </html>
            </xquery>
        </p:input>
        <p:input name="data" href="#page"/>
        <p:output name="data" ref="data"/>
    </p:processor>

    <!-- Below, the same result obtained using XSLT -->
    <!--
    <p:processor name="oxf:xslt">
        <p:input name="config">
            <html xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xsl:version="2.0">
                <body>
                    <table>
                        <xsl:for-each select="//td[contains(a/small/text(), 'New York, NY')]">
                            <xsl:variable name="d" select="."/>
                            <xsl:if test="contains($d/a/small/text()[1], 'New York')">
                                <xsl:for-each select="$d/parent::tr/parent::table/tr">
                                    <xsl:variable name="row" select="."/>
                                    <tr>
                                        <td><xsl:value-of select="$row/td[1]"/></td>
                                        <td><xsl:value-of select="$row/td[2]"/></td>
                                        <td><xsl:copy-of select="$row/td[3]//img"/></td>
                                    </tr>
                                </xsl:for-each>
                            </xsl:if>
                        </xsl:for-each>
                    </table>
                </body>
            </html>
        </p:input>
        <p:input name="data" href="#page"/>
        <p:output name="data" ref="data"/>
    </p:processor>
    -->

</p:config>
