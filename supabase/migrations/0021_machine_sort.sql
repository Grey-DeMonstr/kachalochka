-- How the account's machine lists are ordered: recent, name or frequent.
alter table public.profile
    add column machine_sort text not null default 'recent';

-- machine_peaks also counts the visits with a live set on each machine, for the frequent order.
-- A function's columns cannot change in place, so it is dropped and created again.
drop function if exists public.machine_peaks(uuid[]);

create function public.machine_peaks(owners uuid[])
returns table (
    machine_id    uuid,
    heaviest      double precision,
    heaviest_reps integer,
    lightest      double precision,
    lightest_reps integer,
    last_at       timestamptz,
    visits        integer
)
language sql stable security invoker set search_path = '' as $$
    select s.machine_id,
           max(s.weight),
           (array_agg(s.reps order by s.weight desc, s.reps desc))[1],
           min(s.weight),
           (array_agg(s.reps order by s.weight asc, s.reps desc))[1],
           max(s.recorded_at),
           count(distinct s.visit_id)::integer
    from public.workout_set s
    where not s.deleted and s.user_id = any(owners)
    group by s.machine_id;
$$;

revoke execute on function public.machine_peaks(uuid[]) from public, anon;
grant execute on function public.machine_peaks(uuid[]) to authenticated;
