-- Tags as rows: one per owner and name, and one link per machine and tag. Ids are UUID v5 of
-- the content in one namespace, as the clients make them (TagWire.kt), so the rows a device
-- converts from its own copy of machine.tags are these rows.
create extension if not exists "uuid-ossp" with schema extensions;

-- The keys must equal the clients' to give their ids, so a locale whose lower() leaves Cyrillic
-- as it is stops the migration before it writes a row.
do $$
begin
    if lower('НОГИ') <> 'ноги' then
        raise exception 'lower() does not fold Cyrillic in this database';
    end if;
end;
$$;

create table public.tag (
    id         uuid        primary key,
    user_id    uuid        not null references auth.users (id) on delete cascade,
    name       text        not null,
    updated_at timestamptz not null default now(),
    deleted    boolean     not null default false
);
create index tag_user_id_idx on public.tag (user_id);
create index tag_updated_at_idx on public.tag (updated_at);

-- Neither id is a foreign key: a link may reach the server before its machine or its tag.
create table public.machine_tag (
    id         uuid        primary key,
    user_id    uuid        not null references auth.users (id) on delete cascade,
    machine_id uuid        not null,
    tag_id     uuid        not null,
    updated_at timestamptz not null default now(),
    deleted    boolean     not null default false
);
create index machine_tag_user_id_idx on public.machine_tag (user_id);
create index machine_tag_updated_at_idx on public.machine_tag (updated_at);
create index machine_tag_machine_id_idx on public.machine_tag (machine_id);
create index machine_tag_tag_id_idx on public.machine_tag (tag_id);

-- Read and written as machine_link is (0009, 0024).
alter table public.tag enable row level security;
create policy tag_select_own_guarded_or_group on public.tag
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
create policy tag_insert_own_or_guarded on public.tag
    for insert with check ((select auth.uid()) = user_id or public.guards(user_id));
create policy tag_update_own_or_guarded on public.tag
    for update using ((select auth.uid()) = user_id or public.guards(user_id))
    with check ((select auth.uid()) = user_id or public.guards(user_id));
grant select, insert, update on public.tag to authenticated;

alter table public.machine_tag enable row level security;
create policy machine_tag_select_own_guarded_or_group on public.machine_tag
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
create policy machine_tag_insert_own_or_guarded on public.machine_tag
    for insert with check ((select auth.uid()) = user_id or public.guards(user_id));
create policy machine_tag_update_own_or_guarded on public.machine_tag
    for update using ((select auth.uid()) = user_id or public.guards(user_id))
    with check ((select auth.uid()) = user_id or public.guards(user_id));
grant select, insert, update on public.machine_tag to authenticated;

create trigger tag_user_id_stays before update on public.tag
    for each row execute function public.user_id_stays();
create trigger machine_tag_user_id_stays before update on public.machine_tag
    for each row execute function public.user_id_stays();

-- The tag keys of one machine.tags text, as tagKey makes them: trimmed, inner spaces joined to
-- one, lower case. Text that is not a JSON array of strings, or an entry that is not a string,
-- gives no tag rather than stopping the migration.
create function public.tag_keys_of(tags text) returns setof text
language plpgsql set search_path = '' as $$
begin
    return query
        select distinct keyed.name
        from (
            select lower(btrim(regexp_replace(entry #>> '{}', '[[:space:]]+', ' ', 'g'))) as name
            from jsonb_array_elements(tags::jsonb) as entry
            where jsonb_typeof(entry) = 'string'
        ) keyed
        where keyed.name <> '';
exception when others then
    return;
end;
$$;

-- Every machine's tags, deleted machines' too. A tag only deleted machines hold is deleted.
create temporary table converted_tag as
select m.id as machine_id, m.user_id, m.deleted, k.name
from public.machine m
cross join lateral public.tag_keys_of(m.tags) as k(name);

insert into public.tag (id, user_id, name, updated_at, deleted)
select extensions.uuid_generate_v5(
           '6f1c2a4e-8d3b-4c7a-9e51-2b7d0f3a9c84'::uuid, user_id::text || '/' || name),
       user_id, name, now(), bool_and(deleted)
from converted_tag
group by user_id, name
on conflict (id) do nothing;

insert into public.machine_tag (id, user_id, machine_id, tag_id, updated_at, deleted)
select extensions.uuid_generate_v5(
           '6f1c2a4e-8d3b-4c7a-9e51-2b7d0f3a9c84'::uuid, machine_id::text || '/' || name),
       user_id, machine_id,
       extensions.uuid_generate_v5(
           '6f1c2a4e-8d3b-4c7a-9e51-2b7d0f3a9c84'::uuid, user_id::text || '/' || name),
       now(), deleted
from converted_tag
on conflict (id) do nothing;

drop table converted_tag;
drop function public.tag_keys_of(text);
