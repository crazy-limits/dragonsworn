package crazylimits.dragonsworn.config;

import crazylimits.dragonsworn.ai.AirTactics.Attack;
import crazylimits.dragonsworn.ai.AirTactics.Reach;
import crazylimits.dragonsworn.ai.AirTactics;
import crazylimits.dragonsworn.ai.HitTally;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DragonConfigTest {
	@TempDir
	Path dir;

	@AfterEach
	void restore() {
		DragonConfig.defaults();
	}

	@Test
	void aMissingFileIsWrittenWithEveryDefaultAndReadsBackTheSame() throws Exception {
		Path file = dir.resolve(DragonConfig.FILE);
		assertTrue(DragonConfig.load(file).isEmpty());
		assertTrue(Files.exists(file));
		List<String> problems = new ArrayList<>();
		Map<String, Object> values = Toml.parse(Files.readString(file), problems);
		assertTrue(problems.isEmpty(), problems.toString());
		assertEquals(DragonConfig.options().size(), values.size());
		assertEquals(0.35, (Double) values.get("ground_combat.seize_chance"));
		assertEquals(Boolean.TRUE, values.get("attacks.snatch"));
		// a second read changes nothing and needs no rewrite
		String before = Files.readString(file);
		assertTrue(DragonConfig.load(file).isEmpty());
		assertEquals(before, Files.readString(file));
	}

	@Test
	void valuesAreReadClampedAndBadOnesReported() throws Exception {
		Path file = dir.resolve(DragonConfig.FILE);
		DragonConfig.load(file);
		String text = Files.readString(file)
				.replace("seize_chance = 0.35", "seize_chance = 0.9  # greedy")
				.replace("bite_recovery = 12", "bite_recovery = 99999")
				.replace("snatch = true\n", "snatch = \"maybe\"\n")
				+ "\n[nonsense]\nfoo = 1\n";
		Files.writeString(file, text);
		List<String> problems = DragonConfig.load(file);
		assertEquals(0.9, DragonConfig.SEIZE_CHANCE.get());
		assertEquals(1200, DragonConfig.BITE_RECOVERY.get());
		assertTrue(DragonConfig.SNATCH.get(), "a bad value falls back to the default");
		assertEquals(3, problems.size(), problems.toString());
	}

	@Test
	void missingOptionsAreAddedBackKeepingTheOthers() throws Exception {
		Path file = dir.resolve(DragonConfig.FILE);
		Files.writeString(file, "[ground_combat]\nseize_chance = 0.1\n");
		DragonConfig.load(file);
		assertEquals(0.1, DragonConfig.SEIZE_CHANCE.get());
		Map<String, Object> values = Toml.parse(Files.readString(file), new ArrayList<>());
		assertEquals(0.1, (Double) values.get("ground_combat.seize_chance"));
		assertEquals(DragonConfig.options().size(), values.size());
	}

	@Test
	void aDisabledAttackIsNeverChosenNorAFallback() {
		DragonConfig.HOVER_BREATH.set(false);
		DragonConfig.BARRAGE.set(false);
		for (double roll = 0.0; roll < 1.0; roll += 0.01) {
			List<Attack> choices = AirTactics.choices(Reach.AIR, roll);
			assertFalse(choices.contains(Attack.HOVER_BREATH) || choices.contains(Attack.BARRAGE), choices.toString());
			assertFalse(choices.isEmpty());
		}
		DragonConfig.FLYBY_BITE.set(false);
		DragonConfig.HOVER_BITE.set(false);
		assertTrue(AirTactics.choices(Reach.AIR, 0.5).isEmpty());
	}

	@Test
	void weightsPickTheFirstChoice() {
		for (DragonConfig.Num w : List.of(DragonConfig.GROUND_SNATCH, DragonConfig.GROUND_BREATH_PASS, DragonConfig.GROUND_FIREBALL_PASS,
				DragonConfig.GROUND_BARRAGE)) w.set(0.0);
		DragonConfig.GROUND_CHARGE.set(3.0);     // weights need not sum to 1
		for (double roll = 0.0; roll < 1.0; roll += 0.05) assertEquals(Attack.CHARGE, AirTactics.choices(Reach.GROUND, roll).get(0));
	}

	@Test
	void theHitTallyFollowsTheConfig() {
		DragonConfig.OVERWHELM_HITS.set(2);
		DragonConfig.OVERWHELM_WINDOW.set(10);
		HitTally tally = new HitTally();
		tally.hit(0);
		assertFalse(tally.overwhelmed(1));
		tally.hit(5);
		assertTrue(tally.overwhelmed(8));
		assertFalse(tally.overwhelmed(11));
	}
}
