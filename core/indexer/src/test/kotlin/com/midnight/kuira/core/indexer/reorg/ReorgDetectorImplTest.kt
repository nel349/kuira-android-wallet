package com.midnight.kuira.core.indexer.reorg

import com.midnight.kuira.core.indexer.model.BlockInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for ReorgDetectorImpl.
 *
 * **Critical:** Tests blockchain reorganization detection.
 * Incorrect reorg handling = permanent balance corruption.
 */
class ReorgDetectorImplTest {

    // Counter to generate unique hashes
    private var hashCounter = 0

    private fun createBlock(
        height: Long,
        hash: String? = null, // If provided, will be converted to valid hex
        timestamp: Long = System.currentTimeMillis()
    ): BlockInfo {
        // Generate unique valid hex hash
        val validHash = if (hash != null) {
            // Convert any string to a unique valid hex hash
            hashCounter++
            "deadbeef" + hashCounter.toString(16).padStart(56, '0')
        } else {
            "deadbeef" + height.toString(16).padStart(56, '0')
        }

        return BlockInfo(
            height = height,
            hash = validHash,
            timestamp = timestamp,
            eventCount = 0
        )
    }

    @Test
    fun `first block records without reorg`() = runTest {
        val detector = ReorgDetectorImpl()

        val block1 = createBlock(1)
        val reorg = detector.recordBlock(block1, "genesis_parent_hash")

        assertNull(reorg)
        assertEquals(block1, detector.getChainTip())
    }

    @Test
    fun `extending chain normally does not trigger reorg`() = runTest {
        val detector = ReorgDetectorImpl()

        // Block 1
        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")

        // Block 2 extends block 1 - use actual hash from block1
        val block2 = createBlock(2)
        val reorg = detector.recordBlock(block2, block1.hash)

        assertNull(reorg)
        assertEquals(2L, detector.getChainTip()?.height)
    }

    @Test
    fun `parent hash mismatch triggers reorg when ancestor can be found`() = runTest {
        val detector = ReorgDetectorImpl()

        // Build longer chain so reorg can find common ancestor
        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)
        val block3 = createBlock(3)
        detector.recordBlock(block3, block2.hash)

        // Block 4 pointing back to block 1 triggers reorg
        val block4 = createBlock(4)
        val reorg = detector.recordBlock(block4, block1.hash)

