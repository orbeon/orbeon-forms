alter table orbeon_form_data       add xml_clob clob;
alter table orbeon_form_definition add xml_clob clob;

create or replace trigger orbeon_form_data_xml
         before insert on orbeon_form_data
for each row
begin
  :new.xml      := XMLType(:new.xml_clob);
  :new.xml_clob := null;
end;

create or replace trigger orbeon_form_definition_xml
         before insert on orbeon_form_definition
for each row
begin
  :new.xml      := XMLType(:new.xml_clob);
  :new.xml_clob := null;
end;
