alter table public.profile
    add column friend_colors text             not null default '{}',
    add column sex           text             check (sex in ('male', 'female')),
    add column birth_year    integer          check (birth_year between 1900 and 2100),
    add column height_cm     double precision check (height_cm > 0);

create or replace function public.google_display_name(target uuid) returns text
language sql stable security definer set search_path = '' as $$
    select coalesce(
        nullif(trim(raw_user_meta_data ->> 'full_name'), ''),
        nullif(trim(raw_user_meta_data ->> 'name'), ''),
        'Участник'
    )
    from auth.users where id = target;
$$;

-- The nickname from the owner's newest live profile, else the Google name.
create or replace function public.member_display_name(target uuid) returns text
language sql stable security definer set search_path = '' as $$
    select left(coalesce(
        (select nullif(trim(p.display_name), '') from public.profile p
         where p.user_id = target and not p.deleted
         order by p.updated_at desc, p.id desc limit 1),
        public.google_display_name(target)
    ), 40);
$$;

create or replace function public.my_display_name() returns text
language sql stable security definer set search_path = '' as $$
    select public.member_display_name((select auth.uid()));
$$;

-- Clients may only read group_member, so the rename runs with the owner's rights.
create or replace function public.profile_renames_member() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    resolved text := public.member_display_name(new.user_id);
begin
    update public.group_member
    set display_name = resolved, updated_at = now()
    where user_id = new.user_id
      and display_name is distinct from resolved;
    return null;
end;
$$;

create trigger profile_renames_member
    after insert or update on public.profile
    for each row execute function public.profile_renames_member();

revoke execute on function
    public.google_display_name(uuid), public.member_display_name(uuid),
    public.profile_renames_member()
    from public, anon, authenticated;
