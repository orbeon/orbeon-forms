<!--
    Copyright (C) 2006 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<!--

  This example runs a command-line executable and captures its output.

-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Execute command -->
    <p:processor name="oxf:execute-processor">
        <p:input name="config">
            <exec executable="c:/cygwin/bin/ls.exe" dir="c:/TEMP">
                <arg line="-als"/>
            </exec>
        </p:input>
        <p:output name="stdout" id="stdout"/>
        <p:output name="stderr" id="stderr"/>
        <p:output name="result" id="result"/>
    </p:processor>

    <!-- Output stdout -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config/>
        </p:input>
        <p:input name="data" href="#stdout"/>
    </p:processor>

    <!-- Output stderr -->
    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config/>
        </p:input>
        <p:input name="data" href="#stderr"/>
    </p:processor>

    <!-- Convert result and serialize to XML -->
    <p:processor name="oxf:xml-converter">
        <p:input name="config">
            <config>
                <encoding>utf-8</encoding>
                <indent>true</indent>
                <indent-amount>4</indent-amount>
            </config>
        </p:input>
        <p:input name="data" href="#result"/>
        <p:output name="data" id="converted"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="config">
            <config/>
        </p:input>
        <p:input name="data" href="#converted"/>
    </p:processor>

</p:config>
