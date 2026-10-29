-- Each set stores the machine's best result over the days before its own day: the weight
-- (the lightest on a counterweight machine), the reps at it, and how many sets equalled it.
-- A machine's first day stores 0, 0, 0; null means a client has not computed the row yet.
alter table public.workout_set
    add column prior_best_weight double precision,
    add column prior_best_reps   integer,
    add column prior_best_sets   integer;

with placed as (
    select s.id, s.machine_id, s.weight, s.reps,
           m.weight_mode = 'counterweight' as lighter_wins,
           coalesce(v.day, (v.recorded_at at time zone 'UTC')::date) as day
    from public.workout_set s
    join public.visit v on v.id = s.visit_id
    join public.machine m on m.id = s.machine_id
    where not s.deleted
),
day_best as (
    select distinct on (machine_id, day)
           machine_id, day, weight, reps, lighter_wins,
           count(*) over (partition by machine_id, day, weight, reps) as sets
    from placed
    order by machine_id, day,
             case when lighter_wins then weight else -weight end,
             reps desc
),
prior as (
    select machine_id, day, lighter_wins,
           max(array[case when lighter_wins then -weight else weight end,
                     reps::double precision, sets::double precision])
               over (partition by machine_id order by day
                     rows between unbounded preceding and 1 preceding) as best
    from day_best
)
update public.workout_set s
set prior_best_weight = coalesce(case when p.lighter_wins then -p.best[1] else p.best[1] end, 0),
    prior_best_reps   = coalesce(p.best[2]::integer, 0),
    prior_best_sets   = coalesce(p.best[3]::integer, 0)
from placed pl
join prior p on p.machine_id = pl.machine_id and p.day = pl.day
where s.id = pl.id;
