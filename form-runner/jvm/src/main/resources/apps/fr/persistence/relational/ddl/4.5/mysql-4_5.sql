ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_general_ci;

create table orbeon_form_definition (
    created            timestamp(6),
    last_modified_time timestamp(6),
    last_modified_by   varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       int not null,
    deleted            char(1)                                   COLLATE utf8_bin        NOT NULL,
    xml                mediumtext             CHARACTER SET utf8 COLLATE utf8_unicode_ci
)   engine = InnoDB;

create table orbeon_form_definition_attach (
    created            timestamp(6),
    last_modified_time timestamp(6),
    last_modified_by   varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       int not null,
    deleted            char(1)                                   COLLATE utf8_bin        NOT NULL,
    file_name          varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    file_content       longblob
)   engine = InnoDB;

create table orbeon_form_data (
    created            timestamp(6),
    last_modified_time timestamp(6),
    last_modified_by   varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    username           varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    groupname          varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       int not null,
    document_id        varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    draft              char(1)                                   COLLATE utf8_bin        NOT NULL,
    deleted            char(1)                                   COLLATE utf8_bin        NOT NULL,
    xml                mediumtext             CHARACTER SET utf8 COLLATE utf8_unicode_ci
)   engine = InnoDB;

create table orbeon_form_data_attach (
    created            timestamp(6),
    last_modified_time timestamp(6),
    last_modified_by   varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    username           varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    groupname          varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       int not null,
    document_id        varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    draft              char(1)                                   COLLATE utf8_bin        NOT NULL,
    deleted            char(1)                                   COLLATE utf8_bin        NOT NULL,
    file_name          varchar(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    file_content       longblob
)   engine = InnoDB;