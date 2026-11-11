-- ⚠ LOCAL UNIQUEMENT : imite le minimum de Supabase pour tester les migrations sur un PostgreSQL nu.
-- Jamais appliqué sur un vrai projet Supabase (qui fournit déjà tout ceci).
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

do $$ begin
  create role anon nologin;
exception when duplicate_object then null; end $$;
do $$ begin
  create role authenticated nologin;
exception when duplicate_object then null; end $$;
do $$ begin
  create role service_role nologin bypassrls;
exception when duplicate_object then null; end $$;

create schema if not exists auth;
create table if not exists auth.users (
  id uuid primary key default gen_random_uuid(),
  phone text unique,          -- Supabase stocke le E.164 sans « + »
  created_at timestamptz not null default now()
);

create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(nullif(current_setting('request.jwt.claims', true), '')::json ->> 'sub', '')::uuid
$$;
create or replace function auth.role() returns text language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), '')::json ->> 'role', 'anon')
$$;

grant usage on schema public, auth, extensions to anon, authenticated, service_role;
grant execute on function auth.uid(), auth.role() to anon, authenticated, service_role;
-- Comme Supabase : privilèges de table larges par défaut, la RLS fait le tri.
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
alter default privileges in schema public grant execute on functions to anon, authenticated, service_role;

-- Vault (Supabase) : coffre de secrets, imité sans chiffrement pour les tests locaux.
alter table auth.users add column if not exists phone_confirmed_at timestamptz;
create schema if not exists vault;
create table if not exists vault.secrets (name text primary key, secret text not null, description text);
create or replace view vault.decrypted_secrets as select name, secret as decrypted_secret from vault.secrets;
create or replace function vault.create_secret(new_secret text, new_name text, new_description text default '') returns uuid
language sql as $$ insert into vault.secrets values (new_name, new_secret, new_description); select gen_random_uuid() $$;
