-- 1.19 : connexion sur un nouveau téléphone (PDF 14.1). Le compte est retrouvé par le numéro vérifié (code SMS, Supabase Auth).
-- Ce qui revient : le profil, et les contacts de confiance SEULEMENT si l'utilisateur a activé leur sauvegarde (opt-in).
-- Les contacts sauvegardés sont chiffrés par le serveur (même clé du coffre que les numéros des alertes, cr_alert_key) :
-- c'est le modèle de confiance existant, pas du bout en bout. Les preuves, elles, ne sont jamais transférées.

create function public.my_profile()
returns table (display_name text, locale text, birth_year int, birth_month int, is_adult_attested boolean)
language sql stable security definer set search_path = public as $$
  select display_name, locale, birth_year, birth_month, is_adult_attested from public.profiles where id = (select auth.uid())
$$;

create table private.contact_backups (
  owner_id uuid primary key references public.profiles (id) on delete cascade,
  data_enc bytea not null,
  updated_at timestamptz not null default now()
);
alter table private.contact_backups enable row level security;

-- Remplace la sauvegarde : 5 contacts au plus, 16 Ko au plus.
create function public.backup_contacts(p_items jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.profiles where id = v_uid) then raise exception 'profile_required'; end if;
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) > 5 or octet_length(p_items::text) > 16384 then
    raise exception 'invalid_backup';
  end if;
  insert into private.contact_backups (owner_id, data_enc)
  values (v_uid, extensions.pgp_sym_encrypt(p_items::text, private.secret('cr_alert_key')))
  on conflict (owner_id) do update set data_enc = excluded.data_enc, updated_at = now();
end $$;

-- Rend la sauvegarde de l'appelant (déchiffrée), ou null s'il n'y en a pas.
create function public.restore_contacts()
returns jsonb language sql stable security definer set search_path = public as $$
  select extensions.pgp_sym_decrypt(data_enc, private.secret('cr_alert_key'))::jsonb
  from private.contact_backups where owner_id = (select auth.uid())
$$;

-- Désactivation de la sauvegarde : plus rien n'est gardé.
create function public.forget_contact_backup()
returns void language sql security definer set search_path = public as $$
  update private.contact_backups set data_enc = extensions.pgp_sym_encrypt('[]', private.secret('cr_alert_key')), updated_at = now()
  where owner_id = (select auth.uid())
$$;

revoke execute on function public.my_profile(), public.backup_contacts(jsonb), public.restore_contacts(),
  public.forget_contact_backup() from public, anon;
grant execute on function public.my_profile(), public.backup_contacts(jsonb), public.restore_contacts(),
  public.forget_contact_backup() to authenticated;
