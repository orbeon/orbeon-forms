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

    <p:param type="output" name="data"/>

    <p:processor name="oxf:url-generator">
        <p:input name="config">
            <config>
                <url>parsing-view.xsl</url>
                <content-type>binary/octet-stream</content-type>
                <force-content-type>true</force-content-type>
            </config>
        </p:input>
        <p:output name="data" id="xml-file-as-binary"/>
    </p:processor>

    <p:processor name="oxf:to-xml-converter">
        <p:input name="data" href="#xml-file-as-binary"/>
        <p:input name="config">
            <config/>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
