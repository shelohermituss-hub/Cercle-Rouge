package com.cerclerouge.privacy

import com.cerclerouge.privacy.log.ChainedEntry
import com.cerclerouge.privacy.log.HashChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class HashChainTest {
    private fun chain(n: Int): List<ChainedEntry> {
        val out = mutableListOf<ChainedEntry>()
        repeat(n) { out += HashChain.next(out.lastOrNull(), 1_000L + it, "type$it", "payload $it") }
        return out
    }

    @Test fun `R-PE-03 une chaine intacte est validee`() = assertNull(HashChain.firstBreak(chain(10)))

    @Test fun `une entree modifiee est detectee`() {
        val c = chain(10).toMutableList()
        c[4] = c[4].copy(payload = "falsifié")
        assertEquals(5L, HashChain.firstBreak(c))
    }

    @Test fun `une entree supprimee est detectee`() {
        val c = chain(10).toMutableList().apply { removeAt(6) }
        assertEquals(7L, HashChain.firstBreak(c))
    }

    @Test fun `une entree recalculee sans le reste de la chaine est detectee`() {
        val c = chain(5).toMutableList()
        val forged = c[1].copy(payload = "x")
        c[1] = forged.copy(hash = HashChain.hash(forged.prevHash, forged.seq, forged.utcMillis, forged.type, forged.payload))
        assertEquals(3L, HashChain.firstBreak(c))
    }

    @Test fun `les champs sont separes sans ambiguite`() {
        assertNotEquals(HashChain.hash("p", 1, 1, "ab", "c"), HashChain.hash("p", 1, 1, "a", "bc"))
    }
}
