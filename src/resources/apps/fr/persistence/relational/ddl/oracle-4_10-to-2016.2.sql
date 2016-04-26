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

-- Latest data

CREATE TABLE orbeon_i_current (
    data_id            INT           NOT NULL,
    document_id        VARCHAR(255)  NOT NULL,
    created            TIMESTAMP     NOT NULL,
    last_modified_time TIMESTAMP     NOT NULL,
    username           VARCHAR(255),
    app                VARCHAR(255)  NOT NULL,
    form               VARCHAR(255)  NOT NULL,
    FOREIGN KEY        (data_id)     REFERENCES orbeon_form_data(id)
);

CREATE UNIQUE INDEX orbeon_i_current_i1 ON orbeon_i_current (data_id);

-- Text index for control values

CREATE TABLE orbeon_i_control_text (
    data_id       NUMBER        NOT NULL,
    pos           NUMBER        NOT NULL,
    control       VARCHAR(255)  NOT NULL,
    val           CLOB          NOT NULL,
    FOREIGN KEY   (data_id) REFERENCES orbeon_form_data(id)
);
