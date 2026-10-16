-- The viewer's group mates' live machines, tags, links and photos, with the members that make them
-- mates, in one answer instead of one request per table. Invoker's rights, so row-level security
-- decides what it reads; a managed child's viewer is read under its guardian's session.
create or replace function public.group_gym(viewer uuid)
returns jsonb
language sql stable security invoker set search_path = '' as $$
    with members as (
        select m.*
        from public.group_member m
        where not m.deleted
          and m.group_id in (select v.group_id from public.group_member v
                             where v.user_id = viewer and not v.deleted)
    ),
    mates as (
        select distinct user_id from members where user_id <> viewer
    )
    select jsonb_build_object(
        'members', coalesce((select jsonb_agg(to_jsonb(r)) from members r), '[]'::jsonb),
        'machines', coalesce((select jsonb_agg(to_jsonb(r)) from public.machine r
                              where not r.deleted and r.user_id in (select user_id from mates)),
                             '[]'::jsonb),
        'machine_tags', coalesce((select jsonb_agg(to_jsonb(r)) from public.machine_tag r
                                  where not r.deleted
                                    and r.user_id in (select user_id from mates)),
                                 '[]'::jsonb),
        'tags', coalesce((select jsonb_agg(to_jsonb(r)) from public.tag r
                          where not r.deleted and r.user_id in (select user_id from mates)),
                         '[]'::jsonb),
        'links', coalesce((select jsonb_agg(to_jsonb(r)) from public.machine_link r
                           where not r.deleted and r.user_id in (select user_id from mates)),
                          '[]'::jsonb),
        'photos', coalesce((select jsonb_agg(to_jsonb(r)) from public.photo r
                            where not r.deleted and r.user_id in (select user_id from mates)),
                           '[]'::jsonb)
    );
$$;

revoke execute on function public.group_gym(uuid) from public, anon;
grant execute on function public.group_gym(uuid) to authenticated;
