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

    <!-- Display message -->
    <p:processor name="oxf:null-serializer">
        <p:input name="data" debug="message">
            <message>Running task...</message>
        </p:input>
    </p:processor>

    <!-- Extract document -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>scheduler-test-data</key>
                <scope>application</scope>
            </config>
        </p:input>
        <p:output name="data" id="context"/>
    </p:processor>

    <!-- Display document -->
    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#context" debug="scheduler-test"/>
    </p:processor>

    <!-- Display message -->
    <p:processor name="oxf:null-serializer">
        <p:input name="data" debug="message">
            <message>Task done.</message>
        </p:input>
    </p:processor>

</p:config>
