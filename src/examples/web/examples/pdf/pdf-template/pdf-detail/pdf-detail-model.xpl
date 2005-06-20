<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param name="instance" type="input"/>

    <p:processor name="oxf:pdf-template">
        <p:input name="instance" href="#instance#xpointer(/*/document/*)"/>
        <p:input name="model">
            <config xmlns:claim="http://orbeon.org/oxf/examples/bizdoc/claim">
                <template href="oxf:/examples/pdf/pdf-template/schema/dmv14.pdf"/>
                <field left-position="108.5" top-position="138" spacing="15.9" font-family="Courier" font-size="14" size="20" ref="/claim:form/claim:name/claim:last-name"/>
                <field left-position="108.5" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="10" ref="/claim:form/claim:name/claim:first-name"/>
                <field left-position="287.5" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="1" ref="/claim:form/claim:name/claim:initial"/>

                <field left-position="334" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="2" ref="/claim:form/claim:birth-date/claim:month"/>
                <field left-position="380.5" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="2" ref="/claim:form/claim:birth-date/claim:day"/>
                <field left-position="428.5" top-position="171" spacing="15.9" font-family="Courier" font-size="14" size="4" ref="/claim:form/claim:birth-date/claim:year"/>

                <field left-position="456" top-position="138" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/claim:form/claim:driver-license-no"/>

                <field left-position="108.5" top-position="255" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/claim:form/claim:residence-address/claim:street/claim:number"/>
                <field left-position="204.5" top-position="255" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/claim:form/claim:residence-address/claim:street/claim:name-1"/>
                <field left-position="108.5" top-position="288" spacing="15.9" font-family="Courier" font-size="14" size="4" ref="/claim:form/claim:residence-address/claim:apt"/>
                <field left-position="108.5" top-position="318" spacing="15.9" font-family="Courier" font-size="14" size="22" ref="/claim:form/claim:residence-address/claim:city"/>
                <field left-position="471" top-position="318" spacing="15.9" font-family="Courier" font-size="14" size="2" ref="/claim:form/claim:residence-address/claim:state"/>
                <field left-position="519" top-position="318" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/claim:form/claim:residence-address/claim:zip"/>

                <field left-position="108.5" top-position="354" spacing="15.9" font-family="Courier" font-size="14" size="5" ref="/claim:form/claim:mailing-address/claim:street/claim:number"/>
                <field left-position="204.5" top-position="354" spacing="15.9" font-family="Courier" font-size="14" size="21" ref="/claim:form/claim:mailing-address/claim:street/claim:name-1"/>
                <field left-position="108.5" top-position="387" spacing="15.9" font-family="Courier" font-size="14" size="4" ref="/claim:form/claim:mailing-address/claim:apt"/>
                <field left-position="108.5" top-position="417" spacing="15.9" font-family="Courier" font-size="14" size="22" ref="/claim:form/claim:mailing-address/claim:city"/>
                <field left-position="471" top-position="417" spacing="15.9" font-family="Courier" font-size="14" size="2" ref="/claim:form/claim:mailing-address/claim:state"/>
                <field left-position="519" top-position="417" spacing="15.9" font-family="Courier" font-size="14" size="4" ref="/claim:form/claim:mailing-address/claim:zip"/>

                <field left-position="108.5" top-position="465" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/claim:form/claim:vehicles/claim:vehicle[1]/claim:plate-no"/>
                <field left-position="255" top-position="465" spacing="15.9" font-family="Courier" font-size="14" size="17" ref="/claim:form/claim:vehicles/claim:vehicle[1]/claim:vin"/>
                <field left-position="108.5" top-position="489.5" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/claim:form/claim:vehicles/claim:vehicle[2]/claim:plate-no"/>
                <field left-position="255" top-position="489.5" spacing="15.9" font-family="Courier" font-size="14" size="17" ref="/claim:form/claim:vehicles/claim:vehicle[2]/claim:vin"/>
                <field left-position="108.5" top-position="514" spacing="15.9" font-family="Courier" font-size="14" size="8" ref="/claim:form/claim:vehicles/claim:vehicle[3]/claim:plate-no"/>
                <field left-position="255" top-position="514" spacing="15.9" font-family="Courier" font-size="14" size="17" ref="/claim:form/claim:vehicles/claim:vehicle[3]/claim:vin"/>

                <field left-position="108.5" top-position="547" spacing="15.9" font-family="Courier" font-size="14" size="22" ref="/claim:form/claim:lease/claim:company-name"/>
            </config>
        </p:input>
        <p:input name="config">
            <config/>
        </p:input>
        <p:output name="data" id="out"/>
    </p:processor>

    <p:processor name="oxf:http-serializer">
        <p:input name="data" href="#out"/>
        <p:input name="config">
            <config>
                <header>
                   <name>Content-Disposition</name>
                    <value>attachment; filename=document.pdf</value>
                </header>
            </config>
        </p:input>
    </p:processor>

</p:config>
