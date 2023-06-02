CREATE TABLE orbeon_form_data_lease (
    document_id         VARCHAR2(255)   NOT NULL PRIMARY KEY,
    username            VARCHAR2(255)   NOT NULL            ,
    groupname           VARCHAR2(255)                       ,
    expiration          TIMESTAMP       NOT NULL
);
