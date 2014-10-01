CREATE TABLE orbeon_form_definition (
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    form_metadata       VARCHAR(4000),
    deleted             CHARACTER(1)  NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_definition_attach (
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    deleted             CHARACTER(1) NOT NULL,
    file_name           VARCHAR(255),
    file_content        BYTEA
);

CREATE TABLE orbeon_form_data (
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    username            VARCHAR(255),
    groupname           VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    document_id         VARCHAR(255),
    deleted             CHARACTER(1) NOT NULL,
    draft               CHARACTER(1) NOT NULL,
    xml                 XML
);

CREATE TABLE orbeon_form_data_attach (
    created             TIMESTAMP,
    last_modified_time  TIMESTAMP,
    last_modified_by    VARCHAR(255),
    username            VARCHAR(255),
    groupname           VARCHAR(255),
    app                 VARCHAR(255),
    form                VARCHAR(255),
    form_version        INT NOT NULL,
    document_id         VARCHAR(255),
    deleted             CHARACTER(1) NOT NULL,
    draft               CHARACTER(1) NOT NULL,
    file_name           VARCHAR(255),
    file_content        BYTEA
);
