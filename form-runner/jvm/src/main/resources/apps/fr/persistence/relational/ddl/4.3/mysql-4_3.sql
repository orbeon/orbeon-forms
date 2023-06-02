alter database character set utf8 collate utf8_general_ci;

create table orbeon_form_definition (
    created        timestamp(6),
    last_modified  timestamp(6),
    username       varchar(255),
    app            varchar(255),
    form           varchar(255),
    deleted        char(1) not null,
    xml            mediumtext
)   engine = InnoDB;

create table orbeon_form_definition_attach (
    created        timestamp(6),
    last_modified  timestamp(6),
    username       varchar(255),
    app            varchar(255),
    form           varchar(255),
    deleted        char(1) not null,
    file_name      varchar(255),
    file_content   longblob
)   engine = InnoDB;

create table orbeon_form_data (
    created        timestamp(6),
    last_modified  timestamp(6),
    username       varchar(255),
    app            varchar(255),
    form           varchar(255),
    document_id    varchar(255),
    deleted        char(1) not null,
    xml            mediumtext
)   engine = InnoDB;

create table orbeon_form_data_attach (
    created        timestamp(6),
    last_modified  timestamp(6),
    username       varchar(255),
    app            varchar(255),
    form           varchar(255),
    document_id    varchar(255),
    deleted        char(1) not null,
    file_name      varchar(255),
    file_content   longblob
)   engine = InnoDB;
