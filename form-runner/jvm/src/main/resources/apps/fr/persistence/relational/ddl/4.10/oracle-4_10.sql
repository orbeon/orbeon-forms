CREATE TABLE orbeon_form_definition (
    created            TIMESTAMP       NOT NULL,
    last_modified_time TIMESTAMP       NOT NULL,
    last_modified_by   VARCHAR2(255),
    app                VARCHAR2(255)   NOT NULL,
    form               VARCHAR2(255)   NOT NULL,
    form_version       INT             NOT NULL,
    form_metadata      VARCHAR2(4000),
    deleted            CHAR(1)         NOT NULL,
    xml                XMLTYPE,
    xml_clob           CLOB
) XMLTYPE COLUMN xml STORE AS BASICFILE CLOB;

CREATE TABLE orbeon_form_definition_attach (
    created            TIMESTAMP       NOT NULL,
    last_modified_time TIMESTAMP       NOT NULL,
    last_modified_by   VARCHAR2(255),
    app                VARCHAR2(255)   NOT NULL,
    form               VARCHAR2(255)   NOT NULL,
    form_version       INT             NOT NULL,
    deleted            CHAR(1)         NOT NULL,
    file_name          VARCHAR2(255)   NOT NULL,
    file_content       BLOB
);

CREATE TABLE orbeon_form_data (
    created            TIMESTAMP       NOT NULL,
    last_modified_time TIMESTAMP       NOT NULL,
    last_modified_by   VARCHAR2(255),
    username           VARCHAR2(255),
    groupname          VARCHAR2(255),
    app                VARCHAR2(255)   NOT NULL,
    form               VARCHAR2(255)   NOT NULL,
    form_version       INT             NOT NULL,
    document_id        VARCHAR2(255)   NOT NULL,
    draft              CHAR(1)         NOT NULL,
    deleted            CHAR(1)         NOT NULL,
    xml                XMLTYPE,
    xml_clob           CLOB,
    CONSTRAINT orbeon_form_data_pk PRIMARY KEY (document_id, last_modified_time)
) XMLTYPE COLUMN xml STORE AS BASICFILE CLOB;

CREATE TABLE orbeon_form_data_attach (
    created            TIMESTAMP       NOT NULL,
    last_modified_time TIMESTAMP       NOT NULL,
    last_modified_by   VARCHAR2(255),
    username           VARCHAR2(255),
    groupname          VARCHAR2(255),
    app                VARCHAR2(255)   NOT NULL,
    form               VARCHAR2(255)   NOT NULL,
    form_version       INT             NOT NULL,
    document_id        VARCHAR2(255)   NOT NULL,
    draft              CHAR(1)         NOT NULL,
    deleted            CHAR(1)         NOT NULL,
    file_name          VARCHAR2(255)   NOT NULL,
    file_content       BLOB
);

CREATE INDEX orbeon_form_definition_x      ON orbeon_form_definition        (xml) INDEXTYPE IS ctxsys.context PARAMETERS ('sync (on commit)');
CREATE INDEX orbeon_form_data_x            ON orbeon_form_data              (xml) INDEXTYPE IS ctxsys.context PARAMETERS ('sync (on commit)');

CREATE INDEX orbeon_form_definition_i1     ON orbeon_form_definition        (app, form);
CREATE INDEX orbeon_form_definition_att_i1 ON orbeon_form_definition_attach (app, form, file_name);
CREATE INDEX orbeon_from_data_i1           ON orbeon_form_data              (app, form, document_id);
CREATE INDEX orbeon_from_data_attach_i1    ON orbeon_form_data_attach       (app, form, document_id, file_name);

CREATE OR REPLACE TRIGGER orbeon_form_data_xml
         BEFORE INSERT ON orbeon_form_data
FOR EACH ROW
BEGIN
    IF :new.xml_clob IS NOT NULL THEN
        :new.xml      := XMLType(:new.xml_clob);
        :new.xml_clob := NULL;
    END IF;
END;
/
CREATE OR REPLACE TRIGGER orbeon_form_definition_xml
         BEFORE INSERT ON orbeon_form_definition
FOR EACH ROW
BEGIN
    IF :new.xml_clob IS NOT NULL THEN
        :new.xml      := XMLType(:new.xml_clob);
        :new.xml_clob := NULL;
    END IF;
END;
/
