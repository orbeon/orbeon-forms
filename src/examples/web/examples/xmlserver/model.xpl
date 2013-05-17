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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:delegation="http://orbeon.org/oxf/xml/delegation">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:delegation">
        <p:input name="interface">
            <config>
                <service id="get-quote" type="bus" name="get-quote"/>
            </config>
        </p:input>
        <p:input name="call">
            <result>
                <delegation:execute service="get-quote">
                    <a/>
                    <b/>
                </delegation:execute>
            </result>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
