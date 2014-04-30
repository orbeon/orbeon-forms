create table orbeon_form_definition (
    created            datetime,
    last_modified_time datetime,
    last_modified_by   nvarchar(255),
    app                nvarchar(255),
    form               nvarchar(255),
    form_version       int not null,
    deleted            char(1) not null,
    xml                xml
);

create table orbeon_form_definition_attach (
    created            datetime,
    last_modified_time datetime,
    last_modified_by   nvarchar(255),
    app                nvarchar(255),
    form               nvarchar(255),
    form_version       int not null,
    deleted            char(1) not null,
    file_name          nvarchar(255),
    file_content       varbinary(max)
);

create table orbeon_form_data (
    id                 int identity(1, 1),
    created            datetime,
    last_modified_time datetime,
    last_modified_by   nvarchar(255),
    username           nvarchar(255),
    groupname          nvarchar(255),
    app                nvarchar(255),
    form               nvarchar(255),
    form_version       int not null,
    document_id        nvarchar(255),
    draft              char(1) not null,
    deleted            char(1) not null,
    xml                xml
);

create table orbeon_form_data_attach (
    created            datetime,
    last_modified_time datetime,
    last_modified_by   nvarchar(255),
    username           nvarchar(255),
    groupname          nvarchar(255),
    app                nvarchar(255),
    form               nvarchar(255),
    form_version       int not null,
    document_id        nvarchar(255),
    draft              char(1) not null,
    deleted            char(1) not null,
    file_name          nvarchar(255),
    file_content       varbinary(max)
);

CREATE FULLTEXT CATALOG orbeon_fulltext_catalog AS DEFAULT;
CREATE UNIQUE INDEX orbeon_from_data_pk ON orbeon_form_data (id);
CREATE FULLTEXT INDEX ON orbeon_form_data (xml) KEY INDEX orbeon_from_data_pk;
