-- The caller deletes their own user. Every owned table references auth.users with on delete
-- cascade, and a group the caller owns goes with its members. Storage objects are not rows of
-- these tables, so the client removes its photos through the Storage API first.
create or replace function public.delete_my_account() returns void
language plpgsql security definer set search_path = '' as $$
begin
    delete from auth.users where id = (select auth.uid());
end;
$$;

revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
