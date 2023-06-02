CREATE TABLE orbeon_form_definition (
    created             DATETIME                               ,
    last_modified_time  DATETIME                               ,
    last_modified_by    NVARCHAR(255)                          ,
    app                 NVARCHAR(255)                          ,
    form                NVARCHAR(255)                          ,
    form_version        INT                            NOT NULL,
    form_metadata       NVARCHAR(4000)                         ,
    deleted             CHAR(1)                        NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_definition_attach (
    created             DATETIME                               ,
    last_modified_time  DATETIME                               ,
    last_modified_by    NVARCHAR(255)                          ,
    app                 NVARCHAR(255)                          ,
    form                NVARCHAR(255)                          ,
    form_version        INT                            NOT NULL,
    deleted             CHAR(1)                        NOT NULL,
    file_name           NVARCHAR(255)                          ,
    file_content        VARBINARY(max)
);

CREATE TABLE orbeon_form_data (
    id                  INT IDENTITY(1, 1) PRIMARY KEY NOT NULL,
    created             DATETIME                               ,
    last_modified_time  DATETIME                               ,
    last_modified_by    NVARCHAR(255)                          ,
    username            NVARCHAR(255)                          ,
    groupname           NVARCHAR(255)                          ,
    organization_id     INT                                    ,
    app                 NVARCHAR(255)                          ,
    form                NVARCHAR(255)                          ,
    form_version        INT                            NOT NULL,
    stage               NVARCHAR(255)                          ,
    document_id         NVARCHAR(255)                          ,
    draft               CHAR(1)                        NOT NULL,
    deleted             CHAR(1)                        NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_data_attach (
    created             DATETIME                               ,
    last_modified_time  DATETIME                               ,
    last_modified_by    NVARCHAR(255)                          ,
    username            NVARCHAR(255)                          ,
    groupname           NVARCHAR(255)                          ,
    organization_id     INT                                    ,
    app                 NVARCHAR(255)                          ,
    form                NVARCHAR(255)                          ,
    form_version        INT                            NOT NULL,
    document_id         NVARCHAR(255)                          ,
    draft               CHAR(1)                        NOT NULL,
    deleted             CHAR(1)                        NOT NULL,
    file_name           NVARCHAR(255)                          ,
    file_content        VARBINARY(max)
);

CREATE TABLE orbeon_form_data_lease (
    document_id         NVARCHAR(255)      PRIMARY KEY NOT NULL,
    username            NVARCHAR(255)                  NOT NULL,
    groupname           NVARCHAR(255)                          ,
    expiration          DATETIME                       NOT NULL
);

CREATE TABLE orbeon_organization (
    id                  INT                            NOT NULL,
    depth               INT                            NOT NULL,
    pos                 INT                            NOT NULL,
    name                NVARCHAR(255)                  NOT NULL
);

CREATE TABLE orbeon_seq (
    val                 INT IDENTITY(1, 1) PRIMARY KEY NOT NULL
);

CREATE TABLE orbeon_i_current (
    data_id             INT                            NOT NULL,
    created             DATETIME                       NOT NULL,
    last_modified_time  DATETIME                       NOT NULL,
    last_modified_by    NVARCHAR(255)                          ,
    username            NVARCHAR(255)                          ,
    groupname           NVARCHAR(255)                          ,
    organization_id     INT                                    ,
    app                 NVARCHAR(255)                  NOT NULL,
    form                NVARCHAR(255)                  NOT NULL,
    form_version        INT                            NOT NULL,
    stage               NVARCHAR(255)                          ,
    document_id         NVARCHAR(255)                  NOT NULL,
    draft               CHAR(1)                        NOT NULL,
    FOREIGN KEY         (data_id)                      REFERENCES orbeon_form_data(id)
);

CREATE TABLE orbeon_i_control_text (
    data_id             INT                            NOT NULL,
    control             VARCHAR(255)                   NOT NULL,
    pos                 INT                            NOT NULL,
    val                 NTEXT                          NOT NULL,
    FOREIGN KEY         (data_id)                      REFERENCES orbeon_form_data(id)
);

CREATE FULLTEXT CATALOG orbeon_fulltext_catalog AS DEFAULT;
CREATE UNIQUE INDEX orbeon_from_data_pk ON orbeon_form_data (id);
CREATE FULLTEXT INDEX ON orbeon_form_data (xml) KEY INDEX orbeon_from_data_pk;

CREATE        INDEX orbeon_form_definition_i1     ON orbeon_form_definition        (app, form);
CREATE        INDEX orbeon_form_definition_att_i1 ON orbeon_form_definition_attach (app, form, file_name);
CREATE        INDEX orbeon_from_data_i1           ON orbeon_form_data              (app, form, document_id, draft);
CREATE        INDEX orbeon_form_data_i2           ON orbeon_form_data              (document_id);
CREATE        INDEX orbeon_from_data_attach_i1    ON orbeon_form_data_attach       (app, form, document_id, draft);
CREATE        INDEX orbeon_from_data_attach_i2    ON orbeon_form_data_attach       (document_id);
CREATE UNIQUE INDEX orbeon_i_current_i1           ON orbeon_i_current              (data_id, draft);
CREATE        INDEX orbeon_i_current_i2           ON orbeon_i_current              (app, form, draft);
CREATE        INDEX orbeon_i_current_i3           ON orbeon_i_current              (document_id, draft);
CREATE        INDEX orbeon_i_control_text_i1      ON orbeon_i_control_text         (data_id);
