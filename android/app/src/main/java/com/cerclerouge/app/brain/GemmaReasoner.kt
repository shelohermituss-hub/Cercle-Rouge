package com.cerclerouge.app.brain

import android.content.Context
import com.cerclerouge.brain.AsyncReasoner
import com.cerclerouge.brain.FunctionCalls
import com.cerclerouge.brain.ModelOutput
import com.cerclerouge.brain.SituationContext
import com.cerclerouge.brain.SituationEvent
import com.cerclerouge.brain.SituationLog
import com.cerclerouge.brain.Thought
import com.cerclerouge.privacy.log.EventLog
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Le modèle de langage du veilleur (ADR-0029) : Gemma 3 270M exécuté **dans le téléphone** avec LiteRT-LM, sur le
 * processeur, sans réseau. Il lit la situation des 5 dernières minutes (texte sans nom ni numéro) et répond par des appels
 * de fonction de la liste fermée ; ses propositions passent par la garde et ne peuvent qu'**ajouter** aux règles
 * ([com.cerclerouge.brain.CombinedReasoner]).
 *
 * Garde-fous : modèle chargé seulement si son empreinte est la bonne (T17), sur un téléphone d'au moins 4 Go, et si le
 * porteur ne l'a pas coupé ; une réflexion à la fois ; réponse plus lente que [MAX_MS] jetée ; toute erreur laisse les
 * règles seules. Libéré dès que la protection s'arrête.
 */
@Singleton
class GemmaReasoner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ModelStore,
    private val log: EventLog
) : AsyncReasoner {
    override val version = "${ModelStore.VERSION}+${FunctionCalls.PROMPT_VERSION}"

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "cr-llm").apply { priority = Thread.MIN_PRIORITY } }
    private val busy = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var broken = false

    @Volatile private var last: Pair<Instant, Thought>? = null

    /** Le modèle peut-il tourner ici (installé, permis, téléphone assez puissant) ? */
    val usable: Boolean
        get() = !broken &&
            store.enabled &&
            store.state.value == ModelState.Ready &&
            com.cerclerouge.app.ui.brain.deviceTier(context) != com.cerclerouge.app.ui.brain.DeviceTier.BASIC

    override fun request(events: List<SituationEvent>, ctx: SituationContext, now: Instant) {
        if (!usable || events.isEmpty() || !busy.compareAndSet(false, true)) return
        val situation = SituationLog().apply { events.forEach(::add) }.render(now, ctx)
        worker.execute {
            val start = System.currentTimeMillis()
            try {
                val output = ask(situation)
                val ms = System.currentTimeMillis() - start
                val read = ModelOutput.read(output, version)
                if (ms <= MAX_MS) last = now to read.thought
                journal(
                    "llm ms=$ms actions=${read.thought.actions.joinToString(",") {
                        it.kind.call
                    }.ifEmpty { "-" }} unknown=${read.unknown.size} late=${ms > MAX_MS}"
                )
            } catch (e: Throwable) {
                // Mémoire insuffisante, fichier illisible, bibliothèque absente : on reste aux règles jusqu'au redémarrage.
                broken = e is UnsatisfiedLinkError || e is OutOfMemoryError
                journal("llm error=${e.javaClass.simpleName}")
            } finally {
                busy.set(false)
            }
        }
    }

    override fun result(since: Instant): Thought? = last?.takeIf { !it.first.isBefore(since) }?.second

    /** Libère la mémoire du modèle (protection coupée, modèle supprimé). */
    fun release() {
        worker.execute {
            runCatching { engine?.close() }
            engine = null
            last = null
        }
    }

    private fun ask(situation: String): String {
        val e = engine ?: load().also { engine = it }
        val config = ConversationConfig(
            systemInstruction = Contents.of(FunctionCalls.systemPrompt(emptyList())),
            samplerConfig = SamplerConfig(1, 1.0, 0.0, 0),
            maxOutputToken = MAX_TOKENS
        )
        return e.createConversation(config).use { conv ->
            conv.sendMessage(ModelOutput.prompt(situation)).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        }
    }

    private fun load(): Engine {
        check(store.verifyInstalled()) { "empreinte du modèle invalide" }
        return Engine(
            EngineConfig(store.file.absolutePath, Backend.CPU(), maxNumTokens = CONTEXT_TOKENS, cacheDir = context.cacheDir.absolutePath)
        ).apply {
            initialize()
        }
    }

    private fun journal(line: String) {
        scope.launch { runCatching { log.append("brain", line) } }
    }

    private companion object {
        const val MAX_MS = 3_000L
        const val MAX_TOKENS = 96
        const val CONTEXT_TOKENS = 1024
    }
}
