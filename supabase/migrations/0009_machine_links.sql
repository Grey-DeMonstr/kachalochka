create table public.machine_link (
    id                uuid        primary key,
    user_id           uuid        not null references auth.users (id) on delete cascade,
    machine_id        uuid        not null,
    linked_machine_id uuid        not null,
    updated_at        timestamptz not null default now(),
    deleted           boolean     not null default false,
    check (machine_id <> linked_machine_id)
);
create index machine_link_user_id_idx on public.machine_link (user_id);
create index machine_link_updated_at_idx on public.machine_link (updated_at);
create index machine_link_machine_id_idx on public.machine_link (machine_id);
create index machine_link_linked_machine_id_idx on public.machine_link (linked_machine_id);

alter table public.machine_link enable row level security;
create policy machine_link_select_own_or_group on public.machine_link
    for select using (
        (select auth.uid()) = user_id or (not deleted and public.shares_group_with(user_id))
    );
create policy machine_link_insert_own on public.machine_link
    for insert with check ((select auth.uid()) = user_id);
create policy machine_link_update_own on public.machine_link
    for update using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);
grant select, insert, update on public.machine_link to authenticated;

-- 1.0.2 linked by key: a copy stores the original's coalesce(link_id, id), and unlinking either
-- side writes a random key. Every other user's machine of a key shared across users links to one
-- representative: the machine whose id is the key while it is live, else the oldest.
with keyed as (
    select id, user_id, updated_at, coalesce(link_id, id) as link_key
    from public.machine where not deleted
),
shared as (
    select link_key from keyed group by link_key having count(distinct user_id) > 1
),
representative as (
    select distinct on (k.link_key) k.link_key, k.id, k.user_id
    from keyed k join shared s on s.link_key = k.link_key
    order by k.link_key, (k.id = k.link_key) desc, k.updated_at, k.id
)
insert into public.machine_link (id, user_id, machine_id, linked_machine_id)
select gen_random_uuid(), k.user_id, k.id, r.id
from keyed k join representative r on r.link_key = k.link_key
where k.user_id <> r.user_id;

-- Friends' links into the caller's own machine: only the definer may touch another user's row.
create or replace function public.break_machine_links(target uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    update public.machine_link set deleted = true, updated_at = now()
    where linked_machine_id = target and not deleted
      and exists (select 1 from public.machine m
                  where m.id = target and m.user_id = (select auth.uid()));
end;
$$;

-- A merged duplicate's friends' links move to the machine that stays, which may not have reached
-- the server yet.
create or replace function public.repoint_machine_links(removed uuid, kept uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if not exists (select 1 from public.machine m
                   where m.id = removed and m.user_id = (select auth.uid()))
       or exists (select 1 from public.machine m
                  where m.id = kept and m.user_id <> (select auth.uid())) then
        raise exception 'not your machines' using errcode = 'P0001';
    end if;
    update public.machine_link set linked_machine_id = kept, updated_at = now()
    where linked_machine_id = removed and not deleted;
end;
$$;

revoke execute on function
    public.break_machine_links(uuid), public.repoint_machine_links(uuid, uuid)
    from public, anon;
grant execute on function
    public.break_machine_links(uuid), public.repoint_machine_links(uuid, uuid)
    to authenticated;
