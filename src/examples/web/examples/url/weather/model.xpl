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

    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>http://weather.yahoo.com/</url>
                <content-type>text/html</content-type>
            </config>
        </p:input>
        <p:output name="data" id="page" debug="page"/>
    </p:processor>

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
        <p:output name="data" id="html-page"/>
    </p:processor>

    <p:processor name="oxf:html-serializer">
        <p:input name="config">
            <config/>
        </p:input>
        <p:input name="data" href="#html-page"/>
    </p:processor>
</p:config>
