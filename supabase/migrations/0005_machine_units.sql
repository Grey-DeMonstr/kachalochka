alter table public.machine add column unit_label text not null default '';

alter table public.machine drop constraint machine_unit_check;
alter table public.machine add constraint machine_unit_check
    check (unit in ('kg', 'lb', 'custom'));

-- Clients before 1.0.2 may still write counterweight; the app reads it as total.
update public.machine
set weight_mode = 'total', updated_at = now()
where weight_mode = 'counterweight';
