package crazylimits.dragonsworn.body;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartsTest {
	@Test
	void namedPartsMatchTheGeneratedPoseTrack() {
		assertEquals("head", PoseTrack.partName(Parts.HEAD));
		assertEquals("neck", PoseTrack.partName(Parts.NECK_UPPER));
		assertEquals("neck", PoseTrack.partName(Parts.NECK_LOWER));
		assertEquals("body", PoseTrack.partName(Parts.CHEST));
		assertEquals("body", PoseTrack.partName(Parts.HIPS));
		assertEquals("tail", PoseTrack.partName(Parts.TAIL_ROOT));
		assertEquals("tail", PoseTrack.partName(Parts.TAIL_TIP));
	}

	@Test
	void theTailTipIsTheLastPartOfTheTail() {
		int deepest = -1, tip = -1;
		for (int p = 0; p < PoseTrack.PARTS; p++) {
			if (PoseTrack.partChain(p) == PoseTrack.CHAIN_TAIL && PoseTrack.partDepth(p) > deepest) {
				deepest = PoseTrack.partDepth(p);
				tip = p;
			}
		}
		assertEquals(tip, Parts.TAIL_TIP);
	}

	@Test
	void theUpperNeckIsNearerTheHeadThanTheLower() {
		assertEquals(PoseTrack.CHAIN_NECK, PoseTrack.partChain(Parts.NECK_UPPER));
		assertEquals(PoseTrack.CHAIN_NECK, PoseTrack.partChain(Parts.NECK_LOWER));
		assertEquals(true, PoseTrack.partDepth(Parts.NECK_UPPER) > PoseTrack.partDepth(Parts.NECK_LOWER));
	}
}
