CREATE TABLE orbeon_form_data_lease (
    document_id         NVARCHAR(255)      PRIMARY KEY NOT NULL,
    username            NVARCHAR(255)                  NOT NULL,
    groupname           NVARCHAR(255)                          ,
    expiration          DATETIME                       NOT NULL
);
