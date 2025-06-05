CREATE BUFFERPOOL BP32K IMMEDIATE SIZE 250 AUTOMATIC PAGESIZE 32K;
CREATE LARGE TABLESPACE TS32K PAGESIZE 32K MANAGED BY AUTOMATIC STORAGE BUFFERPOOL BP32K;

CREATE TABLE orbeon_form_definition (
    created             TIMESTAMP              ,
    last_modified_time  TIMESTAMP              ,
    last_modified_by    VARCHAR(1020)          ,
    app                 VARCHAR(1020)          ,
    form                VARCHAR(1020)          ,
    form_version        INT            NOT NULL,
    form_metadata       VARCHAR(4000)          ,
    deleted             CHARACTER(1)   NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_definition_attach (
    created             TIMESTAMP              ,
    last_modified_time  TIMESTAMP              ,
    last_modified_by    VARCHAR(1020)          ,
    app                 VARCHAR(1020)          ,
    form                VARCHAR(1020)          ,
    form_version        INT            NOT NULL,
    deleted             CHARACTER(1)   NOT NULL,
    file_name           VARCHAR(1020)          ,
    file_content        BLOB(1048576)          ,
    hash_algorithm      VARCHAR(1020)          ,
    hash_value          VARCHAR(1020)
);

CREATE TABLE orbeon_form_data (
    id                  INT             NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1),
    created             TIMESTAMP               ,
    last_modified_time  TIMESTAMP               ,
    last_modified_by    VARCHAR(1020)           ,
    username            VARCHAR(1020)           ,
    groupname           VARCHAR(1020)           ,
    organization_id     INT                     ,
    app                 VARCHAR(1020)           ,
    form                VARCHAR(1020)           ,
    form_version        INT             NOT NULL,
    stage               VARCHAR(1020)           ,
    document_id         VARCHAR(1020)           ,
    deleted             CHARACTER(1)    NOT NULL,
    draft               CHARACTER(1)    NOT NULL,
    xml                 XML,
    PRIMARY KEY(id)
);

CREATE TABLE orbeon_form_data_attach (
    created             TIMESTAMP               ,
    last_modified_time  TIMESTAMP               ,
    last_modified_by    VARCHAR(1020)           ,
    username            VARCHAR(1020)           ,
    groupname           VARCHAR(1020)           ,
    organization_id     INT                     ,
    app                 VARCHAR(1020)           ,
    form                VARCHAR(1020)           ,
    form_version        INT             NOT NULL,
    document_id         VARCHAR(1020)           ,
    deleted             CHARACTER(1)    NOT NULL,
    draft               CHARACTER(1)    NOT NULL,
    file_name           VARCHAR(1020)           ,
    file_content        BLOB(2097152)           ,
    hash_algorithm      VARCHAR(1020)           ,
    hash_value          VARCHAR(1020)
);

CREATE TABLE orbeon_form_data_lease (
    document_id         VARCHAR(1020)   NOT NULL PRIMARY KEY,
    username            VARCHAR(1020)   NOT NULL            ,
    groupname           VARCHAR(1020)                       ,
    expiration          TIMESTAMP       NOT NULL
);

CREATE TABLE orbeon_organization (
    id                  INT            NOT NULL,
    depth               INT            NOT NULL,
    pos                 INT            NOT NULL,
    name                VARCHAR(1020)  NOT NULL
);

CREATE TABLE orbeon_seq (
    val                 INT            NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1)
);

CREATE TABLE orbeon_i_current (
    data_id             INT            NOT NULL,
    created             TIMESTAMP      NOT NULL,
    last_modified_time  TIMESTAMP      NOT NULL,
    last_modified_by    VARCHAR(1020)          ,
    username            VARCHAR(1020)          ,
    groupname           VARCHAR(1020)          ,
    organization_id     INT                   ,
    app                 VARCHAR(1020)  NOT NULL,
    form                VARCHAR(1020)  NOT NULL,
    form_version        INT            NOT NULL,
    stage               VARCHAR(1020)          ,
    document_id         VARCHAR(1020)  NOT NULL,
    draft               CHARACTER(1)   NOT NULL,
    FOREIGN KEY         (data_id)      REFERENCES orbeon_form_data(id)
);

CREATE TABLE orbeon_i_control_text (
    data_id             INT           NOT NULL,
    pos                 INT           NOT NULL,
    control             VARCHAR(1020) NOT NULL,
    val                 CLOB          NOT NULL,
    FOREIGN KEY         (data_id)     REFERENCES orbeon_form_data(id)
);

CREATE        INDEX orbeon_form_definition_i1     ON orbeon_form_definition        (app, form, form_version, last_modified_time);
CREATE        INDEX orbeon_form_definition_att_i1 ON orbeon_form_definition_attach (app, form, file_name);
CREATE        INDEX orbeon_form_data_i1           ON orbeon_form_data              (app, form, document_id, draft);
CREATE        INDEX orbeon_form_data_i2           ON orbeon_form_data              (document_id);
CREATE        INDEX orbeon_form_data_attach_i1    ON orbeon_form_data_attach       (app, form, document_id, draft);
CREATE        INDEX orbeon_form_data_attach_i2    ON orbeon_form_data_attach       (document_id);
CREATE UNIQUE INDEX orbeon_i_current_i1           ON orbeon_i_current              (data_id, draft);
CREATE        INDEX orbeon_i_current_i2           ON orbeon_i_current              (app, form, draft);
CREATE        INDEX orbeon_i_current_i3           ON orbeon_i_current              (document_id, draft);
CREATE        INDEX orbeon_i_control_text_i1      ON orbeon_i_control_text         (data_id);