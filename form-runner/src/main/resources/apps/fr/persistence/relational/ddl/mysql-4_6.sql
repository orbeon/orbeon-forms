ALTER DATABASE CHARACTER SET utf8 COLLATE utf8_general_ci;

CREATE TABLE orbeon_form_definition (
    created            TIMESTAMP(6),
    last_modified_time TIMESTAMP(6),
    last_modified_by   VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       INT NOT NULL,
    form_metadata      VARCHAR(4000)                             COLLATE utf8_bin,
    deleted            CHAR(1)                                   COLLATE utf8_bin        NOT NULL,
    xml                MEDIUMTEXT             CHARACTER SET utf8 COLLATE utf8_unicode_ci
)   ENGINE = InnoDB;

CREATE TABLE orbeon_form_definition_attach (
    created            TIMESTAMP(6),
    last_modified_time TIMESTAMP(6),
    last_modified_by   VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       INT NOT NULL,
    deleted            CHAR(1)                                   COLLATE utf8_bin        NOT NULL,
    file_name          VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    file_content       LONGBLOB
)   ENGINE = InnoDB;

CREATE TABLE orbeon_form_data (
    created            TIMESTAMP(6),
    last_modified_time TIMESTAMP(6),
    last_modified_by   VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    username           VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    groupname          VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       INT NOT NULL,
    document_id        VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    draft              CHAR(1)                                   COLLATE utf8_bin        NOT NULL,
    deleted            CHAR(1)                                   COLLATE utf8_bin        NOT NULL,
    xml                MEDIUMTEXT             CHARACTER SET utf8 COLLATE utf8_unicode_ci
)   ENGINE = InnoDB;

CREATE TABLE orbeon_form_data_attach (
    created            TIMESTAMP(6),
    last_modified_time TIMESTAMP(6),
    last_modified_by   VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    username           VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    groupname          VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    app                VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form               VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    form_version       INT NOT NULL,
    document_id        VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    draft              CHAR(1)                                   COLLATE utf8_bin        NOT NULL,
    deleted            CHAR(1)                                   COLLATE utf8_bin        NOT NULL,
    file_name          VARCHAR(255)           CHARACTER SET utf8 COLLATE utf8_bin,
    file_content       LONGBLOB
)   ENGINE = InnoDB;