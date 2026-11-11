// Edge Function `contact-consent` (S3.03) : page « accord.html » du proche (sans compte).
// POST { token } → { name, status } · POST { token, decision: accept | refuse | stop } → { ok }.
// Jeton dans le corps (jamais dans l'URL), 404 identique pour tout jeton non valable, CORS limité à GitHub Pages.
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const ORIGINS = (Deno.env.get("TRACK_ALLOWED_ORIGINS") ?? "https://shelohermituss-hub.github.io").split(",").map((s) => s.trim());
const TOKEN = /^[A-Za-z0-9_-]{32}$/;

function headers(origin: string | null): HeadersInit {
  return {
    "Access-Control-Allow-Origin": origin && ORIGINS.includes(origin) ? origin : ORIGINS[0],
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "content-type",
    "Vary": "Origin",
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
  };
}

Deno.serve(async (req) => {
  const origin = req.headers.get("origin");
  const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: headers(origin) });
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: headers(origin) });
  if (req.method !== "POST") return reply(405, { error: "method" });
  let body: { token?: unknown; decision?: unknown };
  try {
    body = await req.json();
  } catch {
    return reply(400, { error: "bad_request" });
  }
  const token = typeof body.token === "string" ? body.token : "";
  if (!TOKEN.test(token)) return reply(404, { error: "not_found" });
  try {
    if (body.decision !== undefined) {
      const [row] = await sql`select private.contact_invite_decide(${token}, ${String(body.decision)}) as ok`;
      return row.ok ? reply(200, { ok: true }) : reply(404, { error: "not_found" });
    }
    const [row] = await sql`select private.contact_invite_read(${token}) as data`;
    return row.data ? reply(200, row.data) : reply(404, { error: "not_found" });
  } catch (e) {
    console.error("contact-consent: base", (e as Error).name);
    return reply(500, { error: "server" });
  }
});
