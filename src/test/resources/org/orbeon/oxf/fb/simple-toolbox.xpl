<!--
  Copyright (C) 2013 Orbeon, Inc.

  This program is free software; you can redistribute it and/or modify it under the terms of the
  GNU Lesser General Public License as published by the Free Software Foundation; either version
  2.1 of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  -->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="output" name="data"/>

    <p:processor name="oxf:unsafe-xslt">
        <p:input name="config" href="oxf:/forms/orbeon/builder/service/toolbox.xsl"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="global-template-xbl"><dummy/></p:input>
        <p:input name="custom-template-xbl"><dummy/></p:input>
        <p:input name="request">
            <request>
                <parameters>
                    <parameter>
                        <name>application</name>
                        <value>orbeon</value>
                    </parameter>
                    <parameter>
                        <name>form</name>
                        <value>tests</value>
                    </parameter>
                </parameters>
            </request>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
