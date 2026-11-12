// Edge Function `trip-watchdog` (S4.07, S4.08) : appelée chaque minute par pg_cron (private.trip_watchdog_tick) quand un
// trajet suivi est resté 10 min sans battement, ou quand le téléphone revient. Envoie un SMS (Vonage) aux proches choisis.
// Pas de JWT : l'appel est authentifié par le secret `cr_cron_secret` (Vault), comparé en temps constant.
// Numéros jamais journalisés ; un SMS par palier (marqué dès qu'un envoi réussit ; sinon nouvel essai à la minute suivante).
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const KEY = Deno.env.get("VONAGE_API_KEY") ?? "";
const SECRET = Deno.env.get("VONAGE_API_SECRET") ?? "";
const FROM = Deno.env.get("VONAGE_FROM") ?? "CercleRouge";

type Due = { trip_id: string; kind: "lost" | "recovered"; name: string | null; phone: string; lang: string; lat: number | null; lon: number | null; at: Date };

const hhmm = (d: Date) =>
  new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit", timeZone: "America/Port-au-Prince" }).format(d);
const where = (d: Due) => (d.lat != null && d.lon != null ? ` https://maps.google.com/?q=${d.lat.toFixed(5)},${d.lon.toFixed(5)}` : "");

// GSM-7 (sans accents), un ou deux SMS au plus.
const TEXT: Record<string, Record<Due["kind"], (d: Due, n: string) => string>> = {
  fr: {
    lost: (d, n) => `Cercle Rouge: plus de nouvelles du telephone de ${n} depuis ${hhmm(d.at)} (heure d'Haiti) pendant un trajet. Derniere position:${where(d) || " inconnue"}. Essaie de l'appeler.`,
    recovered: (_d, n) => `Cercle Rouge: le telephone de ${n} est de nouveau joignable. Le trajet continue.`,
  },
  ht: {
    lost: (d, n) => `Cercle Rouge: telefon ${n} pa bay nouvel depi ${hhmm(d.at)} (le Ayiti) pandan yon trajet. Denye pozisyon:${where(d) || " enkoni"}. Eseye rele l.`,
    recovered: (_d, n) => `Cercle Rouge: telefon ${n} ap reponn anko. Trajet la ap kontinye.`,
  },
  en: {
    lost: (d, n) => `Cercle Rouge: no news from ${n}'s phone since ${hhmm(d.at)} (Haiti time) during a trip. Last position:${where(d) || " unknown"}. Try calling them.`,
    recovered: (_d, n) => `Cercle Rouge: ${n}'s phone is reachable again. The trip continues.`,
  },
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

function sameSecret(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a), y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0 && x.length > 0;
}

async function send(to: string, text: string): Promise<boolean> {
  const res = await fetch("https://api.nexmo.com/v1/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Authorization": "Basic " + btoa(`${KEY}:${SECRET}`) },
    body: JSON.stringify({ message_type: "text", channel: "sms", to: to.slice(1), from: FROM, text }),
  });
  if (!res.ok) console.error("trip-watchdog: vonage", res.status);
  return res.ok;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method" });
  let expected = "";
  try {
    [{ s: expected }] = await sql`select private.secret('cr_cron_secret') as s`;
  } catch (e) {
    console.error("trip-watchdog: base", (e as Error).name);
    return json(500, { error: "server" });
  }
  if (!sameSecret(req.headers.get("x-cr-cron") ?? "", expected ?? "")) return json(401, { error: "unauthorized" });
  if (!KEY || !SECRET) return json(503, { error: "not_configured" });

  const due = await sql<Due[]>`select * from private.trip_alerts_due()`;
  const byTrip = new Map<string, { kind: Due["kind"]; ok: boolean }>();
  for (const d of due) {
    const t = TEXT[d.lang] ?? TEXT.fr;
    const ok = await send(d.phone, t[d.kind](d, d.name?.slice(0, 30) || "?")).catch(() => false);
    const prev = byTrip.get(d.trip_id);
    byTrip.set(d.trip_id, { kind: d.kind, ok: (prev?.ok ?? false) || ok });
  }
  let sent = 0;
  for (const [id, r] of byTrip) {
    if (!r.ok) continue;
    await sql`select private.trip_alerts_sent(${id}::uuid, ${r.kind})`;
    sent++;
  }
  // Trajets sans destinataire : marqués aussi, pour ne pas réveiller la fonction chaque minute.
  await sql`select private.trip_alerts_sent(t.id, case when t.lost_notified_at is null then 'lost' else 'recovered' end)
            from public.trips t
            where ((t.lost_at is not null and t.lost_notified_at is null) or (t.recovered_at is not null and t.recovery_notified_at is null))
              and not exists (select 1 from private.trip_recipients r where r.trip_id = t.id and r.phone_enc is not null)`;
  return json(200, { trips: sent });
});
