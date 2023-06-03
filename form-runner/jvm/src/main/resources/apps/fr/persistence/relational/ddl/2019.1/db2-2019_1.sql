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
    file_content        BLOB(1048576)
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
    file_content        BLOB(2097152)
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
