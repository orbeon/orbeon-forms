CREATE TABLE orbeon_form_definition (
    created             TIMESTAMP             ,
    last_modified_time  TIMESTAMP             ,
    last_modified_by    VARCHAR(255)          ,
    app                 VARCHAR(255)          ,
    form                VARCHAR(255)          ,
    form_version        INT           NOT NULL,
    form_metadata       VARCHAR(4000)         ,
    deleted             CHARACTER(1)  NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_definition_attach (
    created             TIMESTAMP             ,
    last_modified_time  TIMESTAMP             ,
    last_modified_by    VARCHAR(255)          ,
    app                 VARCHAR(255)          ,
    form                VARCHAR(255)          ,
    form_version        INT           NOT NULL,
    deleted             CHARACTER(1)  NOT NULL,
    file_name           VARCHAR(255)          ,
    file_content        BLOB(1048576)
);

CREATE TABLE orbeon_form_data (
    id                  INT           NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1),
    created             TIMESTAMP             ,
    last_modified_time  TIMESTAMP             ,
    last_modified_by    VARCHAR(255)          ,
    username            VARCHAR(255)          ,
    groupname           VARCHAR(255)          ,
    organization_id     INT                   ,
    app                 VARCHAR(255)          ,
    form                VARCHAR(255)          ,
    form_version        INT           NOT NULL,
    document_id         VARCHAR(255)          ,
    deleted             CHARACTER(1)  NOT NULL,
    draft               CHARACTER(1)  NOT NULL,
    xml                 XML,
    PRIMARY KEY(id)
);

CREATE TABLE orbeon_form_data_attach (
    created             TIMESTAMP             ,
    last_modified_time  TIMESTAMP             ,
    last_modified_by    VARCHAR(255)          ,
    username            VARCHAR(255)          ,
    groupname           VARCHAR(255)          ,
    organization_id     INT                   ,
    app                 VARCHAR(255)          ,
    form                VARCHAR(255)          ,
    form_version        INT           NOT NULL,
    document_id         VARCHAR(255)          ,
    deleted             CHARACTER(1)  NOT NULL,
    draft               CHARACTER(1)  NOT NULL,
    file_name           VARCHAR(255),
    file_content        BLOB(2097152)
);

CREATE TABLE orbeon_form_data_lease (
    document_id         VARCHAR(255)   NOT NULL PRIMARY KEY,
    username            VARCHAR(255)   NOT NULL            ,
    groupname           VARCHAR(255)                       ,
    expiration          TIMESTAMP      NOT NULL
);

CREATE TABLE orbeon_organization (
    id                  INT           NOT NULL,
    depth               INT           NOT NULL,
    pos                 INT           NOT NULL,
    name                VARCHAR(255)  NOT NULL
);

CREATE TABLE orbeon_seq (
    val                 INT           NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1)
);

CREATE TABLE orbeon_i_current (
    data_id             INT           NOT NULL,
    created             TIMESTAMP     NOT NULL,
    last_modified_time  TIMESTAMP     NOT NULL,
    last_modified_by    VARCHAR(255)          ,
    username            VARCHAR(255)          ,
    groupname           VARCHAR(255)          ,
    organization_id     INT                   ,
    app                 VARCHAR(255)  NOT NULL,
    form                VARCHAR(255)  NOT NULL,
    form_version        INT           NOT NULL,
    document_id         VARCHAR(255)  NOT NULL,
    draft               CHARACTER(1)  NOT NULL,
    FOREIGN KEY         (data_id)     REFERENCES orbeon_form_data(id)
);

CREATE TABLE orbeon_i_control_text (
    data_id             INT           NOT NULL,
    pos                 INT           NOT NULL,
    control             VARCHAR(255)  NOT NULL,
    val                 CLOB          NOT NULL,
    FOREIGN KEY         (data_id)     REFERENCES orbeon_form_data(id)
);
