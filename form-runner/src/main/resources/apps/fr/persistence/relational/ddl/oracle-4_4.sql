create table orbeon_form_definition (
    created            timestamp,
    last_modified_time timestamp,
    last_modified_by   varchar2(255),
    app                varchar2(255),
    form               varchar2(255),
    form_version       int not null,
    deleted            char(1) not null,
    xml                xmltype
) xmltype column xml store as basicfile clob;

create table orbeon_form_definition_attach (
    created            timestamp,
    last_modified_time timestamp,
    last_modified_by   varchar2(255),
    app                varchar2(255),
    form               varchar2(255),
    form_version       int not null,
    deleted            char(1) not null,
    file_name          varchar2(255),
    file_content       blob
);

create table orbeon_form_data (
    created            timestamp not null,
    last_modified_time timestamp not null,
    last_modified_by   varchar2(255),
    username           varchar2(255),
    groupname          varchar2(255),
    app                varchar2(255) not null,
    form               varchar2(255) not null,
    form_version       int not null,
    document_id        varchar2(255) not null,
    draft              char(1) not null,
    deleted            char(1) not null,
    xml                xmltype not null,
    constraint orbeon_form_data_pk primary key (document_id, last_modified_time)
) xmltype column xml store as basicfile clob;

create table orbeon_form_data_attach (
    created            timestamp,
    last_modified_time timestamp,
    last_modified_by   varchar2(255),
    username           varchar2(255),
    groupname          varchar2(255),
    app                varchar2(255),
    form               varchar2(255),
    form_version       int not null,
    document_id        varchar2(255),
    draft              char(1) not null,
    deleted            char(1) not null,
    file_name          varchar2(255),
    file_content       blob
);

create index orbeon_form_definition_x      on orbeon_form_definition        (xml) indextype is ctxsys.context parameters ('sync (on commit)');
create index orbeon_form_data_x            on orbeon_form_data              (xml) indextype is ctxsys.context parameters ('sync (on commit)');

create index orbeon_form_definition_i1     on orbeon_form_definition        (app, form);
create index orbeon_form_definition_att_i1 on orbeon_form_definition_attach (app, form, file_name);
create index orbeon_from_data_i1           on orbeon_form_data              (app, form, document_id);
create index orbeon_from_data_attach_i1    on orbeon_form_data_attach       (app, form, document_id, file_name);