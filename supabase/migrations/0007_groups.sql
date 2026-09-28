alter table public.machine add column link_id uuid;
create index machine_link_id_idx on public.machine (link_id);

create extension if not exists pgcrypto with schema extensions;

create or replace function public.new_invite_code() returns text
language sql volatile set search_path = '' as $$
    select string_agg(
        substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + get_byte(bytes, i) % 32, 1), ''
    )
    from extensions.gen_random_bytes(8) as bytes, generate_series(0, 7) as i;
$$;

create table public.friend_group (
    id          uuid        primary key default gen_random_uuid(),
    name        text        not null check (length(trim(name)) between 1 and 40),
    owner_id    uuid        not null references auth.users (id) on delete cascade,
    invite_code text        not null unique default public.new_invite_code(),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted     boolean     not null default false
);

create table public.group_member (
    group_id     uuid        not null references public.friend_group (id) on delete cascade,
    user_id      uuid        not null references auth.users (id) on delete cascade,
    display_name text        not null,
    joined_at    timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted      boolean     not null default false,
    primary key (group_id, user_id)
);
create index group_member_user_id_idx on public.group_member (user_id);

-- Definer rights read group_member past its own policy, which calls this.
create or replace function public.is_group_member(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1
        from public.group_member m
        join public.friend_group g on g.id = m.group_id
        where m.group_id = target and m.user_id = (select auth.uid())
          and not m.deleted and not g.deleted
    );
$$;

create or replace function public.shares_group_with(other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1
        from public.group_member mine
        join public.group_member theirs on theirs.group_id = mine.group_id
        join public.friend_group g on g.id = mine.group_id
        where mine.user_id = (select auth.uid()) and theirs.user_id = other
          and not mine.deleted and not theirs.deleted and not g.deleted
    );
$$;

-- Google's name lands under either claim, as SupabaseSessions also allows for.
create or replace function public.my_display_name() returns text
language sql stable security definer set search_path = '' as $$
    select left(coalesce(
        nullif(trim(raw_user_meta_data ->> 'full_name'), ''),
        nullif(trim(raw_user_meta_data ->> 'name'), ''),
        'Участник'
    ), 40)
    from auth.users where id = (select auth.uid());
$$;

create or replace function public.create_group(group_name text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    created uuid;
begin
    insert into public.friend_group (name, owner_id)
    values (trim(group_name), (select auth.uid()))
    returning id into created;
    insert into public.group_member (group_id, user_id, display_name)
    values (created, (select auth.uid()), public.my_display_name());
    return created;
end;
$$;

-- The code is read as inviteCodeOf reads it: any surrounding whitespace, any case.
create or replace function public.join_group(code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    target uuid;
begin
    select id into target from public.friend_group
    where invite_code = upper(regexp_replace(code, '^\s+|\s+$', '', 'g')) and not deleted;
    if target is null then
        -- PostgREST answers a PTxyz code with HTTP status xyz.
        raise exception 'unknown invite code' using errcode = 'PT404';
    end if;
    insert into public.group_member (group_id, user_id, display_name)
    values (target, (select auth.uid()), public.my_display_name())
    on conflict (group_id, user_id) do update
        set deleted = false, updated_at = now(), display_name = excluded.display_name;
    return target;
end;
$$;

create or replace function public.leave_group(target uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if exists (select 1 from public.friend_group
               where id = target and owner_id = (select auth.uid())) then
        raise exception 'the owner deletes the group instead' using errcode = 'P0001';
    end if;
    update public.group_member set deleted = true, updated_at = now()
    where group_id = target and user_id = (select auth.uid());
end;
$$;

alter table public.friend_group enable row level security;
alter table public.group_member enable row level security;

create policy friend_group_select_member on public.friend_group
    for select using (owner_id = (select auth.uid()) or public.is_group_member(id));
create policy friend_group_update_owner on public.friend_group
    for update using (owner_id = (select auth.uid()) and not deleted)
    with check (owner_id = (select auth.uid()));
create policy group_member_select_member on public.group_member
    for select using (public.is_group_member(group_id));

grant select on public.friend_group to authenticated;
grant update (name, deleted, updated_at) on public.friend_group to authenticated;
grant select on public.group_member to authenticated;

drop policy if exists machine_select_own on public.machine;
create policy machine_select_own_or_group on public.machine
    for select using (
        (select auth.uid()) = user_id or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists visit_select_own on public.visit;
create policy visit_select_own_or_group on public.visit
    for select using (
        (select auth.uid()) = user_id or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists workout_set_select_own on public.workout_set;
create policy workout_set_select_own_or_group on public.workout_set
    for select using (
        (select auth.uid()) = user_id or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists profile_select_own on public.profile;
create policy profile_select_own_or_group on public.profile
    for select using (
        (select auth.uid()) = user_id or (not deleted and public.shares_group_with(user_id))
    );

-- Friends can read each other's visit and machine ids; a set may point only at its owner's own.
drop policy if exists workout_set_insert_own on public.workout_set;
create policy workout_set_insert_own on public.workout_set
    for insert with check (
        (select auth.uid()) = user_id
        and exists (select 1 from public.visit v
                    where v.id = visit_id and v.user_id = (select auth.uid()))
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = (select auth.uid()))
    );
drop policy if exists workout_set_update_own on public.workout_set;
create policy workout_set_update_own on public.workout_set
    for update using ((select auth.uid()) = user_id)
    with check (
        (select auth.uid()) = user_id
        and exists (select 1 from public.visit v
                    where v.id = visit_id and v.user_id = (select auth.uid()))
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = (select auth.uid()))
    );

revoke execute on function
    public.new_invite_code(), public.is_group_member(uuid), public.shares_group_with(uuid),
    public.my_display_name(), public.create_group(text), public.join_group(text),
    public.leave_group(uuid)
    from public, anon;
-- Supabase's default privileges grant every new function to authenticated as well; these two
-- run only inside the definer functions and the column default.
revoke execute on function public.new_invite_code(), public.my_display_name()
    from authenticated;
grant execute on function
    public.is_group_member(uuid), public.shares_group_with(uuid), public.create_group(text),
    public.join_group(text), public.leave_group(uuid)
    to authenticated;
