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
          xmlns:f="http://orbeon.org/oxf/xml/formatting" xmlns:xhtml="http://www.w3.org/1999/xhtml"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xforms="http://www.w3.org/2002/xforms"
          xmlns:sql="http://orbeon.org/oxf/xml/sql"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

	<p:processor name="oxf:pipeline">
		<p:input name="config" href="olap.xpl"/>
	</p:processor>

    <p:processor name="oxf:sql">
        <p:input name="data" href="/examples/petstore/database/database.xml"/>
        <p:input name="config">
            <sql:config>
                <sql:connection>
                    <sql:datasource>db</sql:datasource>
                    
                    <sql:execute><sql:update>drop table friends if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table item_details if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table item if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table product_details if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table product if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table category_details if exists</sql:update></sql:execute>
                    <sql:execute><sql:update>drop table category if exists</sql:update></sql:execute>

                    <sql:execute>
                        <sql:update>create table friends (id integer not null identity primary key,
                                            first varchar(255) not null,
                                            last varchar(255) not null,
                                            phone varchar(255) not null)</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>create table category (catid char(10) not null,
                                           constraint pk_category primary key (catid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>
                            create table category_details
                            (catid char(10) not null,
                             name varchar(80) not null, image varchar(255) null,
                             descn varchar(255) null, locale char(10) not null,
                             constraint pk_category_details primary key (catid, locale),
                             constraint fk_category_details_1 foreign key (catid)
                             references category (catid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>create table product (productid varchar(10) not null,
                                                          catid char(10) not null,
                                          constraint pk_product primary key (productid),
                                          constraint fk_product_1 foreign key (catid)
                                          references category (catid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>create table product_details (
                                               productid varchar(10) not null,
                                               locale char(10) not null,
                                               cname varchar(80) not null,
                                               image varchar(255) null,
                                               descn varchar(255) null,
                                          constraint pk_product_details primary key (productid, locale),
                                          constraint fk_product_details_1 foreign key (productid)
                                          references product (productid))</sql:update>
                    </sql:execute>
                    <sql:execute>
                        <sql:update>create table item (
                                               itemid char(10) not null,
                                               productid varchar(10) not null,
                                          constraint pk_item primary key (itemid),
                                          constraint fk_item_1 foreign key (productid)
                                          references product (productid))</sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update>create table item_details (
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
                                            references item (itemid))</sql:update>
                    </sql:execute>

                    <sql:execute><sql:update>insert into friends values (null, 'John', 'Smith', '555-123-4567')</sql:update></sql:execute>
                    <sql:execute><sql:update>insert into friends values (null, 'Tom', 'Washington', '555-123-4567')</sql:update></sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Categories/Category">
                            insert into category (catid) values (
                            <sql:parameter type="xs:string" select="@id"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Categories/Category/CategoryDetails">
                            insert into category_details  values (
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
                            insert into product  values (
                            <sql:parameter type="xs:string" select="@id"/>,
                            <sql:parameter type="xs:string" select="@category"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Products/Product/ProductDetails">
                            insert into product_details  values (
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
                            insert into item  values (
                            <sql:parameter type="xs:string" select="@id"/>,
                            <sql:parameter type="xs:string" select="@product"/>
                            )
                        </sql:update>
                    </sql:execute>

                    <sql:execute>
                        <sql:update select="/Populate/Catalog/Items/Item/ItemDetails">
                            insert into item_details  values (
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
