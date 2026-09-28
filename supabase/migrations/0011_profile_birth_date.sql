alter table public.profile
    add column birth_date date check (birth_date >= date '1900-01-01');

update public.profile
set birth_date = make_date(birth_year, 1, 1)
where birth_year is not null;

alter table public.profile drop column birth_year;
