-- The reduction machinePeaks makes on the device, per machine of the given owners: its heaviest
-- and lightest weights with the most reps at each, and its last set. Invoker's rights, so
-- row-level security decides whose sets it reads; one row per machine stays under the row cap.
create or replace function public.machine_peaks(owners uuid[])
returns table (
    machine_id    uuid,
    heaviest      double precision,
    heaviest_reps integer,
    lightest      double precision,
    lightest_reps integer,
    last_at       timestamptz
)
language sql stable security invoker set search_path = '' as $$
    select s.machine_id,
           max(s.weight),
           (array_agg(s.reps order by s.weight desc, s.reps desc))[1],
           min(s.weight),
           (array_agg(s.reps order by s.weight asc, s.reps desc))[1],
           max(s.recorded_at)
    from public.workout_set s
    where not s.deleted and s.user_id = any(owners)
    group by s.machine_id;
$$;

revoke execute on function public.machine_peaks(uuid[]) from public, anon;
grant execute on function public.machine_peaks(uuid[]) to authenticated;
