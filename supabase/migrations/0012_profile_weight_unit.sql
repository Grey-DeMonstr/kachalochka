alter table public.profile
    add column weight_unit text not null default 'kg'
        check (weight_unit in ('kg', 'lb', 'mixed'));
