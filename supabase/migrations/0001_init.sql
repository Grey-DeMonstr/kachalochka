create table public.profile (
    id           uuid        primary key,
    user_id      uuid        not null references auth.users (id) on delete cascade,
    display_name text,
    updated_at   timestamptz not null default now(),
    deleted      boolean     not null default false
);

create index profile_user_id_idx on public.profile (user_id);
create index profile_updated_at_idx on public.profile (updated_at);

alter table public.profile enable row level security;

create policy profile_select_own on public.profile
    for select using (auth.uid() = user_id);

create policy profile_insert_own on public.profile
    for insert with check (auth.uid() = user_id);

create policy profile_update_own on public.profile
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
