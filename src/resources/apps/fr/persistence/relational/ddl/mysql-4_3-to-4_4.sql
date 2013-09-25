alter table orbeon_form_definition
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255),
    add    form_version                       int            after form;

alter table orbeon_form_definition_attach
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255),
    add    form_version                       int            after form;

alter table orbeon_form_data
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255),
    add    username                           varchar(255)   after last_modified_by,
    add    groupname                          varchar(255)   after username,
    add    form_version                       int            after form,
    add    draft                              char(1)        after document_id;

alter table orbeon_form_data_attach
    change last_modified  last_modified_time  timestamp,
    change username       last_modified_by    varchar(255),
    add    username                           varchar(255)   after last_modified_by,
    add    groupname                          varchar(255)   after username,
    add    form_version                       int            after form,
    add    draft                              char(1)        after document_id;

update      orbeon_form_data        set draft = 'N';
update      orbeon_form_data_attach set draft = 'N';
alter table orbeon_form_data        change draft draft char(1) not null;
alter table orbeon_form_data_attach change draft draft char(1) not null;