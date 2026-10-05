-- A guardian (0023) reads and writes the gym rows, photos and plans of the children they guard
-- as the child's own device does; the child's profile, measures and groups stay the child's.
-- Deleted rows stay readable to the guardian, whose device syncs them.

drop policy if exists machine_select_own_or_group on public.machine;
create policy machine_select_own_guarded_or_group on public.machine
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists machine_insert_own on public.machine;
create policy machine_insert_own_or_guarded on public.machine
    for insert with check ((select auth.uid()) = user_id or public.guards(user_id));
drop policy if exists machine_update_own on public.machine;
create policy machine_update_own_or_guarded on public.machine
    for update using ((select auth.uid()) = user_id or public.guards(user_id))
    with check ((select auth.uid()) = user_id or public.guards(user_id));

drop policy if exists visit_select_own_or_group on public.visit;
create policy visit_select_own_guarded_or_group on public.visit
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists visit_insert_own on public.visit;
create policy visit_insert_own_or_guarded on public.visit
    for insert with check ((select auth.uid()) = user_id or public.guards(user_id));
drop policy if exists visit_update_own on public.visit;
create policy visit_update_own_or_guarded on public.visit
    for update using ((select auth.uid()) = user_id or public.guards(user_id))
    with check ((select auth.uid()) = user_id or public.guards(user_id));

drop policy if exists machine_link_select_own_or_group on public.machine_link;
create policy machine_link_select_own_guarded_or_group on public.machine_link
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
drop policy if exists machine_link_insert_own on public.machine_link;
create policy machine_link_insert_own_or_guarded on public.machine_link
    for insert with check ((select auth.uid()) = user_id or public.guards(user_id));
drop policy if exists machine_link_update_own on public.machine_link;
create policy machine_link_update_own_or_guarded on public.machine_link
    for update using ((select auth.uid()) = user_id or public.guards(user_id))
    with check ((select auth.uid()) = user_id or public.guards(user_id));

drop policy if exists workout_set_select_own_or_group on public.workout_set;
create policy workout_set_select_own_guarded_or_group on public.workout_set
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
-- A set points only at a visit and a machine of its own owner, whoever writes it.
drop policy if exists workout_set_insert_own on public.workout_set;
create policy workout_set_insert_own_or_guarded on public.workout_set
    for insert with check (
        ((select auth.uid()) = user_id or public.guards(user_id))
        and exists (select 1 from public.visit v
                    where v.id = visit_id and v.user_id = workout_set.user_id)
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = workout_set.user_id)
    );
drop policy if exists workout_set_update_own on public.workout_set;
create policy workout_set_update_own_or_guarded on public.workout_set
    for update using ((select auth.uid()) = user_id or public.guards(user_id))
    with check (
        ((select auth.uid()) = user_id or public.guards(user_id))
        and exists (select 1 from public.visit v
                    where v.id = visit_id and v.user_id = workout_set.user_id)
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = workout_set.user_id)
    );

drop policy if exists photo_select_own_or_group on public.photo;
create policy photo_select_own_guarded_or_group on public.photo
    for select using (
        (select auth.uid()) = user_id or public.guards(user_id)
        or (not deleted and public.shares_group_with(user_id))
    );
-- A photo names a machine or the profile of its own owner, whoever writes it.
drop policy if exists photo_insert_own on public.photo;
create policy photo_insert_own_or_guarded on public.photo
    for insert with check (
        ((select auth.uid()) = user_id or public.guards(user_id))
        and (exists (select 1 from public.machine m
                     where m.id = machine_id and m.user_id = photo.user_id)
             or exists (select 1 from public.profile p
                        where p.id = machine_id and p.user_id = photo.user_id))
    );
drop policy if exists photo_update_own on public.photo;
create policy photo_update_own_or_guarded on public.photo
    for update using ((select auth.uid()) = user_id or public.guards(user_id))
    with check (
        ((select auth.uid()) = user_id or public.guards(user_id))
        and (exists (select 1 from public.machine m
                     where m.id = machine_id and m.user_id = photo.user_id)
             or exists (select 1 from public.profile p
                        where p.id = machine_id and p.user_id = photo.user_id))
    );

drop policy if exists workout_plan_own on public.workout_plan;
create policy workout_plan_own_or_guarded on public.workout_plan
    for all using ((select auth.uid()) = user_id or public.guards(user_id))
    with check ((select auth.uid()) = user_id or public.guards(user_id));

-- The bytes: the first folder of an object's name is its owner's id.
drop policy if exists photos_insert_own on storage.objects;
create policy photos_insert_own_or_guarded on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'photos' and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or public.guards(((storage.foldername(name))[1])::uuid)
        )
    );
drop policy if exists photos_update_own on storage.objects;
create policy photos_update_own_or_guarded on storage.objects
    for update to authenticated
    using (
        bucket_id = 'photos' and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or public.guards(((storage.foldername(name))[1])::uuid)
        )
    )
    with check (
        bucket_id = 'photos' and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or public.guards(((storage.foldername(name))[1])::uuid)
        )
    );
drop policy if exists photos_delete_own on storage.objects;
create policy photos_delete_own_or_guarded on storage.objects
    for delete to authenticated
    using (
        bucket_id = 'photos' and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or public.guards(((storage.foldername(name))[1])::uuid)
        )
    );
drop policy if exists photos_select_own_or_group on storage.objects;
create policy photos_select_own_guarded_or_group on storage.objects
    for select to authenticated
    using (
        bucket_id = 'photos' and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or public.guards(((storage.foldername(name))[1])::uuid)
            or public.shares_group_with(((storage.foldername(name))[1])::uuid)
        )
    );

-- Friends' links into a machine of the caller or of a child the caller guards.
create or replace function public.break_machine_links(target uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
    update public.machine_link set deleted = true, updated_at = now()
    where linked_machine_id = target and not deleted
      and exists (select 1 from public.machine m
                  where m.id = target
                    and (m.user_id = (select auth.uid()) or public.guards(m.user_id)));
end;
$$;

-- Everyone else's live links into removed move to kept, which only removed's owner may own.
create or replace function public.repoint_machine_links(removed uuid, kept uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
    holder uuid;
begin
    select m.user_id into holder from public.machine m where m.id = removed;
    if holder is null
       or not (holder = (select auth.uid()) or public.guards(holder))
       or exists (select 1 from public.machine m
                  where m.id = kept and m.user_id <> holder) then
        raise exception 'not your machines' using errcode = 'P0001';
    end if;
    update public.machine_link set linked_machine_id = kept, updated_at = now()
    where linked_machine_id = removed and not deleted and user_id <> holder;
end;
$$;
