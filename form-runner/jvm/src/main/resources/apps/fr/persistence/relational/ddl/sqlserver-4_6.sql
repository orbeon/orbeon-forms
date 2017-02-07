CREATE TABLE orbeon_form_definition (
    created            DATETIME,
    last_modified_time DATETIME,
    last_modified_by   NVARCHAR(255),
    app                NVARCHAR(255),
    form               NVARCHAR(255),
    form_version       int NOT NULL,
    form_metadata      NVARCHAR(4000),
    deleted            CHAR(1)        NOT NULL,
    xml                XML
);

CREATE TABLE orbeon_form_definition_attach (
    created            DATETIME,
    last_modified_time DATETIME,
    last_modified_by   NVARCHAR(255),
    app                NVARCHAR(255),
    form               NVARCHAR(255),
    form_version       INT NOT NULL,
    deleted            CHAR(1) NOT NULL,
    file_name          NVARCHAR(255),
    file_content       VARBINARY(max)
);

CREATE TABLE orbeon_form_data (
    id                 int identity(1, 1),
    created            DATETIME,
    last_modified_time DATETIME,
    last_modified_by   NVARCHAR(255),
    username           NVARCHAR(255),
    groupname          NVARCHAR(255),
    app                NVARCHAR(255),
    form               NVARCHAR(255),
    form_version       INT NOT NULL,
    document_id        NVARCHAR(255),
    draft              CHAR(1) NOT NULL,
    deleted            CHAR(1) NOT NULL,
    xml                XML
);

CREATE TABLE orbeon_form_data_attach (
    created            DATETIME,
    last_modified_time DATETIME,
    last_modified_by   NVARCHAR(255),
    username           NVARCHAR(255),
    groupname          NVARCHAR(255),
    app                NVARCHAR(255),
    form               NVARCHAR(255),
    form_version       INT NOT NULL,
    document_id        NVARCHAR(255),
    draft              CHAR(1) NOT NULL,
    deleted            CHAR(1) NOT NULL,
    file_name          NVARCHAR(255),
    file_content       VARBINARY(max)
);

CREATE FULLTEXT CATALOG orbeon_fulltext_catalog AS DEFAULT;
CREATE UNIQUE INDEX orbeon_from_data_pk ON orbeon_form_data (id);
CREATE FULLTEXT INDEX ON orbeon_form_data (xml) KEY INDEX orbeon_from_data_pk;
