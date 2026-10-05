-- A parent records a child's gym rows under the parent's own session once the child has taken
-- the parent's code: Family Link keeps the child's Google account off the parent's phone.
create table public.guardian (
    child_id    uuid        not null references auth.users (id) on delete cascade,
    guardian_id uuid        not null references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (child_id, guardian_id),
    check (child_id <> guardian_id)
);
create index guardian_guardian_id_idx on public.guardian (guardian_id);

-- Only the definer functions below read and write invites.
create table public.guardian_invite (
    code        text        primary key default public.new_invite_code(),
    guardian_id uuid        not null references auth.users (id) on delete cascade,
    expires_at  timestamptz not null default now() + interval '24 hours'
);
create index guardian_invite_guardian_id_idx on public.guardian_invite (guardian_id);

alter table public.guardian enable row level security;
alter table public.guardian_invite enable row level security;

create policy guardian_select_party on public.guardian
    for select using ((select auth.uid()) in (child_id, guardian_id));
grant select on public.guardian to authenticated;

-- Definer rights read guardian past its own policy; the guardian write policies call this per row.
create or replace function public.guards(other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.guardian g
        where g.guardian_id = (select auth.uid()) and g.child_id = other
    );
$$;

-- One live code per parent: a new one retires the last, and expired ones go with it.
create or replace function public.offer_guardianship() returns text
language plpgsql security definer set search_path = '' as $$
declare
    offered text;
begin
    delete from public.guardian_invite as i
    where i.guardian_id = (select auth.uid()) or i.expires_at < now();
    insert into public.guardian_invite (guardian_id) values ((select auth.uid()))
    returning guardian_invite.code into offered;
    return offered;
end;
$$;

-- The code is read as join_group reads one. A code works once: a second device of the child
-- asks the parent for a new one.
create or replace function public.accept_guardian(code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    typed  text := upper(regexp_replace(code, '^\s+|\s+$', '', 'g'));
    parent uuid;
begin
    delete from public.guardian_invite as i
    where i.code = typed and i.expires_at > now()
    returning i.guardian_id into parent;
    if parent is null then
        -- PostgREST answers a PTxyz code with HTTP status xyz.
        raise exception 'unknown guardian code' using errcode = 'PT404';
    end if;
    if parent = (select auth.uid()) then
        raise exception 'your own guardian code' using errcode = 'P0001';
    end if;
    insert into public.guardian (child_id, guardian_id)
    values ((select auth.uid()), parent)
    on conflict (child_id, guardian_id) do nothing;
    return parent;
end;
$$;

create or replace function public.end_guardianship(child uuid, guardian uuid) returns void
language sql security definer set search_path = '' as $$
    delete from public.guardian as g
    where g.child_id = end_guardianship.child
      and g.guardian_id = end_guardianship.guardian
      and (select auth.uid()) in (end_guardianship.child, end_guardianship.guardian);
$$;

-- Profiles stay private, so names and avatars come as group members see them.
create or replace function public.my_family()
returns table (
    user_id      uuid,
    relation     text,
    display_name text,
    avatar_photo uuid,
    picture_url  text
)
language sql stable security definer set search_path = '' as $$
    select g.child_id, 'child', public.member_display_name(g.child_id),
           public.member_avatar_photo(g.child_id), public.google_picture(g.child_id)
    from public.guardian g
    where g.guardian_id = (select auth.uid())
    union all
    select g.guardian_id, 'guardian', public.member_display_name(g.guardian_id),
           public.member_avatar_photo(g.guardian_id), public.google_picture(g.guardian_id)
    from public.guardian g
    where g.child_id = (select auth.uid());
$$;

revoke execute on function
    public.guards(uuid), public.offer_guardianship(), public.accept_guardian(text),
    public.end_guardianship(uuid, uuid), public.my_family()
    from public, anon;
grant execute on function
    public.guards(uuid), public.offer_guardianship(), public.accept_guardian(text),
    public.end_guardianship(uuid, uuid), public.my_family()
    to authenticated;
