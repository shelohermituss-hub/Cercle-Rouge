// Edge Function `track` (S3.07) : relais entre la page web des proches (sans compte) et la base.
// - POST { token }                → statut, prénom, niveau, positions (alerte ouverte seulement), chronologie
// - POST { token, action }        → « J'ai pris contact » (TOOK_CONTACT), « Alerte fausse » (FALSE_ALARM), « Secours » (CALLED_EMERGENCY)
// Le jeton voyage dans le corps (jamais dans l'URL : pas de trace dans les journaux d'accès). Seul son hachage est en base.
// Aucun jeton, position ni prénom n'est écrit dans les journaux de la fonction (R-BE-06).
// Réponse identique (404) pour un jeton inconnu, expiré ou révoqué : jetons non énumérables (R-BE-05).
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const ALLOWED_ORIGINS = (Deno.env.get("TRACK_ALLOWED_ORIGINS") ?? "https://shelohermituss-hub.github.io")
  .split(",").map((s) => s.trim()).filter(Boolean);
const TOKEN = /^[A-Za-z0-9_-]{32}$/;
const ACTIONS = new Set(["TOOK_CONTACT", "FALSE_ALARM", "CALLED_EMERGENCY"]);

// Limite de débit grossière par IP et par instance (60 requêtes par minute) ; la vraie barrière reste le jeton de 192 bits.
const hits = new Map<string, { n: number; reset: number }>();
function limited(ip: string): boolean {
  const now = Date.now();
  const h = hits.get(ip);
  if (!h || h.reset < now) {
    hits.set(ip, { n: 1, reset: now + 60_000 });
    if (hits.size > 5_000) hits.clear();
    return false;
  }
  h.n++;
  return h.n > 60;
}

function headers(origin: string | null): HeadersInit {
  const allow = origin && ALLOWED_ORIGINS.includes(origin) ? origin : ALLOWED_ORIGINS[0];
  return {
    "Access-Control-Allow-Origin": allow,
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "content-type",
    "Access-Control-Max-Age": "600",
    "Vary": "Origin",
    "Cache-Control": "no-store",
    "Referrer-Policy": "no-referrer",
    "X-Content-Type-Options": "nosniff",
    "Content-Type": "application/json; charset=utf-8",
  };
}

const reply = (status: number, body: unknown, origin: string | null) =>
  new Response(JSON.stringify(body), { status, headers: headers(origin) });

Deno.serve(async (req) => {
  const origin = req.headers.get("origin");
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: headers(origin) });
  if (req.method !== "POST") return reply(405, { error: "method" }, origin);
  const ip = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "?";
  if (limited(ip)) return reply(429, { error: "rate_limited" }, origin);

  let body: { token?: unknown; action?: unknown };
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "bad_request" }, origin);
  }
  const token = typeof body.token === "string" ? body.token : "";
  if (!TOKEN.test(token)) return reply(404, { error: "not_found" }, origin);

  try {
    if (body.action !== undefined) {
      const action = String(body.action);
      if (!ACTIONS.has(action)) return reply(400, { error: "bad_action" }, origin);
      const [row] = await sql`select private.track_act(${token}, ${action}) as ok`;
      return row.ok ? reply(200, { ok: true }, origin) : reply(404, { error: "not_found" }, origin);
    }
    const [row] = await sql`select private.track_read(${token}) as data`;
    if (!row.data) return reply(404, { error: "not_found" }, origin);
    return reply(200, row.data, origin);
  } catch (e) {
    console.error("track: erreur base", (e as Error).name); // sans détail : pas de données personnelles
    return reply(500, { error: "server" }, origin);
  }
});
