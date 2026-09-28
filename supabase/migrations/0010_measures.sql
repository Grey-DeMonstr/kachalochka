create table public.measure (
    id         uuid        primary key,
    user_id    uuid        not null references auth.users (id) on delete cascade,
    name       text        not null,
    unit       text        not null default '',
    kind       text,
    position   integer     not null default 0,
    updated_at timestamptz not null default now(),
    deleted    boolean     not null default false
);

-- A seeded measure may reach the server after its first value, so measure_id is not a foreign
-- key.
create table public.measurement (
    id         uuid             primary key,
    user_id    uuid             not null references auth.users (id) on delete cascade,
    measure_id uuid             not null,
    day        date             not null,
    value      double precision not null,
    updated_at timestamptz      not null default now(),
    deleted    boolean          not null default false
);

create index measure_user_id_idx on public.measure (user_id);
create index measure_updated_at_idx on public.measure (updated_at);
create index measurement_user_id_idx on public.measurement (user_id);
create index measurement_updated_at_idx on public.measurement (updated_at);

-- Body measures are private: only their owner reads or writes them.
alter table public.measure enable row level security;
alter table public.measurement enable row level security;

create policy measure_own on public.measure
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy measurement_own on public.measurement
    for all using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

grant select, insert, update on public.measure, public.measurement to authenticated;

-- Predefined measures are seeded at the epoch on every device; a seed must not undo an edit.
-- Every other update applies, older or not: the outbox wins, as on every table.
create or replace function public.keep_newer_measure() returns trigger
language plpgsql set search_path = '' as $$
begin
    if new.updated_at = to_timestamp(0) and new.updated_at < old.updated_at then
        return null;
    end if;
    return new;
end;
$$;

create trigger measure_keeps_newer
    before update on public.measure
    for each row execute function public.keep_newer_measure();
