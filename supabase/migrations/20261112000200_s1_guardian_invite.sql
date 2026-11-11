-- S1.17 · Invitation SMS de l'adulte référent (Vonage), envoyée par l'Edge Function `guardian-invite`.
-- Le numéro en clair n'existe que le temps de la demande (effacé à la décision, migration s1_socle) ; un seul SMS par demande.

alter table public.guardian_links add column invite_sent_at timestamptz;

-- Demande en attente du jeune, pas encore envoyée, créée il y a moins de 15 min : rend le numéro et le prénom du jeune,
-- et la marque comme envoyée (un seul SMS par demande, même si l'app rappelle). Null sinon.
create function private.guardian_invite_target(p_minor uuid)
returns table (phone text, minor_name text)
language plpgsql security definer set search_path = public as $$
declare g public.guardian_links;
begin
  select * into g from public.guardian_links
  where minor_id = p_minor and status = 'pending' and invite_sent_at is null
    and guardian_phone is not null and created_at > now() - interval '15 minutes'
  order by created_at desc limit 1
  for update;
  if g.id is null then return; end if;
  update public.guardian_links set invite_sent_at = now() where id = g.id;
  return query select g.guardian_phone, (select display_name from public.profiles where id = p_minor);
end $$;

revoke execute on function private.guardian_invite_target(uuid) from public, anon, authenticated;
