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
            <message>Context initialized.</message>
        </p:input>
    </p:processor>

    <!-- Store data in the application context -->
    <p:processor name="oxf:scope-serializer">
        <p:input name="config">
            <config>
                <key>scheduler-test-data</key>
                <scope>application</scope>
            </config>
        </p:input>
        <p:input name="data">
            <document>
                <body>
                    This is a document stored into the application context.
                </body>
            </document>
        </p:input>
    </p:processor>

    <!-- Schedule a task -->
    <p:processor name="oxf:scheduler">
        <p:input name="config">
            <config>
                <start-task>
                    <name>Task Scheduled From Context Initialization Pipeline</name>
                    <start-time>now</start-time>
                    <interval>3600000</interval>
                    <synchronized>true</synchronized>
                    <processor-name>oxf:pipeline</processor-name>
                    <input name="config" url="oxf:/apps/context/scheduled-task.xpl"/>
                </start-task>
            </config>
        </p:input>
    </p:processor>

</p:config>
