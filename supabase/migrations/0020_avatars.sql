-- The photo an owner chose to stand for them: a photo row whose machine_id is the owner's
-- profile id, so its bytes travel, sync and go with the account as every photo does.
alter table public.profile add column avatar_photo uuid;

drop policy if exists photo_insert_own on public.photo;
create policy photo_insert_own on public.photo
    for insert with check (
        (select auth.uid()) = user_id
        and (exists (select 1 from public.machine m
                     where m.id = machine_id and m.user_id = (select auth.uid()))
             or exists (select 1 from public.profile p
                        where p.id = machine_id and p.user_id = (select auth.uid())))
    );

drop policy if exists photo_update_own on public.photo;
create policy photo_update_own on public.photo
    for update using ((select auth.uid()) = user_id)
    with check (
        (select auth.uid()) = user_id
        and (exists (select 1 from public.machine m
                     where m.id = machine_id and m.user_id = (select auth.uid()))
             or exists (select 1 from public.profile p
                        where p.id = machine_id and p.user_id = (select auth.uid())))
    );

-- Friends read avatars with the member list, since profiles stay private.
alter table public.group_member
    add column avatar_photo uuid,
    add column picture_url  text;

-- Google's picture lands under either claim, as SupabaseSessions also allows for.
create or replace function public.google_picture(target uuid) returns text
language sql stable security definer set search_path = '' as $$
    select coalesce(
        nullif(trim(raw_user_meta_data ->> 'avatar_url'), ''),
        nullif(trim(raw_user_meta_data ->> 'picture'), '')
    )
    from auth.users where id = target;
$$;

-- The photo chosen in the owner's newest live profile, as member_display_name picks the nickname.
create or replace function public.member_avatar_photo(target uuid) returns uuid
language sql stable security definer set search_path = '' as $$
    select p.avatar_photo from public.profile p
    where p.user_id = target and not p.deleted
    order by p.updated_at desc, p.id desc limit 1;
$$;

-- However a member row is written, it carries its user's avatar as it stands.
create or replace function public.member_takes_avatar() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    new.avatar_photo := public.member_avatar_photo(new.user_id);
    new.picture_url := public.google_picture(new.user_id);
    return new;
end;
$$;

create trigger member_takes_avatar
    before insert or update on public.group_member
    for each row execute function public.member_takes_avatar();

-- A profile write now also touches member rows whose avatar changed.
create or replace function public.profile_renames_member() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    resolved text := public.member_display_name(new.user_id);
    chosen   uuid := public.member_avatar_photo(new.user_id);
begin
    update public.group_member
    set display_name = resolved, updated_at = now()
    where user_id = new.user_id
      and (display_name is distinct from resolved or avatar_photo is distinct from chosen);
    return null;
end;
$$;

update public.group_member set updated_at = updated_at;

revoke execute on function
    public.google_picture(uuid), public.member_avatar_photo(uuid), public.member_takes_avatar()
    from public, anon, authenticated;
