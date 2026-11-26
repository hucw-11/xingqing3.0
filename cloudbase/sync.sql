-- CloudBase PostgreSQL environment; execute as environment administrator.
-- No service key is ever embedded in the APK.
begin;
create table if not exists public.xq_sync_records (
  owner_id text not null default auth.uid(),
  record_key text not null,
  payload jsonb,
  deleted boolean not null default false,
  revision bigint not null,
  mutation_id text not null,
  primary key(owner_id, record_key),
  check (length(record_key) between 3 and 240),
  check (deleted or jsonb_typeof(payload) = 'object')
);
create table if not exists public.xq_sync_counters(owner_id text primary key, revision bigint not null default 0);
create index if not exists xq_sync_incremental on public.xq_sync_records(owner_id, revision);
alter table public.xq_sync_records enable row level security;
alter table public.xq_sync_counters enable row level security;
revoke all on public.xq_sync_records, public.xq_sync_counters from anon, authenticated;
grant select on public.xq_sync_records to authenticated;
drop policy if exists xq_read_own on public.xq_sync_records;
create policy xq_read_own on public.xq_sync_records for select to authenticated using(owner_id=auth.uid());

create or replace function public.xq_sync_put(p_key text, p_payload jsonb, p_deleted boolean, p_expected bigint, p_mutation text)
returns jsonb language plpgsql security definer set search_path=pg_catalog,public as $$
declare
  uid text := auth.uid();
  existing public.xq_sync_records%rowtype;
  counter bigint;
begin
  if uid is null or uid='' or auth.role()<>'authenticated' then raise exception 'login required' using errcode='42501'; end if;
  if p_key is null or p_key !~ '^(courses|events|moods|labels|settings):[A-Za-z0-9._:-]{1,200}$'
     or p_mutation is null or length(p_mutation)>100 or length(p_mutation)<8
     or p_expected is null or p_expected<0 or p_deleted is null then raise exception 'invalid mutation'; end if;
  if not p_deleted and (p_payload is null or jsonb_typeof(p_payload)<>'object' or octet_length(p_payload::text)>2097152) then raise exception 'invalid payload'; end if;
  -- The per-owner row lock serializes commits, so incremental cursors never skip a late commit.
  insert into public.xq_sync_counters(owner_id) values(uid) on conflict do nothing;
  select revision into counter from public.xq_sync_counters where owner_id=uid for update;
  select * into existing from public.xq_sync_records where owner_id=uid and record_key=p_key;
  if found then
    if existing.mutation_id=p_mutation then return jsonb_build_object('accepted',true,'record',to_jsonb(existing)-'owner_id'); end if;
    if existing.revision<>p_expected then return jsonb_build_object('accepted',false,'record',to_jsonb(existing)-'owner_id'); end if;
  elsif p_expected<>0 then raise exception 'baseline missing';
  end if;
  counter:=counter+1;
  update public.xq_sync_counters set revision=counter where owner_id=uid;
  insert into public.xq_sync_records(owner_id,record_key,payload,deleted,revision,mutation_id)
  values(uid,p_key,case when p_deleted then null else p_payload end,p_deleted,counter,p_mutation)
  on conflict(owner_id,record_key) do update set payload=excluded.payload,deleted=excluded.deleted,revision=excluded.revision,mutation_id=excluded.mutation_id
  returning * into existing;
  return jsonb_build_object('accepted',true,'record',to_jsonb(existing)-'owner_id');
end $$;
revoke all on function public.xq_sync_put(text,jsonb,boolean,bigint,text) from public, anon;
grant execute on function public.xq_sync_put(text,jsonb,boolean,bigint,text) to authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('xq-photos','xq-photos',false,16777216,array['image/jpeg','image/png','application/octet-stream'])
on conflict(id) do nothing;
drop policy if exists xq_photos_read on storage.objects;
drop policy if exists xq_photos_insert on storage.objects;
create policy xq_photos_read on storage.objects for select to authenticated
using(bucket_id='xq-photos' and (storage.foldername(name))[1]=auth.uid());
create policy xq_photos_insert on storage.objects for insert to authenticated
with check(bucket_id='xq-photos' and (storage.foldername(name))[1]=auth.uid());
-- Photo names are immutable UUIDs. Do not grant overwrite/delete to phone clients.
notify pgrst,'reload schema';
commit;
