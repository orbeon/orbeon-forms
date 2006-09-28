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
                <template href="oxf:/examples/forms/dmv14.pdf"/><!-- show-grid="true" -->
                <field left-position="108.5" top-position="138" spacing="15.9" font-family="Courier" font-size="14" size="20" ref="/dmv:form/dmv:personal-information/dmv:name/dmv:last-name"/>
                <field left-position="108.5" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="10" ref="/dmv:form/dmv:personal-information/dmv:name/dmv:first-name"/>
                <field left-position="287.5" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="1" ref="/dmv:form/dmv:personal-information/dmv:name/dmv:initial"/>

                <field left-position="334" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="10"
                       ref="concat(substring(/dmv:form/dmv:personal-information/dmv:birth-date, 6, 2), ' ',
                                   substring(/dmv:form/dmv:personal-information/dmv:birth-date, 9, 2), ' ',
                                   substring(/dmv:form/dmv:personal-information/dmv:birth-date, 1, 4))"/>

                <field left-position="456" top-position="138" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/dmv:form/dmv:personal-information/dmv:driver-license-number"/>

                <field left-position="108.3" top-position="223" spacing="15.9" font-family="Courier" font-size="14" size="1" ref="/dmv:form/dmv:address-information/dmv:voter-address-change/dmv:change-address"/>

                <field left-position="108.5" top-position="255" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'residence']/dmv:street/dmv:number"/>
                <field left-position="204.5" top-position="255" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'residence']/dmv:street/dmv:name-1"/>
                <field left-position="204.5" top-position="279" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'residence']/dmv:street/dmv:name-2"/>
                <field left-position="108.5" top-position="288" spacing="15.9" font-family="Courier" font-size="14" size="4" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'residence']/dmv:apt"/>
                <field left-position="108.5" top-position="318" spacing="15.9" font-family="Courier" font-size="14" size="22" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'residence']/dmv:city"/>
                <field left-position="471" top-position="318" spacing="15.9" font-family="Courier" font-size="14" size="2" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'residence']/dmv:state"/>
                <field left-position="519" top-position="318" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'residence']/dmv:zip"/>

                <field left-position="108.5" top-position="354" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'mailing']/dmv:street/dmv:number"/>
                <field left-position="204.5" top-position="354" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'mailing']/dmv:street/dmv:name-1"/>
                <field left-position="204.5" top-position="378" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'mailing']/dmv:street/dmv:name-2"/>
                <field left-position="108.5" top-position="387" spacing="15.9" font-family="Courier" font-size="14" size="4" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'mailing']/dmv:apt"/>
                <field left-position="108.5" top-position="417" spacing="15.9" font-family="Courier" font-size="14" size="22" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'mailing']/dmv:city"/>
                <field left-position="471" top-position="417" spacing="15.9" font-family="Courier" font-size="14" size="2" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'mailing']/dmv:state"/>
                <field left-position="519" top-position="417" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/dmv:form/dmv:address-information/dmv:address[@type = 'mailing']/dmv:zip"/>

                <field left-position="108.5" top-position="465" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[1]/dmv:plate-number"/>
                <field left-position="255" top-position="465" spacing="15.9" font-family="Courier" font-size="14" size="17" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[1]/dmv:vin"/>
                <field left-position="549" top-position="465" spacing="15.9" font-family="Courier" font-size="14" size="1" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[1]/dmv:leased"/>
                <field left-position="108.5" top-position="489" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[2]/dmv:plate-number"/>
                <field left-position="255" top-position="489" spacing="15.9" font-family="Courier" font-size="14" size="17" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[2]/dmv:vin"/>
                <field left-position="549" top-position="489" spacing="15.9" font-family="Courier" font-size="14" size="1" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[2]/dmv:leased"/>
                <field left-position="108.5" top-position="512.5" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[3]/dmv:plate-number"/>
                <field left-position="255" top-position="512.5" spacing="15.9" font-family="Courier" font-size="14" size="17" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[3]/dmv:vin"/>
                <field left-position="549" top-position="512.5" spacing="15.9" font-family="Courier" font-size="14" size="1" ref="/dmv:form/dmv:vehicle-information/dmv:vehicles/dmv:vehicle[3]/dmv:leased"/>

                <field left-position="108.5" top-position="547" spacing="15.9" font-family="Courier" font-size="14" size="22" ref="/dmv:form/dmv:vehicle-information/dmv:leased-vehicles/dmv:company-name"/>

                <field left-position="108.5" top-position="582" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/dmv:form/dmv:vehicle-information/dmv:vehicle-address/dmv:street/dmv:number"/>
                <field left-position="204.5" top-position="582" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/dmv:form/dmv:vehicle-information/dmv:vehicle-address/dmv:street/dmv:name-1"/>
                <field left-position="204.5" top-position="606" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/dmv:form/dmv:vehicle-information/dmv:vehicle-address/dmv:street/dmv:name-2"/>
                <field left-position="108.5" top-position="638" spacing="15.9" font-family="Courier" font-size="14" size="16" ref="/dmv:form/dmv:vehicle-information/dmv:vehicle-address/dmv:city"/>
                <field left-position="375" top-position="638" spacing="15.9" font-family="Courier" font-size="14" size="14" ref="/dmv:form/dmv:vehicle-information/dmv:vehicle-address/dmv:county"/>

                <field left-position="108.5" top-position="670" spacing="7" font-family="Courier" font-size="10" size="49"
                       ref="concat(/dmv:form/dmv:address-information/dmv:address[@type = 'old']/dmv:street/dmv:number, ' ',
                                   /dmv:form/dmv:address-information/dmv:address[@type = 'old']/dmv:street/dmv:name-1, ' ',
                                   /dmv:form/dmv:address-information/dmv:address[@type = 'old']/dmv:street/dmv:name-2)"/>

                <field left-position="334" top-position="670" spacing="7" font-family="Courier" font-size="10" size="27"
                       ref="/dmv:form/dmv:address-information/dmv:address[@type = 'old']/dmv:city"/>

                <field left-position="466" top-position="670" spacing="7" font-family="Courier" font-size="10" size="27"
                       ref="/dmv:form/dmv:address-information/dmv:address[@type = 'old']/dmv:state"/>

                <field left-position="530" top-position="670" spacing="7" font-family="Courier" font-size="10" size="27"
                       ref="/dmv:form/dmv:address-information/dmv:address[@type = 'old']/dmv:zip"/>

                <field left-position="435" top-position="750.2" spacing="15.9" font-family="Courier" font-size="14" size="10"
                       ref="concat(substring(/document-info/document-date, 6, 2), ' ',
                                   substring(/document-info/document-date, 9, 2), ' ',
                                   substring(/document-info/document-date, 1, 4))"/>
            </config>
        </p:input>
        <p:input name="config">
            <config/>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
