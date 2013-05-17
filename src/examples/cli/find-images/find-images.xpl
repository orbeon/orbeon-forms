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
<!--

  This example finds all the images in the OPS examples and outputs metadata about them.

-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Find all examples descriptors -->
    <p:processor name="oxf:directory-scanner">
        <p:input name="config">
            <config>
                <base-directory>../../web/doc</base-directory>
                <include>**/*.jpg</include>
                <include>**/*.jpeg</include>
                <include>**/*.gif</include>
                <include>**/*.png</include>
                <case-sensitive>false</case-sensitive>
                <image-metadata>
                    <basic-info>true</basic-info>
                </image-metadata>
            </config>
        </p:input>
        <p:output name="data" id="directory-scan"/>
    </p:processor>

    <p:processor name="oxf:xml-serializer">
        <p:input name="data" href="#directory-scan"/>
        <p:input name="config">
            <config>
                <indent-amount>4</indent-amount>
            </config>
        </p:input>
    </p:processor>

</p:config>
