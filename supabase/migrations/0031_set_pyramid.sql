-- True on the set that starts a pyramid; the pyramid holds the sets after it up to the next start.
alter table public.workout_set
    add column pyramid boolean not null default false;
