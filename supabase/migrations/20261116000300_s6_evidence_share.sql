-- 1.16 : partage des preuves de bout en bout (S6.06, S1.13b, ADR-0016).
-- Chaque application publie la partie publique de sa paire de clés de chiffrement hybride (Tink HPKE), signée par sa clé
-- d'appareil. Pour une alerte, le téléphone de la personne protégée chiffre la clé de l'alerte pour chaque proche qui a
-- l'app (enveloppe). Le serveur ne garde que des clés publiques et des enveloppes : il ne peut toujours rien écouter.
-- Un proche « a l'app » s'il a vérifié son numéro et que ce numéro correspond à une invitation ACCEPTÉE de la personne.

create table public.user_public_keys (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  -- Haché du numéro vérifié au moment de la publication : sert à reconnaître le proche, jamais le numéro lui-même.
  phone_hash text not null,
  public_key text not null check (char_length(public_key) between 40 and 2000),
  device_key text check (char_length(device_key) <= 400),
  signature text check (char_length(signature) <= 200),
  updated_at timestamptz not null default now()
);
create index user_public_keys_phone on public.user_public_keys (phone_hash);
alter table public.user_public_keys enable row level security;
create policy user_public_keys_select_own on public.user_public_keys
  for select to authenticated using (user_id = (select auth.uid()));
revoke insert, update, delete on public.user_public_keys from anon, authenticated;

create table public.evidence_key_wraps (
  alert_id uuid not null references public.evidence_uploads (alert_id) on delete cascade,
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  wrapped text not null check (char_length(wrapped) between 40 and 4000),
  created_at timestamptz not null default now(),
  primary key (alert_id, recipient_id)
);
create index evidence_key_wraps_recipient on public.evidence_key_wraps (recipient_id);
alter table public.evidence_key_wraps enable row level security;
create policy evidence_key_wraps_select_recipient on public.evidence_key_wraps
  for select to authenticated using (recipient_id = (select auth.uid()));
revoke insert, update, delete on public.evidence_key_wraps from anon, authenticated;

-- Publie (ou remplace) sa clé publique de partage. Exige un numéro vérifié : c'est lui qui relie l'app au contact.
create function public.publish_public_key(p_public_key text, p_device_key text, p_signature text)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_hash text := public.current_phone_hash();
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if v_hash is null then raise exception 'phone_required'; end if;
  if not exists (select 1 from public.profiles where id = v_uid) then raise exception 'profile_required'; end if;
  insert into public.user_public_keys (user_id, phone_hash, public_key, device_key, signature)
  values (v_uid, v_hash, p_public_key, p_device_key, p_signature)
  on conflict (user_id) do update
    set phone_hash = excluded.phone_hash, public_key = excluded.public_key, device_key = excluded.device_key,
        signature = excluded.signature, updated_at = now();
end $$;

-- Pour la personne protégée : les proches qui peuvent recevoir la clé de cette alerte (invitation acceptée, app installée).
create function public.evidence_recipients(p_alert_id uuid)
returns table (recipient_id uuid, public_key text, device_key text, signature text)
language plpgsql stable security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.evidence_uploads where alert_id = p_alert_id and owner_id = v_uid and delete_after > now()) then
    raise exception 'upload_not_found';
  end if;
  return query
    select distinct k.user_id, k.public_key, k.device_key, k.signature
    from public.user_public_keys k
    join public.contact_invites ci on ci.phone_hash = k.phone_hash
    where ci.owner_id = v_uid and ci.status = 'accepted' and k.user_id <> v_uid;
end $$;

-- Dépose l'enveloppe de la clé d'alerte pour un proche. Idempotent : une enveloppe déjà déposée ne change pas.
create function public.share_evidence_key(p_alert_id uuid, p_recipient_id uuid, p_wrapped text)
returns void
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  perform 1 from public.evidence_uploads where alert_id = p_alert_id and owner_id = v_uid and delete_after > now() for update;
  if not found then raise exception 'upload_not_found'; end if;
  if not exists (
    select 1 from public.user_public_keys k
    join public.contact_invites ci on ci.phone_hash = k.phone_hash
    where k.user_id = p_recipient_id and ci.owner_id = v_uid and ci.status = 'accepted' and k.user_id <> v_uid
  ) then
    raise exception 'recipient_not_allowed';
  end if;
  insert into public.evidence_key_wraps (alert_id, recipient_id, wrapped)
  values (p_alert_id, p_recipient_id, p_wrapped)
  on conflict (alert_id, recipient_id) do nothing;
end $$;

-- Pour le proche : les preuves qu'on lui a confiées et qui ne sont pas échues (une invitation retirée coupe l'accès).
create function public.my_shared_evidence()
returns table (
  alert_id uuid, owner_name text, folder text, wrapped text, segments int, chain_root text,
  opened_at timestamptz, delete_after timestamptz
)
language sql stable security definer set search_path = public as $$
  select u.alert_id, p.display_name, u.owner_id::text || '/' || u.alert_id::text, w.wrapped, u.segments, u.chain_root,
         u.started_at, u.delete_after
  from public.evidence_key_wraps w
  join public.evidence_uploads u on u.alert_id = w.alert_id
  join public.profiles p on p.id = u.owner_id
  where w.recipient_id = (select auth.uid()) and u.delete_after > now()
    and exists (
      select 1 from public.contact_invites ci
      where ci.owner_id = u.owner_id and ci.status = 'accepted' and ci.phone_hash = public.current_phone_hash()
    )
  order by u.started_at desc
$$;

-- Le proche peut lire les objets (chiffrés) d'une alerte dont il a reçu l'enveloppe, tant qu'elle n'est pas échue.
do $$
begin
  if exists (select 1 from pg_namespace where nspname = 'storage') then
    execute $p$
      create policy evidence_select_shared on storage.objects for select to authenticated
      using (
        bucket_id = 'evidence'
        and exists (
          select 1 from public.evidence_key_wraps w
          join public.evidence_uploads u on u.alert_id = w.alert_id
          where w.recipient_id = (select auth.uid())
            and u.owner_id::text = (storage.foldername(name))[1]
            and u.alert_id::text = (storage.foldername(name))[2]
            and u.delete_after > now()
        )
      )
    $p$;
  end if;
end $$;

revoke execute on function public.publish_public_key(text, text, text), public.evidence_recipients(uuid),
  public.share_evidence_key(uuid, uuid, text), public.my_shared_evidence() from public, anon;
grant execute on function public.publish_public_key(text, text, text), public.evidence_recipients(uuid),
  public.share_evidence_key(uuid, uuid, text), public.my_shared_evidence() to authenticated;
