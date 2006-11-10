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
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <!-- Create REST submission -->
    <p:processor name="oxf:xslt">
        <p:input name="config">
            <xforms:submission xmlns:xforms="http://www.w3.org/2002/xforms" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                               xsl:version="2.0" method="get" action="/exist/rest/db/ops/dmv-example/{/*/document-id}"/>
        </p:input>
        <p:input name="data" href="#instance"/>
        <p:output name="data" id="submission"/>
    </p:processor>

    <!-- Execute REST submission -->
    <p:processor name="oxf:xforms-submission">
        <p:input name="submission" href="#submission"/>
        <p:input name="request"><dummy/></p:input>
        <p:output name="response" id="document"/>
    </p:processor>

    <p:processor name="oxf:pdf-template">
        <p:input name="instance" href="#document"/>
        <p:input name="model">
            <config xmlns:dmv="http://orbeon.org/oxf/examples/dmv">
                <template href="oxf:/apps/forms/dmv14.pdf" show-grid="false"/>

                <group ref="/*" font-pitch="15.9" font-family="Courier" font-size="14">
                    <group ref="dmv:personal-information">
                        <group ref="dmv:name">
                            <field left="108.5" top="138" size="20" value="dmv:last-name"/>
                            <field left="108.5" top="171" size="10" value="dmv:first-name"/>
                            <field left="287.5" top="171" size="1" value="dmv:initial"/>
                        </group>

                        <field left="334" top="171" size="10"
                               value="concat(substring(dmv:birth-date, 6, 2), ' ',
                                           substring(dmv:birth-date, 9, 2), ' ',
                                           substring(dmv:birth-date, 1, 4))"/>

                        <field left="456" top="138" size="8" value="dmv:driver-license-number"/>
                    </group>

                    <group ref="dmv:address-information">
                        <field left="108.3" top="223" size="1" value="dmv:voter-address-change/dmv:change-address"/>
                        <group ref="dmv:address[@type = 'residence']">
                            <field left="108.5" top="255" size="5" value="dmv:street/dmv:number"/>
                            <field left="204.5" top="255" size="21" value="dmv:street/dmv:name-1"/>
                            <field left="204.5" top="279" size="21" value="dmv:street/dmv:name-2"/>
                            <field left="108.5" top="288" size="4" value="dmv:apt"/>
                            <field left="108.5" top="318" size="22" value="dmv:city"/>
                            <field left="471" top="318" size="2" value="dmv:state"/>
                            <field left="519" top="318" size="5" value="dmv:zip"/>
                        </group>
                        <group ref="dmv:address[@type = 'mailing']">
                            <field left="108.5" top="354" size="5" value="dmv:street/dmv:number"/>
                            <field left="204.5" top="354" size="21" value="dmv:street/dmv:name-1"/>
                            <field left="204.5" top="378" size="21" value="dmv:street/dmv:name-2"/>
                            <field left="108.5" top="387" size="4" value="dmv:apt"/>
                            <field left="108.5" top="417" size="22" value="dmv:city"/>
                            <field left="471" top="417" size="2" value="dmv:state"/>
                            <field left="519" top="417" size="5" value="dmv:zip"/>
                        </group>
                    </group>

                    <group ref="dmv:vehicle-information">
                        <repeat nodeset="dmv:vehicles/dmv:vehicle" offset-y="24">
                            <field left="108.5" top="465" size="8" value="dmv:plate-number"/>
                            <field left="255" top="465" size="17" value="dmv:vin"/>
                            <field left="549" top="465" size="1" value="dmv:leased"/>
                        </repeat>
                        <field left="108.5" top="547" size="22" value="dmv:leased-vehicles/dmv:company-name"/>
                        <group ref="dmv:vehicle-address">
                            <field left="108.5" top="582" size="5" value="dmv:street/dmv:number"/>
                            <field left="204.5" top="582" size="21" value="dmv:street/dmv:name-1"/>
                            <field left="204.5" top="606" size="21" value="dmv:street/dmv:name-2"/>
                            <field left="108.5" top="638" size="16" value="dmv:city"/>
                            <field left="375" top="638" size="14" value="dmv:county"/>
                        </group>
                    </group>

                    <group ref="dmv:address-information" font-size="10" font-pitch="7">
                        <field left="108.5" top="670" size="49"
                               value="concat(dmv:address[@type = 'old']/dmv:street/dmv:number, ' ',
                                           dmv:address[@type = 'old']/dmv:street/dmv:name-1, ' ',
                                           dmv:address[@type = 'old']/dmv:street/dmv:name-2)"/>

                        <field left="334" top="670" size="27"
                               value="dmv:address[@type = 'old']/dmv:city"/>

                        <field left="466" top="670" size="27"
                               value="dmv:address[@type = 'old']/dmv:state"/>

                        <field left="530" top="670" size="27"
                               value="dmv:address[@type = 'old']/dmv:zip"/>
                    </group>

                    <field left="435" top="750.2" size="10"
                           value="concat(substring(/document-info/document-date, 6, 2), ' ',
                                       substring(/document-info/document-date, 9, 2), ' ',
                                       substring(/document-info/document-date, 1, 4))"/>
                </group>
            </config>
        </p:input>
        <p:input name="config">
            <config/>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
