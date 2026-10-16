-- The menstrual cycle in days; null is the usual 28.
alter table public.profile
    add column cycle_length integer check (cycle_length between 14 and 60);
