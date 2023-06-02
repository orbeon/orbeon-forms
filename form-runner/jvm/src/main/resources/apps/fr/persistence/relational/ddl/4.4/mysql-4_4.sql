alter database character set utf8 collate utf8_general_ci;

create table orbeon_form_definition (
    created            timestamp(6),
    last_modified_time timestamp(6),
    last_modified_by   varchar(255),
    app                varchar(255),
    form               varchar(255),
    form_version       int not null,
    deleted            char(1) not null,
    xml                mediumtext
)   engine = InnoDB;

create table orbeon_form_definition_attach (
    created            timestamp(6),
    last_modified_time timestamp(6),
    last_modified_by   varchar(255),
    app                varchar(255),
    form               varchar(255),
    form_version       int not null,
    deleted            char(1) not null,
    file_name          varchar(255),
    file_content       longblob
)   engine = InnoDB;

create table orbeon_form_data (
    created            timestamp(6),
    last_modified_time timestamp(6),
    last_modified_by   varchar(255),
    username           varchar(255),
    groupname          varchar(255),
    app                varchar(255),
    form               varchar(255),
    form_version       int not null,
    document_id        varchar(255),
    draft              char(1) not null,
    deleted            char(1) not null,
    xml                mediumtext
)   engine = InnoDB;

create table orbeon_form_data_attach (
    created            timestamp(6),
    last_modified_time timestamp(6),
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
    file_content       longblob
)   engine = InnoDB;