package com.claude.teambattle;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config
{
	public enum CenterMode { SPAWN, RANDOM, CUSTOM }

	public static final ForgeConfigSpec SPEC;

	// Valori modificabili in config/teambattle-common.toml,
	// oppure passabili come argomenti a /battle start
	public static final ForgeConfigSpec.DoubleValue INITIAL_BORDER_SIZE;
	public static final ForgeConfigSpec.DoubleValue FINAL_BORDER_SIZE;
	public static final ForgeConfigSpec.IntValue SHRINK_SECONDS;
	public static final ForgeConfigSpec.DoubleValue MAX_HEALTH;
	public static final ForgeConfigSpec.DoubleValue BORDER_DAMAGE_PER_BLOCK;
	public static final ForgeConfigSpec.DoubleValue MIN_TEAM_SEPARATION;
	public static final ForgeConfigSpec.EnumValue<CenterMode> CENTER_MODE;
	public static final ForgeConfigSpec.DoubleValue CENTER_X;
	public static final ForgeConfigSpec.DoubleValue CENTER_Z;
	public static final ForgeConfigSpec.DoubleValue RANDOM_CENTER_RANGE;
	public static final ForgeConfigSpec.BooleanValue TEAMMATE_GLOW;
	public static final ForgeConfigSpec.BooleanValue ANNOUNCE_SHRINK;
	public static final ForgeConfigSpec.BooleanValue SHOW_FINAL_ZONE;
	public static final ForgeConfigSpec.BooleanValue WITHER_ENABLED;
	public static final ForgeConfigSpec.IntValue WITHER_DELAY_SECONDS;
	public static final ForgeConfigSpec.IntValue WITHER_INTERVAL_SECONDS;

	static
	{
		ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

		b.push("battle");

		INITIAL_BORDER_SIZE = b
			.comment("Lato iniziale della barriera, in blocchi (la zona di gioco di partenza)")
			.defineInRange("initialBorderSize", 500.0, 20.0, 30000.0);

		FINAL_BORDER_SIZE = b
			.comment("Lato finale della barriera, in blocchi (es. 80 = zona finale 80x80)")
			.defineInRange("finalBorderSize", 80.0, 5.0, 30000.0);

		SHRINK_SECONDS = b
			.comment("Tempo in secondi impiegato dalla barriera per restringersi dal lato iniziale a quello finale")
			.defineInRange("shrinkSeconds", 600, 10, 86400);

		MAX_HEALTH = b
			.comment("Vita massima dei giocatori durante la partita (20 = vanilla, 50 = 25 cuori)")
			.defineInRange("maxHealth", 20.0, 1.0, 1024.0);

		BORDER_DAMAGE_PER_BLOCK = b
			.comment("Danno al secondo per ogni blocco di distanza oltre la barriera")
			.defineInRange("borderDamagePerBlock", 1.0, 0.0, 100.0);

		MIN_TEAM_SEPARATION = b
			.comment("Distanza minima desiderata tra i punti di spawn dei team, in blocchi (best effort)")
			.defineInRange("minTeamSeparation", 80.0, 0.0, 10000.0);

		CENTER_MODE = b
			.comment("Centro della mappa: SPAWN = spawn del mondo, RANDOM = casuale vicino allo spawn, CUSTOM = coordinate centerX/centerZ")
			.defineEnum("centerMode", CenterMode.SPAWN);

		CENTER_X = b
			.comment("Coordinata X del centro (usata solo con centerMode = CUSTOM)")
			.defineInRange("centerX", 0.0, -30000000.0, 30000000.0);

		CENTER_Z = b
			.comment("Coordinata Z del centro (usata solo con centerMode = CUSTOM)")
			.defineInRange("centerZ", 0.0, -30000000.0, 30000000.0);

		RANDOM_CENTER_RANGE = b
			.comment("Con centerMode = RANDOM: distanza massima del centro casuale dallo spawn del mondo, in blocchi")
			.defineInRange("randomCenterRange", 2000.0, 0.0, 1000000.0);

		TEAMMATE_GLOW = b
			.comment("I compagni di squadra si vedono con l'effetto glowing (visibile solo ai membri del team)")
			.define("teammateGlow", true);

		ANNOUNCE_SHRINK = b
			.comment("Annuncia in chat il tempo rimanente prima della chiusura completa della zona")
			.define("announceShrink", true);

		SHOW_FINAL_ZONE = b
			.comment("Mostra il perimetro della zona finale con particelle rosse")
			.define("showFinalZone", true);

		WITHER_ENABLED = b
			.comment("Dopo la chiusura completa della zona, spawna Wither per forzare la fine della partita")
			.define("witherEnabled", true);

		WITHER_DELAY_SECONDS = b
			.comment("Secondi di attesa tra la chiusura completa della zona e il primo Wither")
			.defineInRange("witherDelaySeconds", 600, 5, 86400);

		WITHER_INTERVAL_SECONDS = b
			.comment("Secondi tra un Wither e il successivo")
			.defineInRange("witherIntervalSeconds", 60, 5, 86400);

		b.pop();

		SPEC = b.build();
	}
}
