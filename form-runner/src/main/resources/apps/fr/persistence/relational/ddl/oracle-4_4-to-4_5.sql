alter table orbeon_form_data       add xml_clob clob;
alter table orbeon_form_definition add xml_clob clob;

alter table orbeon_form_definition        modify(created            not null);
alter table orbeon_form_definition        modify(last_modified_time not null);
alter table orbeon_form_definition        modify(app                not null);
alter table orbeon_form_definition        modify(form               not null);

alter table orbeon_form_definition_attach modify(created            not null);
alter table orbeon_form_definition_attach modify(last_modified_time not null);
alter table orbeon_form_definition_attach modify(app                not null);
alter table orbeon_form_definition_attach modify(form               not null);
alter table orbeon_form_definition_attach modify(file_name          not null);
alter table orbeon_form_definition_attach modify(file_content       not null);

alter table orbeon_form_data              modify(xml                    null);

alter table orbeon_form_data_attach       modify(created            not null);
alter table orbeon_form_data_attach       modify(last_modified_time not null);
alter table orbeon_form_data_attach       modify(app                not null);
alter table orbeon_form_data_attach       modify(form               not null);
alter table orbeon_form_data_attach       modify(document_id        not null);
alter table orbeon_form_data_attach       modify(file_name          not null);
alter table orbeon_form_data_attach       modify(file_content       not null);

create or replace trigger orbeon_form_data_xml
         before insert on orbeon_form_data
for each row
begin
    if :new.xml_clob is not null then
        :new.xml      := XMLType(:new.xml_clob);
        :new.xml_clob := null;
    end if;
end;

create or replace trigger orbeon_form_definition_xml
         before insert on orbeon_form_definition
for each row
begin
    if :new.xml_clob is not null then
        :new.xml      := XMLType(:new.xml_clob);
        :new.xml_clob := null;
    end if;
end;
