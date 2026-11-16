// Edge Function `evidence-purge` (1.15, ADR-0028) : appelée chaque heure par pg_cron (private.evidence_purge_tick) quand des
// preuves envoyées sont échues (rétention choisie, 30 jours au plus, ou suppression par l'utilisateur) ou que le compte a été
// supprimé. Vide le dossier « <propriétaire>/<alerte> » du bucket privé `evidence` par l'API Storage, puis retire la ligne.
// Pas de JWT : l'appel est authentifié par le secret `cr_cron_secret` (Vault), comparé en temps constant.
// La clé service_role ne sert qu'ici (règle 9). Les objets sont chiffrés sur le téléphone : rien n'est lu, seulement effacé.
import postgres from "npm:postgres@3.4.5";

const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 2, idle_timeout: 20 });
const URL_BASE = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const BUCKET = "evidence";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store" } });

function sameSecret(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a), y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0 && x.length > 0;
}

const storage = (path: string, init: RequestInit) =>
  fetch(`${URL_BASE}/storage/v1/${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${SERVICE}`, "apikey": SERVICE, ...(init.headers ?? {}) },
  });

/** Vide un dossier (au plus 200 segments + métadonnées par alerte). Vrai si plus rien n'y reste. */
async function emptyFolder(folder: string): Promise<boolean> {
  const list = await storage(`object/list/${BUCKET}`, { method: "POST", body: JSON.stringify({ prefix: folder + "/", limit: 1000 }) });
  if (!list.ok) {
    console.error("evidence-purge: list", list.status);
    return false;
  }
  const names = ((await list.json()) as { name: string }[]).map((o) => `${folder}/${o.name}`);
  if (names.length === 0) return true;
  const del = await storage(`object/${BUCKET}`, { method: "DELETE", body: JSON.stringify({ prefixes: names }) });
  if (!del.ok) console.error("evidence-purge: delete", del.status);
  return del.ok;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method" });
  let expected = "";
  try {
    [{ s: expected }] = await sql`select private.secret('cr_cron_secret') as s`;
  } catch (e) {
    console.error("evidence-purge: base", (e as Error).name);
    return json(500, { error: "db" });
  }
  if (!sameSecret(req.headers.get("x-cr-cron") ?? "", expected ?? "")) return json(401, { error: "unauthorized" });
  if (!URL_BASE || !SERVICE) return json(500, { error: "config" });

  const due = await sql<{ alert_id: string; folder: string }[]>`select alert_id, folder from private.evidence_due(50)`;
  let purged = 0;
  for (const d of due) {
    // La ligne n'est retirée qu'une fois le dossier vide : sinon nouvel essai à l'heure suivante.
    if (await emptyFolder(d.folder)) {
      await sql`select private.evidence_purged(${d.alert_id}::uuid)`;
      purged++;
    }
  }
  return json(200, { due: due.length, purged });
});
