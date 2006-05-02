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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline" xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:image-server" xmlns:p="http://www.orbeon.com/oxf/pipeline">
        <p:input name="image" debug="image">
            <image>
                <path>ca-coast.jpg</path>
                <!--<url>http://static.flickr.com/39/111563124_98f6c52a62_o.jpg</url>-->
                <transform type="scale">
                    <scale-up>false</scale-up>
                    <max-width>800</max-width>
                </transform>
            </image>
        </p:input>
        <p:input name="config" debug="gaga">
            <config>
                <image-directory>oxf:/examples/imageserver/</image-directory>
                <default-quality>0.7</default-quality>
            </config>
        </p:input>
    </p:processor>

</p:config>
