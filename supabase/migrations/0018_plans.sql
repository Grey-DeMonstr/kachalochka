-- PLAN is a keyword in the device's SQLite, so the table mirrors workout_set's name.
create table public.workout_plan (
    id          uuid        primary key,
    user_id     uuid        not null references auth.users (id) on delete cascade,
    name        text        not null default '',
    -- A JSON array of machine ids, as machine.tags keeps its tag names.
    machine_ids text        not null default '[]',
    created_at  timestamptz not null,
    updated_at  timestamptz not null default now(),
    deleted     boolean     not null default false
);

create index workout_plan_user_id_idx on public.workout_plan (user_id);
create index workout_plan_updated_at_idx on public.workout_plan (updated_at);

-- Plans are private: only their owner reads or writes them.
alter table public.workout_plan enable row level security;

create policy workout_plan_own on public.workout_plan
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

grant select, insert, update on public.workout_plan to authenticated;

-- The machines a started plan added to the visit, a JSON array of machine ids.
alter table public.visit
    add column planned text not null default '[]';
