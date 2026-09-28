-- A photo may reach the server before its machine's latest row, so machine_id is not a foreign
-- key, as on machine_link.
create table public.photo (
    id         uuid        primary key,
    user_id    uuid        not null references auth.users (id) on delete cascade,
    machine_id uuid        not null,
    taken_at   timestamptz not null,
    updated_at timestamptz not null default now(),
    deleted    boolean     not null default false
);
create index photo_user_id_idx on public.photo (user_id);
create index photo_updated_at_idx on public.photo (updated_at);
create index photo_machine_id_idx on public.photo (machine_id);

alter table public.photo enable row level security;
create policy photo_select_own_or_group on public.photo
    for select using (
        (select auth.uid()) = user_id or (not deleted and public.shares_group_with(user_id))
    );
create policy photo_insert_own on public.photo
    for insert with check ((select auth.uid()) = user_id);
create policy photo_update_own on public.photo
    for update using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);
grant select, insert, update on public.photo to authenticated;

-- The bytes: <user_id>/<photo_id> in a private bucket. Android shrinks a photo well below the
-- limit before it uploads.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('photos', 'photos', false, 5242880, array['image/jpeg'])
on conflict (id) do nothing;

-- The insert policy keeps every first folder a user id, which the group policy casts.
create policy photos_insert_own on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text
    );
create policy photos_update_own on storage.objects
    for update to authenticated
    using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text)
    with check (
        bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text
    );
create policy photos_delete_own on storage.objects
    for delete to authenticated
    using (bucket_id = 'photos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy photos_select_own_or_group on storage.objects
    for select to authenticated
    using (
        bucket_id = 'photos' and (
            (storage.foldername(name))[1] = (select auth.uid())::text
            or public.shares_group_with(((storage.foldername(name))[1])::uuid)
        )
    );
