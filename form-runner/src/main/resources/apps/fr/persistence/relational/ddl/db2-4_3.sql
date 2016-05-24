create table orbeon_form_definition (
    created             timestamp,
    last_modified_time  timestamp,
    last_modified_by    varchar(255),
    app                 varchar(255),
    form                varchar(255),
    deleted             character(1) not null,
    xml                 xml
);

create table orbeon_form_definition_attach (
    created             timestamp,
    last_modified_time  timestamp,
    last_modified_by    varchar(255),
    app                 varchar(255),
    form                varchar(255),
    deleted             character(1) not null,
    file_name           varchar(255),
    file_content        blob(1048576)
);

create table orbeon_form_data (
    created             timestamp,
    last_modified_time  timestamp,
    last_modified_by    varchar(255),
    username            varchar(255),
    groupname           varchar(255),
    app                 varchar(255),
    form                varchar(255),
    document_id         varchar(255),
    deleted             character(1) not null,
    draft               character(1) not null,
    xml                 xml
);

create table orbeon_form_data_attach (
    created             timestamp,
    last_modified_time  timestamp,
    last_modified_by    varchar(255),
    username            varchar(255),
    groupname           varchar(255),
    app                 varchar(255),
    form                varchar(255),
    document_id         varchar(255),
    deleted             character(1) not null,
    draft               character(1) not null,
    file_name           varchar(255),
    file_content        blob(2097152)
);