        assertNotNull(reorg)
    }

    @Test
    fun `shallow reorg correctly identified`() = runTest {
        val config = ReorgConfig(finalityThreshold = 10, historyDepth = 100)
        val detector = ReorgDetectorImpl(config)

        // Build chain: 1 → 2 → 3
        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)
        val block3 = createBlock(3)
        detector.recordBlock(block3, block2.hash)

        // Reorg at height 4: parent points to block 1 (fork from block 1)
        // This creates: 1 → 2 → 3 (old) vs 1 → 4 (new)
        val block4Alt = createBlock(4)
        val reorg = detector.recordBlock(block4Alt, block1.hash)

        // Should trigger reorg: common ancestor = block 1, depth = 4-1 = 3
        assertNotNull(reorg)
        assertTrue(reorg is ReorgEvent.ShallowReorg)

        val shallowReorg = reorg as ReorgEvent.ShallowReorg
        assertEquals(4L, shallowReorg.reorgHeight)
        assertEquals(1L, shallowReorg.commonAncestorHeight)

        // oldBranch = getBlocksInRange(2, 3) = [block2, block3]
        // depth = oldBranch.size = 2
        assertEquals(2, shallowReorg.depth)
        assertEquals(listOf(block2.hash, block3.hash), shallowReorg.oldBranch.map { it.hash })

        assertTrue(shallowReorg.depth < config.finalityThreshold)
    }

    @Test
    fun `deep reorg correctly identified`() = runTest {
        val config = ReorgConfig(finalityThreshold = 5, historyDepth = 100)
        val detector = ReorgDetectorImpl(config)

        // Build long chain: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10
        val blocks = mutableListOf<BlockInfo>()
        val block1 = createBlock(1)
        blocks.add(block1)
        detector.recordBlock(block1, "genesis_hash")

        for (i in 2L..10L) {
            val block = createBlock(i)
            blocks.add(block)
            detector.recordBlock(block, blocks[i.toInt() - 2].hash)
        }

        // Deep reorg at height 11: fork from block 3 (depth = 11-3 = 8 > threshold 5)
        // This creates: 1→2→3→4→5→6→7→8→9→10 (old) vs 1→2→3→11 (new)
        val block11Alt = createBlock(11)
        val reorg = detector.recordBlock(block11Alt, blocks[2].hash) // Block 3 at index 2

        assertNotNull(reorg)
        assertTrue(reorg is ReorgEvent.DeepReorg)

        val deepReorg = reorg as ReorgEvent.DeepReorg
        assertEquals(11L, deepReorg.reorgHeight)
        assertEquals(3L, deepReorg.commonAncestorHeight)

        // Verify it's beyond finality: (reorgHeight - commonAncestor) > threshold
        val actualDepth = 11L - 3L  // = 8
        assertTrue(actualDepth > config.finalityThreshold) // 8 > 5

        // depthBeyondFinality is calculated as: reorgHeight - finalityThreshold
        assertEquals(11L - config.finalityThreshold, deepReorg.depthBeyondFinality)
    }

    @Test
    fun `extending from older block triggers reorg with correct common ancestor`() = runTest {
        val detector = ReorgDetectorImpl()

        // Build chain: 1 → 2 → 3
        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)
        val block3 = createBlock(3)
        detector.recordBlock(block3, block2.hash)

        // Block 4 extending from block 2 (not block 3) triggers reorg
        val block4 = createBlock(4)
        val reorg = detector.recordBlock(block4, block2.hash)

        // Should trigger reorg: parent (block2) != previous block (block3)
        assertNotNull(reorg)
        assertTrue(reorg is ReorgEvent.ShallowReorg)

        val shallowReorg = reorg as ReorgEvent.ShallowReorg
        assertEquals(2L, shallowReorg.commonAncestorHeight) // Common ancestor is block 2
    }

    @Test
    fun `reorg event emitted via flow`() = runTest {
        val detector = ReorgDetectorImpl()

        // Collect reorg events
        val reorgEvents = mutableListOf<ReorgEvent>()
        val job = launch {
            detector.observeReorgs().collect { reorgEvents.add(it) }
        }

        // Yield to ensure collector starts
        testScheduler.advanceUntilIdle()

        // Build chain
        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)
        val block3 = createBlock(3)
        detector.recordBlock(block3, block2.hash)

        // Trigger reorg by forking from block 1
        val block4 = createBlock(4)
        val reorg = detector.recordBlock(block4, block1.hash)

        // Give flow time to emit
        testScheduler.advanceUntilIdle()
        job.cancel()

        // Verify reorg was emitted
        assertNotNull(reorg)
        assertEquals(1, reorgEvents.size)
        assertEquals(reorg, reorgEvents[0])
    }

    @Test
    fun `reset clears detector state`() = runTest {
        val detector = ReorgDetectorImpl()

        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)

        assertNotNull(detector.getChainTip())

        detector.reset()

        assertNull(detector.getChainTip())
    }

    @Test
    fun `block history maintained within history depth`() = runTest {
        val config = ReorgConfig(historyDepth = 10, finalityThreshold = 5)
        val detector = ReorgDetectorImpl(config)

        // Add 20 blocks (exceeds history depth)
        val blocks = mutableListOf<BlockInfo>()
        val block1 = createBlock(1)
        blocks.add(block1)
        detector.recordBlock(block1, "genesis_hash")

        for (i in 2L..20L) {
            val block = createBlock(i)
            blocks.add(block)
            detector.recordBlock(block, blocks[i.toInt() - 2].hash)
        }

        // Block 15 should still be in history (within last 10 blocks from block 20)
        // Try to fork from block 15
        val block21 = createBlock(21)
        val reorg = detector.recordBlock(block21, blocks[14].hash) // Block 15 at index 14

        // Should trigger reorg since parent (block 15) != previous block (block 20)
        assertNotNull(reorg)
    }

    @Test
    fun `cannot find common ancestor beyond history throws exception`() = runTest {
        val config = ReorgConfig(historyDepth = 5, finalityThreshold = 5)
        val detector = ReorgDetectorImpl(config)

        // Build chain of 10 blocks
        val blocks = mutableListOf<BlockInfo>()
        val block1 = createBlock(1)
        blocks.add(block1)
        detector.recordBlock(block1, "genesis_hash")

        for (i in 2L..10L) {
            val block = createBlock(i)
            blocks.add(block)
            detector.recordBlock(block, blocks[i.toInt() - 2].hash)
        }

        // Try to reorg pointing to block 3 (should be beyond 5-block history from block 10)
        // Block 3 was at height 3, current height is 10, so history goes back to block 6 (10 - 5 + 1)
        try {
            val block11 = createBlock(11)
            detector.recordBlock(block11, blocks[2].hash) // Block 3 at index 2, should be pruned
            fail("Expected ReorgHandlingException")
        } catch (e: ReorgHandlingException) {
            // Expected
            assertTrue(e.message!!.contains("Cannot find common ancestor"))
        }
    }

    @Test
    fun `shallow reorg contains correct branch information`() = runTest {
        val detector = ReorgDetectorImpl()

        // Build chain: 1 → 2 → 3
        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)
        val block3 = createBlock(3)
        detector.recordBlock(block3, block2.hash)

        // Trigger reorg at height 4 by forking from block 2
        val block4 = createBlock(4)
        val reorg = detector.recordBlock(block4, block2.hash)

        assertNotNull(reorg)
        assertTrue(reorg is ReorgEvent.ShallowReorg)

        val shallowReorg = reorg as ReorgEvent.ShallowReorg
        assertEquals(2L, shallowReorg.commonAncestorHeight)
        assertEquals(listOf(block3.hash), shallowReorg.oldBranch.map { it.hash })
        assertEquals(listOf(block4.hash), shallowReorg.newBranch.map { it.hash })
    }

    @Test
    fun `multiple sequential reorgs handled correctly`() = runTest {
        val detector = ReorgDetectorImpl()

        // Build initial chain: 1 → 2 → 3
        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)
        val block3 = createBlock(3)
        detector.recordBlock(block3, block2.hash)

        // First reorg: fork from block 1
        val block4 = createBlock(4)
        val reorg1 = detector.recordBlock(block4, block1.hash)
        assertNotNull(reorg1)

        // Chain continues from block 4
        val block5 = createBlock(5)
        detector.recordBlock(block5, block4.hash)

        // Second reorg: fork from block 4
        val block6 = createBlock(6)
        val reorg2 = detector.recordBlock(block6, block4.hash)
        assertNotNull(reorg2)
    }

    @Test
    fun `chain tip updates correctly after reorg`() = runTest {
        val detector = ReorgDetectorImpl()

        val block1 = createBlock(1)
        detector.recordBlock(block1, "genesis_hash")
        val block2 = createBlock(2)
        detector.recordBlock(block2, block1.hash)

        assertEquals(2L, detector.getChainTip()?.height)
        assertEquals(block2.hash, detector.getChainTip()?.hash)

        // Reorg with block 3 forking from block 1
        val block3 = createBlock(3)
        detector.recordBlock(block3, block1.hash)

        // Chain tip should update
        assertEquals(3L, detector.getChainTip()?.height)
        assertEquals(block3.hash, detector.getChainTip()?.hash)
    }

    @Test
    fun `config validation enforces constraints`() {
        try {
            ReorgConfig(finalityThreshold = 0)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Finality threshold must be positive"))
        }
    }

    @Test
    fun `config validation enforces history depth greater than finality`() {
        try {
            ReorgConfig(finalityThreshold = 100, historyDepth = 50)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("History depth"))
            assertTrue(e.message!!.contains("finality threshold"))
        }
    }

    @Test
    fun `reorg depth calculated correctly for shallow reorg`() = runTest {
        val config = ReorgConfig(finalityThreshold = 10, historyDepth = 100)
        val detector = ReorgDetectorImpl(config)

        // Build chain to height 5
        val blocks = mutableListOf<BlockInfo>()
        val block1 = createBlock(1)
        blocks.add(block1)
        detector.recordBlock(block1, "genesis_hash")

        for (i in 2L..5L) {
            val block = createBlock(i)
            blocks.add(block)
            detector.recordBlock(block, blocks[i.toInt() - 2].hash)
        }

        // Reorg at height 6, forking from block 3
        val block6 = createBlock(6)
        val reorg = detector.recordBlock(block6, blocks[2].hash) // Block 3 at index 2

        assertNotNull(reorg)
        assertTrue(reorg is ReorgEvent.ShallowReorg)

        val shallowReorg = reorg as ReorgEvent.ShallowReorg
        assertEquals(3L, shallowReorg.commonAncestorHeight)
        // oldBranch contains blocks 4 and 5
        assertEquals(2, shallowReorg.depth)
    }
}
