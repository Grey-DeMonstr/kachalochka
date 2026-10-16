-- A pinned plan heads the list and stays when started.
alter table public.workout_plan add column pinned boolean not null default false;
