-- A trainer sees a trainee's machines, visits and plans under the trainer's own session, writes
-- the trainee's plans and adds machines for them, once the trainee has taken the trainer's code.
-- The trainee's profile, measures, groups and links to friends stay the trainee's.
create table public.coaching (
    trainee_id uuid        not null references auth.users (id) on delete cascade,
    trainer_id uuid        not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (trainee_id, trainer_id),
    check (trainee_id <> trainer_id)
);
create index coaching_trainer_id_idx on public.coaching (trainer_id);

-- Only the definer functions below read and write invites.
create table public.coaching_invite (
    code       text        primary key default public.new_invite_code(),
    trainer_id uuid        not null references auth.users (id) on delete cascade,
    expires_at timestamptz not null default now() + interval '24 hours'
);
create index coaching_invite_trainer_id_idx on public.coaching_invite (trainer_id);

alter table public.coaching enable row level security;
alter table public.coaching_invite enable row level security;

create policy coaching_select_party on public.coaching
    for select using ((select auth.uid()) in (trainee_id, trainer_id));
grant select on public.coaching to authenticated;

-- Definer rights read coaching past its own policy; the trainer policies call this per row.
create or replace function public.coaches(other uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.coaching c
        where c.trainer_id = (select auth.uid()) and c.trainee_id = other
    );
$$;

-- One live code per trainer, as offer_guardianship keeps one per parent.
create or replace function public.offer_coaching() returns text
language plpgsql security definer set search_path = '' as $$
declare
    offered text;
begin
    delete from public.coaching_invite as i
    where i.trainer_id = (select auth.uid()) or i.expires_at < now();
    insert into public.coaching_invite (trainer_id) values ((select auth.uid()))
    returning coaching_invite.code into offered;
    return offered;
end;
$$;

-- Read as accept_guardian reads a code: it works once.
create or replace function public.accept_trainer(code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare
    typed   text := upper(regexp_replace(code, '^\s+|\s+$', '', 'g'));
    trainer uuid;
begin
    delete from public.coaching_invite as i
    where i.code = typed and i.expires_at > now()
    returning i.trainer_id into trainer;
    if trainer is null then
        -- PostgREST answers a PTxyz code with HTTP status xyz.
        raise exception 'unknown trainer code' using errcode = 'PT404';
    end if;
    if trainer = (select auth.uid()) then
        raise exception 'your own trainer code' using errcode = 'P0001';
    end if;
    insert into public.coaching (trainee_id, trainer_id)
    values ((select auth.uid()), trainer)
    on conflict (trainee_id, trainer_id) do nothing;
    return trainer;
end;
$$;

create or replace function public.end_coaching(trainee uuid, trainer uuid) returns void
language sql security definer set search_path = '' as $$
    delete from public.coaching as c
    where c.trainee_id = end_coaching.trainee
      and c.trainer_id = end_coaching.trainer
      and (select auth.uid()) in (end_coaching.trainee, end_coaching.trainer);
$$;

-- Apart from my_family, which builds released before trainers read as children and guardians.
create or replace function public.my_coaching()
returns table (
    user_id      uuid,
    relation     text,
    display_name text,
    avatar_photo uuid,
    picture_url  text
)
language sql stable security definer set search_path = '' as $$
    select c.trainee_id, 'trainee', public.member_display_name(c.trainee_id),
           public.member_avatar_photo(c.trainee_id), public.google_picture(c.trainee_id)
    from public.coaching c
    where c.trainer_id = (select auth.uid())
    union all
    select c.trainer_id, 'trainer', public.member_display_name(c.trainer_id),
           public.member_avatar_photo(c.trainer_id), public.google_picture(c.trainer_id)
    from public.coaching c
    where c.trainee_id = (select auth.uid());
$$;

revoke execute on function
    public.coaches(uuid), public.offer_coaching(), public.accept_trainer(text),
    public.end_coaching(uuid, uuid), public.my_coaching()
    from public, anon;
grant execute on function
    public.coaches(uuid), public.offer_coaching(), public.accept_trainer(text),
    public.end_coaching(uuid, uuid), public.my_coaching()
    to authenticated;

-- A trainer's update of a trainee's row may only repeat it, as a sync pass retrying an upsert
-- does, or bring back a deleted tag the trainer uses again. The update policies below need to let
-- the retry through, and cannot tell a machine the trainer added from the trainee's own.
create or replace function public.trainer_keeps_row() returns trigger
language plpgsql set search_path = '' as $$
begin
    if old.user_id is distinct from (select auth.uid())
       and not public.guards(old.user_id)
       and public.coaches(old.user_id)
       and (to_jsonb(new) - 'updated_at' - 'deleted' <> to_jsonb(old) - 'updated_at' - 'deleted'
            or (new.deleted and not old.deleted)) then
        raise exception 'a trainer only adds rows' using errcode = 'P0001';
    end if;
    return new;
end;
$$;
revoke execute on function public.trainer_keeps_row() from public, anon, authenticated;

-- A trainer reads deleted rows too, since the trainer's device syncs them.
drop policy if exists machine_select_own_guarded_or_group on public.machine;
create policy machine_select_own_guarded_coached_or_group on public.machine
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
-- A trainer adds machines for a trainee. The app opens none of the trainee's machines for changes,
-- but a sync pass writes a new machine with an upsert, which needs the update policy too.
drop policy if exists machine_insert_own_or_guarded on public.machine;
create policy machine_insert_own_guarded_or_coached on public.machine
    for insert with check (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
    );
drop policy if exists machine_update_own_or_guarded on public.machine;
create policy machine_update_own_guarded_or_coached on public.machine
    for update
    using ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
    with check (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
    );

drop policy if exists visit_select_own_guarded_or_group on public.visit;
create policy visit_select_own_guarded_coached_or_group on public.visit
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );

