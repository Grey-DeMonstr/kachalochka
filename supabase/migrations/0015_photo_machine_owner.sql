-- Group mates can read each other's machine ids, so a photo row must also name one of the
-- writer's own machines, as a set does. Machines are pushed before photos.
drop policy if exists photo_insert_own on public.photo;
create policy photo_insert_own on public.photo
    for insert with check (
        (select auth.uid()) = user_id
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = (select auth.uid()))
    );

drop policy if exists photo_update_own on public.photo;
create policy photo_update_own on public.photo
    for update using ((select auth.uid()) = user_id)
    with check (
        (select auth.uid()) = user_id
        and exists (select 1 from public.machine m
                    where m.id = machine_id and m.user_id = (select auth.uid()))
    );
