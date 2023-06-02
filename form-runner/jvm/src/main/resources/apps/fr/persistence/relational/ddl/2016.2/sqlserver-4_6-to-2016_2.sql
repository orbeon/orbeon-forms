-- id column was already there, but not defined as primary key

ALTER TABLE orbeon_form_data
ADD PRIMARY KEY (id);

-- New index tables

CREATE TABLE orbeon_i_current (
    data_id             INT            NOT NULL,
    created             DATETIME       NOT NULL,
    last_modified_time  DATETIME       NOT NULL,
    last_modified_by    NVARCHAR(255)          ,
    username            NVARCHAR(255)          ,
    groupname           NVARCHAR(255)          ,
    app                 NVARCHAR(255)  NOT NULL,
    form                NVARCHAR(255)  NOT NULL,
    form_version        INT            NOT NULL,
    document_id         NVARCHAR(255)  NOT NULL,
    draft               CHAR(1)        NOT NULL,
    FOREIGN KEY         (data_id)      REFERENCES orbeon_form_data(id)
);

CREATE TABLE orbeon_i_control_text (
    data_id             INT             NOT NULL,
    control             VARCHAR(255)    NOT NULL,
    pos                 INT             NOT NULL,
    val                 NTEXT           NOT NULL,
    FOREIGN KEY         (data_id)       REFERENCES orbeon_form_data(id)
);
