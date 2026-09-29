-- A JSON array of the tag names, as friend_colors keeps a JSON object.
alter table public.machine
    add column tags text not null default '[]';

alter table public.profile
    add column group_by_tag boolean not null default false;
