-- CI only: minimal CloudBase built-in schemas; never run this on a real environment.
create role anon;
create role authenticated;
create role service_role bypassrls;
create schema auth;
create schema storage;
create function auth.uid() returns text language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')$$;
create function auth.role() returns text language sql stable as $$select nullif(current_setting('request.jwt.claim.role',true),'')$$;
create function storage.foldername(name text) returns text[] language sql immutable as $$select (string_to_array(name,'/'))[1:array_length(string_to_array(name,'/'),1)-1]$$;
create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
create table storage.objects(id bigserial primary key,bucket_id text,name text);
alter table storage.objects enable row level security;
grant usage on schema public,auth,storage to anon,authenticated;
grant select,insert on storage.objects to authenticated;
grant usage on sequence storage.objects_id_seq to authenticated;
create function public.test_assert(ok boolean) returns void language plpgsql as $$begin if ok is distinct from true then raise exception 'assertion failed';end if;end$$;