drop policy if exists workout_set_select_own_guarded_or_group on public.workout_set;
create policy workout_set_select_own_guarded_coached_or_group on public.workout_set
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );

drop policy if exists photo_select_own_guarded_or_group on public.photo;
create policy photo_select_own_guarded_coached_or_group on public.photo
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );

-- A machine the trainer adds may carry tags, written as 0027 writes them.
drop policy if exists tag_select_own_guarded_or_group on public.tag;
create policy tag_select_own_guarded_coached_or_group on public.tag
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists tag_insert_own_or_guarded on public.tag;
create policy tag_insert_own_guarded_or_coached on public.tag
    for insert with check (
        ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
        and id = public.tag_content_id(user_id::text || '/' || name)
    );
drop policy if exists tag_update_own_or_guarded on public.tag;
create policy tag_update_own_guarded_or_coached on public.tag
    for update
    using ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
    with check (
        ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
        and id = public.tag_content_id(user_id::text || '/' || name)
    );

drop policy if exists machine_tag_select_own_guarded_or_group on public.machine_tag;
create policy machine_tag_select_own_guarded_coached_or_group on public.machine_tag
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists machine_tag_insert_own_or_guarded on public.machine_tag;
create policy machine_tag_insert_own_guarded_or_coached on public.machine_tag
    for insert with check (
        ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = machine_tag.user_id)
        and exists (select 1 from public.tag t
                    where t.id = tag_id and t.user_id = machine_tag.user_id
                      and machine_tag.id
                          = public.tag_content_id(machine_id::text || '/' || t.name))
    );
drop policy if exists machine_tag_update_own_or_guarded on public.machine_tag;
create policy machine_tag_update_own_guarded_or_coached on public.machine_tag
    for update
    using ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
    with check (
        ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = machine_tag.user_id)
        and exists (select 1 from public.tag t
                    where t.id = tag_id and t.user_id = machine_tag.user_id
                      and machine_tag.id
                          = public.tag_content_id(machine_id::text || '/' || t.name))
    );

create trigger machine_trainer_keeps_row before update on public.machine
    for each row execute function public.trainer_keeps_row();
create trigger tag_trainer_keeps_row before update on public.tag
    for each row execute function public.trainer_keeps_row();
create trigger machine_tag_trainer_keeps_row before update on public.machine_tag
    for each row execute function public.trainer_keeps_row();

drop policy if exists workout_plan_own_or_guarded on public.workout_plan;
create policy workout_plan_own_guarded_or_coached on public.workout_plan
    for all
    using ((select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id))
    with check (
        (select auth.uid()) = user_id or public.guards(user_id) or public.coaches(user_id)
    );

drop policy if exists photos_select_own_guarded_or_group on storage.objects;
create policy photos_select_own_guarded_coached_or_group on storage.objects
    for select to authenticated
    using (
        bucket_id = 'photos' and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or (storage.foldername(name))[1] in (
                select g.child_id::text from public.guardian g
                where g.guardian_id = (select auth.uid())
            )
            or (storage.foldername(name))[1] in (
                select c.trainee_id::text from public.coaching c
                where c.trainer_id = (select auth.uid())
            )
            or public.shares_group_with(((storage.foldername(name))[1])::uuid)
        )
    );
