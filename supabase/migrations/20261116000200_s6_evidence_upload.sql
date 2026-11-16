-- 1.15 : copie de secours des preuves sur le serveur (S6.03–S6.05, R-EVD-04/05, R-SY-04, ADR-0028).
-- Les objets envoyés sont DÉJÀ chiffrés sur le téléphone (Tink, une clé par alerte) : le serveur ne peut pas les lire.
-- Rangement : bucket privé `evidence`, chemin « <propriétaire>/<alerte>/<n°>.bin ». Un objet ne se remplace jamais.
-- Suppression : par l'Edge Function `evidence-purge` (API Storage), appelée par pg_cron, pour les lignes échues ou dont le
-- propriétaire a supprimé son compte. Le serveur garde au plus 30 jours, même en rétention « Manuel » (ADR-0028).

create table public.evidence_uploads (
  alert_id uuid primary key,
  -- Pas de clé étrangère : le compte ou l'alerte peuvent disparaître avant que la purge ait retiré les objets.
  owner_id uuid not null,
  chain_root text check (chain_root ~ '^[0-9a-f]{64}$'),
  segments int check (segments between 0 and 200),
  signature text check (char_length(signature) <= 200),
  public_key text check (char_length(public_key) <= 400),
  started_at timestamptz not null default now(),
  root_registered_at timestamptz,
  delete_after timestamptz not null,
  check (delete_after <= started_at + interval '30 days')
);
create index evidence_uploads_owner on public.evidence_uploads (owner_id);
create index evidence_uploads_due on public.evidence_uploads (delete_after);

alter table public.evidence_uploads enable row level security;
create policy evidence_uploads_select_own on public.evidence_uploads
  for select to authenticated using (owner_id = (select auth.uid()));
revoke insert, update, delete on public.evidence_uploads from anon, authenticated;

-- Ouvre l'envoi des preuves d'une alerte de l'appelant ; idempotent. Rend le dossier où écrire.
create function public.begin_evidence_upload(p_alert_id uuid)
returns text
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  perform 1 from public.alerts where id = p_alert_id and owner_id = v_uid for update;
  if not found then raise exception 'alert_not_found'; end if;
  insert into public.evidence_uploads (alert_id, owner_id, delete_after)
  values (p_alert_id, v_uid, now() + interval '30 days')
  on conflict (alert_id) do nothing;
  perform 1 from public.evidence_uploads where alert_id = p_alert_id and owner_id = v_uid and delete_after > now();
  if not found then raise exception 'upload_closed'; end if;
  return v_uid::text || '/' || p_alert_id::text;
end $$;

-- Racine de la chaîne d'intégrité, signée sur le téléphone par sa clé d'appareil (Keystore), horodatée par le serveur.
-- Une fois enregistrée, elle ne change plus. `p_keep_hours` : durée de garde choisie (0 à 720 h), plafonnée à 30 jours.
create function public.register_chain_root(
  p_alert_id uuid, p_root text, p_segments int, p_signature text, p_public_key text, p_keep_hours int
)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_row public.evidence_uploads;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if p_keep_hours is null or p_keep_hours not between 0 and 720 then raise exception 'invalid_keep'; end if;
  select * into v_row from public.evidence_uploads where alert_id = p_alert_id and owner_id = v_uid for update;
  if not found then raise exception 'upload_not_found'; end if;
  if v_row.chain_root is not null then
    if v_row.chain_root <> p_root then raise exception 'root_already_registered'; end if;
    return; -- même racine : rien à faire (idempotent)
  end if;
  update public.evidence_uploads
  set chain_root = p_root, segments = p_segments, signature = p_signature, public_key = p_public_key,
      root_registered_at = now(),
      delete_after = least(now() + make_interval(hours => p_keep_hours), started_at + interval '30 days')
  where alert_id = p_alert_id;
end $$;

-- L'utilisateur a supprimé ses preuves sur le téléphone (ou elles sont arrivées à échéance) : la copie serveur suit.
-- Sans argument : toutes ses preuves (effacement d'urgence).
create function public.forget_evidence(p_alert_id uuid default null)
returns int
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  n int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  update public.evidence_uploads set delete_after = least(delete_after, now())
  where owner_id = v_uid and (p_alert_id is null or alert_id = p_alert_id);
  get diagnostics n = row_count;
  return n;
end $$;

-- Pour evidence-purge : dossiers à vider (échus, ou compte supprimé). Jamais exposé à l'API.
create function private.evidence_due(p_limit int default 50)
returns table (alert_id uuid, folder text)
language sql security definer set search_path = public as $$
  select u.alert_id, u.owner_id::text || '/' || u.alert_id::text
  from public.evidence_uploads u
  where u.delete_after <= now() or not exists (select 1 from public.profiles p where p.id = u.owner_id)
  order by u.delete_after
  limit least(greatest(p_limit, 1), 200)
$$;

create function private.evidence_purged(p_alert_id uuid)
returns void
language sql security definer set search_path = public as $$
  delete from public.evidence_uploads where alert_id = p_alert_id
$$;

create function private.evidence_purge_tick()
returns int
language plpgsql security definer set search_path = public as $$
declare
  n int;
begin
  select count(*) into n from public.evidence_uploads u
  where u.delete_after <= now() or not exists (select 1 from public.profiles p where p.id = u.owner_id);
  if n > 0 and private.secret('cr_functions_url') is not null and exists (select 1 from pg_extension where extname = 'pg_net') then
    perform net.http_post(
      url := private.secret('cr_functions_url') || '/evidence-purge',
      headers := jsonb_build_object('Content-Type', 'application/json', 'x-cr-cron', private.secret('cr_cron_secret')),
      body := '{}'::jsonb
    );
  end if;
  return n;
end $$;

-- Bucket privé et règles d'accès (seulement sur Supabase : le schéma storage n'existe pas dans les tests locaux).
do $$
begin
  if exists (select 1 from pg_namespace where nspname = 'storage') then
    insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    values ('evidence', 'evidence', false, 2097152, array['application/octet-stream'])
    on conflict (id) do nothing;

    -- Écrire : seulement dans son dossier, pour un envoi ouvert et pas échu. Jamais de remplacement (pas d'UPDATE).
    execute $p$
      create policy evidence_insert_own on storage.objects for insert to authenticated
      with check (
        bucket_id = 'evidence'
        and (storage.foldername(name))[1] = (select auth.uid())::text
        and exists (
          select 1 from public.evidence_uploads u
          where u.owner_id = (select auth.uid())
            and u.alert_id::text = (storage.foldername(name))[2]
            and u.delete_after > now()
        )
      )
    $p$;
    -- Lire : son propre dossier (le partage aux proches viendra avec les enveloppes de clés, ADR-0016).
    execute $p$
      create policy evidence_select_own on storage.objects for select to authenticated
      using (bucket_id = 'evidence' and (storage.foldername(name))[1] = (select auth.uid())::text)
    $p$;
  end if;
end $$;

do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.schedule('cr-evidence-purge', '23 * * * *', 'select private.evidence_purge_tick()');
  end if;
end $$;

revoke execute on function public.begin_evidence_upload(uuid),
  public.register_chain_root(uuid, text, int, text, text, int), public.forget_evidence(uuid) from public, anon;
grant execute on function public.begin_evidence_upload(uuid),
  public.register_chain_root(uuid, text, int, text, text, int), public.forget_evidence(uuid) to authenticated;
revoke execute on function private.evidence_due(int), private.evidence_purged(uuid), private.evidence_purge_tick()
  from public, anon, authenticated;
