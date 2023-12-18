/* AUTOINCREMENT is only allowed on an INTEGER PRIMARY KEY */

CREATE TABLE orbeon_form_definition (
    created             TIMESTAMP(6)                ,
    last_modified_time  TIMESTAMP(6)                ,
    last_modified_by    VARCHAR(255)                ,
    app                 VARCHAR(255)                ,
    form                VARCHAR(255)                ,
    form_version        INT                 NOT NULL,
    form_metadata       VARCHAR(4000)               ,
    deleted             CHAR(1)             NOT NULL,
    xml                 MEDIUMTEXT
);

CREATE TABLE orbeon_form_definition_attach (
    created             TIMESTAMP(6)                ,
    last_modified_time  TIMESTAMP(6)                ,
    last_modified_by    VARCHAR(255)                ,
    app                 VARCHAR(255)                ,
    form                VARCHAR(255)                ,
    form_version        INT                 NOT NULL,
    deleted             CHAR(1)             NOT NULL,
    file_name           VARCHAR(255)                ,
    file_content        LONGBLOB
);

CREATE TABLE orbeon_form_data (
    id                  INTEGER             NOT NULL PRIMARY KEY AUTOINCREMENT,
    created             TIMESTAMP(6)                                          ,
    last_modified_time  TIMESTAMP(6)                                          ,
    last_modified_by    VARCHAR(255)                                          ,
    username            VARCHAR(255)                                          ,
    groupname           VARCHAR(255)                                          ,
    organization_id     INT                                                   ,
    app                 VARCHAR(255)                                          ,
    form                VARCHAR(255)                                          ,
    form_version        INT                 NOT NULL                          ,
    stage               VARCHAR(255)                                          ,
    document_id         VARCHAR(255)                                          ,
    draft               CHAR(1)             NOT NULL                          ,
    deleted             CHAR(1)             NOT NULL                          ,
    xml                 MEDIUMTEXT
);

CREATE TABLE orbeon_form_data_attach (
    created             TIMESTAMP(6)                ,
    last_modified_time  TIMESTAMP(6)                ,
    last_modified_by    VARCHAR(255)                ,
    username            VARCHAR(255)                ,
    groupname           VARCHAR(255)                ,
    organization_id     INT                         ,
    app                 VARCHAR(255)                ,
    form                VARCHAR(255)                ,
    form_version        INT                 NOT NULL,
    document_id         VARCHAR(255)                ,
    draft               CHAR(1)             NOT NULL,
    deleted             CHAR(1)             NOT NULL,
    file_name           VARCHAR(255)                ,
    file_content        LONGBLOB
);

CREATE TABLE orbeon_form_data_lease (
    document_id         VARCHAR(255)        NOT NULL PRIMARY KEY,
    username            VARCHAR(255)        NOT NULL            ,
    groupname           VARCHAR(255)                            ,
    expiration          TIMESTAMP(6)        NOT NULL
);

CREATE TABLE orbeon_organization (
    id                  INTEGER             NOT NULL,
    depth               INT                 NOT NULL,
    pos                 INT                 NOT NULL,
    name                VARCHAR(255)        NOT NULL
);

CREATE TABLE orbeon_seq (
    val                 INTEGER             NOT NULL PRIMARY KEY AUTOINCREMENT
);

CREATE TABLE orbeon_i_current (
    data_id             INT                 NOT NULL,
    created             TIMESTAMP(6)        NOT NULL,
    last_modified_time  TIMESTAMP(6)        NOT NULL,
    last_modified_by    VARCHAR(255)                ,
    username            VARCHAR(255)                ,
    groupname           VARCHAR(255)                ,
    organization_id     INT                         ,
    app                 VARCHAR(255)        NOT NULL,
    form                VARCHAR(255)        NOT NULL,
    form_version        INT                 NOT NULL,
    stage               VARCHAR(255)                ,
    document_id         VARCHAR(255)        NOT NULL,
    draft               CHAR(1)             NOT NULL,
    FOREIGN KEY         (data_id)           REFERENCES orbeon_form_data(id)
);

CREATE TABLE orbeon_i_control_text (
    data_id             INT                 NOT NULL,
    pos                 INT                 NOT NULL,
    control             VARCHAR(255)        NOT NULL,
    val                 MEDIUMTEXT          NOT NULL,
    FOREIGN KEY         (data_id)           REFERENCES orbeon_form_data(id)
);

CREATE        INDEX orbeon_form_definition_i1     ON orbeon_form_definition        (app, form);
CREATE        INDEX orbeon_form_definition_i2     ON orbeon_form_definition        (app, form, form_version, last_modified_time);
CREATE        INDEX orbeon_form_definition_att_i1 ON orbeon_form_definition_attach (app, form, file_name);
CREATE        INDEX orbeon_from_data_i1           ON orbeon_form_data              (app, form, document_id, draft);
CREATE        INDEX orbeon_form_data_i2           ON orbeon_form_data              (document_id);
CREATE        INDEX orbeon_from_data_attach_i1    ON orbeon_form_data_attach       (app, form, document_id, draft);
CREATE        INDEX orbeon_from_data_attach_i2    ON orbeon_form_data_attach       (document_id);
CREATE UNIQUE INDEX orbeon_i_current_i1           ON orbeon_i_current              (data_id, draft);
CREATE        INDEX orbeon_i_current_i2           ON orbeon_i_current              (app, form, draft);
CREATE        INDEX orbeon_i_current_i3           ON orbeon_i_current              (document_id, draft);
CREATE        INDEX orbeon_i_control_text_i1      ON orbeon_i_control_text         (data_id);
