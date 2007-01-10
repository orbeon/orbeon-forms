<!--
    Copyright (C) 2007 Orbeon, Inc.

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

    <p:processor name="oxf:java">
        <!-- Define the class that implements the custom processor -->
        <p:input name="config">
            <config sourcepath="." class="CallXSLT"/>
        </p:input>
        <!-- Pass a data input that the custom processor will read -->
        <p:input name="myinput" href="input.xml"/>
        <!-- Return out the data output returned by the custom processor -->
        <p:output name="myoutput" ref="data"/>
    </p:processor>

</p:config>
