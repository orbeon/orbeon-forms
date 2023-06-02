CREATE TABLE orbeon_form_data_lease (
    document_id         VARCHAR(255)  NOT NULL PRIMARY KEY,
    username            VARCHAR(255)  NOT NULL            ,
    groupname           VARCHAR(255)                      ,
    expiration          TIMESTAMP     NOT NULL
);
