create table public.machine (
    id                uuid             primary key,
    user_id           uuid             not null references auth.users (id) on delete cascade,
    name              text             not null,
    setup_note        text             not null default '',
    weight_mode       text             not null
        check (weight_mode in ('total', 'per_side', 'counterweight')),
    platform_weight   double precision not null default 0 check (platform_weight >= 0),
    platform_included boolean          not null default false,
    unit              text             not null check (unit in ('kg', 'lb')),
    weight_step       double precision not null check (weight_step > 0),
    updated_at        timestamptz      not null default now(),
    deleted           boolean          not null default false
);

create table public.visit (
    id         uuid        primary key,
    user_id    uuid        not null references auth.users (id) on delete cascade,
    started_at timestamptz not null,
    ended_at   timestamptz,
    updated_at timestamptz not null default now(),
    deleted    boolean     not null default false
);

create table public.workout_set (
    id          uuid             primary key,
    user_id     uuid             not null references auth.users (id) on delete cascade,
    visit_id    uuid             not null references public.visit (id) on delete cascade,
    machine_id  uuid             not null references public.machine (id) on delete cascade,
    weight      double precision not null check (weight >= 0),
    reps        integer          not null check (reps >= 1),
    recorded_at timestamptz      not null,
    updated_at  timestamptz      not null default now(),
    deleted     boolean          not null default false
);

create index machine_user_id_idx on public.machine (user_id);
create index machine_updated_at_idx on public.machine (updated_at);
create index visit_user_id_idx on public.visit (user_id);
create index visit_updated_at_idx on public.visit (updated_at);
create index workout_set_user_id_idx on public.workout_set (user_id);
create index workout_set_updated_at_idx on public.workout_set (updated_at);
create index workout_set_visit_id_idx on public.workout_set (visit_id);
create index workout_set_machine_id_idx on public.workout_set (machine_id);

alter table public.machine enable row level security;
alter table public.visit enable row level security;
alter table public.workout_set enable row level security;

create policy machine_select_own on public.machine
    for select using (auth.uid() = user_id);
create policy machine_insert_own on public.machine
    for insert with check (auth.uid() = user_id);
create policy machine_update_own on public.machine
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy visit_select_own on public.visit
    for select using (auth.uid() = user_id);
create policy visit_insert_own on public.visit
    for insert with check (auth.uid() = user_id);
create policy visit_update_own on public.visit
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy workout_set_select_own on public.workout_set
    for select using (auth.uid() = user_id);
create policy workout_set_insert_own on public.workout_set
    for insert with check (auth.uid() = user_id);
create policy workout_set_update_own on public.workout_set
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
