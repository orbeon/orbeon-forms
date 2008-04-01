<!--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="data" type="output"/>

    <p:processor name="oxf:barcode">
        <p:input name="data">
            <input>foobar</input>
        </p:input>
        <p:input name="barcode">
            <barcode message="/input">
                <code39>
                    <height>10mm</height>
                    <wide-factor>1.5</wide-factor>
                </code39>
            </barcode>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
