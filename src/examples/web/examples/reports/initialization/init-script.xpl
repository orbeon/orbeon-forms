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
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:sql="http://orbeon.org/oxf/xml/sql"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:sql">
        <p:input name="data" href="/examples/petstore/database/database.xml"/>
        <p:input name="datasource" href="../../datasource-sql.xml"/>
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <sql:execute><sql:update>drop table oxf_item_details if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table oxf_item if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table oxf_product_details if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table oxf_product if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table oxf_category_details if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table oxf_category if exists</sql:update></sql:execute>

                    <sql:execute>
                        <sql:update>create table oxf_category (catid char(10) not null,
                                           constraint pk_category primary key (catid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>
                            create table oxf_category_details
                            (catid char(10) not null,
                             name varchar(80) not null, image varchar(255) null,
                             descn varchar(255) null, locale char(10) not null,
                             constraint pk_category_details primary key (catid, locale),
                             constraint fk_category_details_1 foreign key (catid)
                             references oxf_category (catid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>create table oxf_product (productid varchar(10) not null,
                                                          catid char(10) not null,
                                          constraint pk_product primary key (productid),
                                          constraint fk_product_1 foreign key (catid)
                                          references oxf_category (catid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>create table oxf_product_details (
                                               productid varchar(10) not null,
                                               locale char(10) not null,
                                               cname varchar(80) not null,
                                               image varchar(255) null,
                                               descn varchar(255) null,
                                          constraint pk_product_details primary key (productid, locale),
                                          constraint fk_product_details_1 foreign key (productid)
                                          references oxf_product (productid))</sql:update>
                    </sql:execute>
                    <sql:execute>
                        <sql:update>create table oxf_item (
                                               itemid char(10) not null,
                                               productid varchar(10) not null,
                                          constraint pk_item primary key (itemid),
                                          constraint fk_item_1 foreign key (productid)
                                          references oxf_product (productid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>create table oxf_item_details (
                                               itemid char(10) not null,
                                               listprice decimal(10,2) not null,
                                               unitcost decimal(10,2) not null,
                                               locale char(10) not null,
                                               image char(255) not null,
                                               descn varchar(255) not null,
                                               attr1 varchar(80) null,
                                               attr2 varchar(80) null,
                                               attr3 varchar(80) null,
                                               attr4 varchar(80) null,
                                               attr5 varchar(80) null,
                                               constraint pk_item_details primary key (itemid, locale),
                                            constraint fk_item_details_1 foreign key (itemid)
                                            references oxf_item (itemid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Categories/Category">
                            insert into oxf_category (catid) values (
                            <sql:parameter type="xs:string" select="@id"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Categories/Category/CategoryDetails">
                            insert into oxf_category_details  values (
                            <sql:parameter type="xs:string" select="../@id"/>,
                            <sql:parameter type="xs:string" select="Name"/>,
                            <sql:parameter type="xs:string" select="Image"/>,
                            <sql:parameter type="xs:string" select="Name"/>,
                            <sql:parameter type="xs:string" select="@locale"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Products/Product">
                            insert into oxf_product  values (
                            <sql:parameter type="xs:string" select="@id"/>,
                            <sql:parameter type="xs:string" select="@category"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Products/Product/ProductDetails">
                            insert into oxf_product_details  values (
                            <sql:parameter type="xs:string" select="../@id"/>,
                            <sql:parameter type="xs:string" select="@locale"/>,
                            <sql:parameter type="xs:string" select="Name"/>,
                            <sql:parameter type="xs:string" select="Image"/>,
                            <sql:parameter type="xs:string" select="Description"/>
                            )
                        </sql:update>
                    </sql:execute>


                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Items/Item">
                            insert into oxf_item  values (
                            <sql:parameter type="xs:string" select="@id"/>,
                            <sql:parameter type="xs:string" select="@product"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Items/Item/ItemDetails">
                            insert into oxf_item_details  values (
                            <sql:parameter type="xs:string" select="../@id"/>,
                            convert(<sql:parameter type="xs:string" select="ListPrice"/>,decimal),
                            convert(<sql:parameter type="xs:string" select="UnitCost"/>,decimal),
                            <sql:parameter type="xs:string" select="@locale"/>,
                            <sql:parameter type="xs:string" select="Image"/>,
                            <sql:parameter type="xs:string" select="Description"/>,
                            <sql:parameter type="xs:string" select="Attribute[1]"/>,
                            <sql:parameter type="xs:string" select="Attribute[2]"/>,
                            <sql:parameter type="xs:string" select="Attribute[3]"/>,
                            <sql:parameter type="xs:string" select="Attribute[4]"/>,
                            <sql:parameter type="xs:string" select="Attribute[5]"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <dummy/>

                </sql:connection>
            </sql:config>
        </p:input>
        <p:output name="output" id="dummy"/>
    </p:processor>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#dummy"/>
    </p:processor>

</p:config>
