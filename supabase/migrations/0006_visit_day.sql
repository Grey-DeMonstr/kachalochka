alter table public.visit add column day date;
create index visit_user_id_day_idx on public.visit (user_id, day);

alter table public.workout_set add column position integer not null default 0;
