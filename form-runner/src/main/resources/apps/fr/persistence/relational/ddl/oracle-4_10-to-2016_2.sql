-- Introducing unique sequence number for primary keys

CREATE SEQUENCE orbeon_seq;

-- Add a simple id as primary key

ALTER TABLE orbeon_form_data
ADD         id NUMBER;

UPDATE orbeon_form_data
SET    id = orbeon_seq.nextval;

ALTER TABLE orbeon_form_data
MODIFY      id NUMBER NOT NULL;

ALTER TABLE orbeon_form_data
DROP CONSTRAINT orbeon_form_data_pk;

ALTER TABLE orbeon_form_data
ADD PRIMARY KEY (id);

-- New index tables

CREATE TABLE orbeon_i_current (
    data_id             NUMBER          NOT NULL,
    created             TIMESTAMP       NOT NULL,
    last_modified_time  TIMESTAMP       NOT NULL,
    last_modified_by    VARCHAR2(255)           ,
    username            VARCHAR2(255)           ,
    groupname           VARCHAR2(255)           ,
    app                 VARCHAR2(255)   NOT NULL,
    form                VARCHAR2(255)   NOT NULL,
    form_version        INT             NOT NULL,
    document_id         VARCHAR2(255)   NOT NULL,
    draft               CHAR(1)         NOT NULL,
    FOREIGN KEY         (data_id)       REFERENCES orbeon_form_data(id)
);

CREATE TABLE orbeon_i_control_text (
    data_id             NUMBER          NOT NULL,
    control             VARCHAR(255)    NOT NULL,
    pos                 NUMBER          NOT NULL,
    val                 CLOB            NOT NULL,
    FOREIGN KEY         (data_id)       REFERENCES orbeon_form_data(id)
);

CREATE UNIQUE INDEX orbeon_i_current_i1           ON orbeon_i_current (data_id);