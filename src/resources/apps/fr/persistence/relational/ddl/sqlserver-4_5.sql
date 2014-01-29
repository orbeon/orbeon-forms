create table orbeon_form_definition (
    created            datetime,
    last_modified_time datetime,
    last_modified_by   varchar(255),
    app                varchar(255),
    form               varchar(255),
    form_version       int not null,
    deleted            char(1) not null,
    xml                xml
);

create table orbeon_form_definition_attach (
    created            datetime,
    last_modified_time datetime,
    last_modified_by   varchar(255),
    app                varchar(255),
    form               varchar(255),
    form_version       int not null,
    deleted            char(1) not null,
    file_name          varchar(255),
    file_content       varbinary(max)
);

create table orbeon_form_data (
    created            datetime,
    last_modified_time datetime,
    last_modified_by   varchar(255),
    username           varchar(255),
    groupname          varchar(255),
    app                varchar(255),
    form               varchar(255),
    form_version       int not null,
    document_id        varchar(255),
    draft              char(1) not null,
    deleted            char(1) not null,
    xml                xml
);

create table orbeon_form_data_attach (
    created            datetime,
    last_modified_time datetime,
    last_modified_by   varchar(255),
    username           varchar(255),
    groupname          varchar(255),
    app                varchar(255),
    form               varchar(255),
    form_version       int not null,
    document_id        varchar(255),
    draft              char(1) not null,
    deleted            char(1) not null,
    file_name          varchar(255),
    file_content       varbinary(max)
);
