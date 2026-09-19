-- The Data API reaches tables only through privileges granted to its roles, and new tables in
-- this project get none by default. Clients soft-delete, so no role is granted delete.
grant select, insert, update on public.profile to authenticated;
grant select, insert, update on public.machine to authenticated;
grant select, insert, update on public.visit to authenticated;
grant select, insert, update on public.workout_set to authenticated;
