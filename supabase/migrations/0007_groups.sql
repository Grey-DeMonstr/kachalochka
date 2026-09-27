alter table public.machine add column link_id uuid;
create index machine_link_id_idx on public.machine (link_id);
