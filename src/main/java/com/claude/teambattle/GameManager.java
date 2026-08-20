package com.claude.teambattle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GameManager
{
	public static final GameManager INSTANCE = new GameManager();

	private static final ChatFormatting[] TEAM_COLORS = {
		ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW,
		ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD, ChatFormatting.DARK_GREEN,
		ChatFormatting.DARK_AQUA, ChatFormatting.DARK_PURPLE, ChatFormatting.DARK_RED, ChatFormatting.DARK_BLUE,
		ChatFormatting.WHITE, ChatFormatting.GRAY, ChatFormatting.DARK_GRAY, ChatFormatting.BLACK
	};

	private final Random random = new Random();

	private boolean running = false;
	private MinecraftServer server = null;

	// UUID giocatore -> nome team scoreboard
	private final Map<UUID, String> playerTeams = new HashMap<>();
	// Nome team -> nome visualizzato
	private final Map<String, Component> teamDisplayNames = new HashMap<>();
	private final Set<UUID> alivePlayers = new HashSet<>();
	private final List<String> teamNames = new ArrayList<>();

	// Stato del bordo prima della partita, per ripristinarlo alla fine
	private double prevBorderSize = -1;
	private double prevBorderCenterX = 0;
	private double prevBorderCenterZ = 0;
	private double prevDamagePerBlock = 0.2;

	private double gameMaxHealth = 20.0;

	// Geometria della partita corrente
	private double centerX = 0, centerZ = 0;
	private double finalSizeStored = 80;
	private long shrinkEndTick = 0;
	private final Set<Integer> announcedSeconds = new HashSet<>();
	private int tickCounter = 0;

	// Fase Wither dopo la chiusura completa della zona
	private long zoneClosedTick = 0;
	private long nextWitherTick = 0;
	private int withersSpawned = 0;
	private ServerBossEvent witherBar = null;
	private final List<UUID> spawnedWithers = new ArrayList<>();

	// Lucky block
	private final Set<BlockPos> luckyBlocks = new HashSet<>();
	private Boolean luckyOverride = null;	// impostato con /battle luckyblocks, null = usa la config
	private Integer teamSizeOverride = null;	// impostato con /battle teamsize, null = usa la config
	private Integer luckyCountOverride = null;	// impostato con /battle luckyblocks count

	public int getLuckyCount()
	{
		return luckyCountOverride != null ? luckyCountOverride : Config.LUCKY_COUNT.get();
	}

	public void setLuckyCountOverride(int count)
	{
		this.luckyCountOverride = Math.max(1, Math.min(5000, count));
	}

	public int getTeamSize()
	{
		return teamSizeOverride != null ? teamSizeOverride : Config.TEAM_SIZE.get();
	}

	public void setTeamSizeOverride(int size)
	{
		this.teamSizeOverride = Math.max(1, Math.min(16, size));
	}

	public boolean isLuckyEnabled()
	{
		return luckyOverride != null ? luckyOverride : Config.LUCKY_ENABLED.get();
	}

	public void setLuckyOverride(boolean enabled)
	{
		this.luckyOverride = enabled;
	}

	// Override del centro impostato con /battle center (null = usa la config)
	private Config.CenterMode centerOverrideMode = null;
	private double centerOverrideX = 0, centerOverrideZ = 0;

	private static final DustParticleOptions ZONE_PARTICLE =
		new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.15f), 1.5f);

	private GameManager() {}

	public void setCenterOverride(Config.CenterMode mode, double x, double z)
	{
		this.centerOverrideMode = mode;
		this.centerOverrideX = x;
		this.centerOverrideZ = z;
	}

	public Component describeCenter()
	{
		Config.CenterMode mode = centerOverrideMode != null ? centerOverrideMode : Config.CENTER_MODE.get();
		String base = switch (mode)
		{
			case SPAWN -> "spawn del mondo";
			case RANDOM -> "casuale (entro " + (int) (double) Config.RANDOM_CENTER_RANGE.get() + " blocchi dallo spawn)";
			case CUSTOM -> centerOverrideMode != null
				? "personalizzato (" + (int) centerOverrideX + ", " + (int) centerOverrideZ + ")"
				: "personalizzato (" + (int) (double) Config.CENTER_X.get() + ", " + (int) (double) Config.CENTER_Z.get() + ")";
		};
		return Component.literal("Centro mappa: " + base + (centerOverrideMode != null ? " [impostato via comando]" : " [da config]"));
	}

	public boolean isRunning()
	{
		return running;
	}

	// ---------------------------------------------------------------
	// Avvio partita
	// ---------------------------------------------------------------
	public Component start(MinecraftServer server, double initialSize, double finalSize, int shrinkSeconds, double maxHealth)
	{
		if (running)
			return Component.literal("C'è già una partita in corso! Usa /battle stop per annullarla.");

		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		players.removeIf(ServerPlayer::isSpectator);

		if (players.size() < 2)
			return Component.literal("Servono almeno 2 giocatori connessi (non spettatori) per iniziare.");

		final int teamSize = getTeamSize();
		final int teamCount = (players.size() + teamSize - 1) / teamSize;

		if (teamCount < 2)
			return Component.literal("Con team da " + teamSize + " servono almeno " + (teamSize + 1) +
				" giocatori per avere almeno 2 team. Cambia con /battle teamsize <n>.");

		this.server = server;
		this.gameMaxHealth = maxHealth;

		playerTeams.clear();
		teamDisplayNames.clear();
		alivePlayers.clear();
		teamNames.clear();

		ServerLevel level = server.overworld();
		WorldBorder border = level.getWorldBorder();

		// Salva lo stato del bordo per ripristinarlo a fine partita
		prevBorderSize = border.getSize();
		prevBorderCenterX = border.getCenterX();
		prevBorderCenterZ = border.getCenterZ();
		prevDamagePerBlock = border.getDamagePerBlock();

		// Determina il centro della mappa
		BlockPos spawn = level.getSharedSpawnPos();
		Config.CenterMode mode = centerOverrideMode != null ? centerOverrideMode : Config.CENTER_MODE.get();

		switch (mode)
		{
			case RANDOM ->
			{
				double range = Config.RANDOM_CENTER_RANGE.get();
				double a = random.nextDouble() * Math.PI * 2;
				double d = Math.sqrt(random.nextDouble()) * range;
				centerX = spawn.getX() + 0.5 + Math.cos(a) * d;
				centerZ = spawn.getZ() + 0.5 + Math.sin(a) * d;
			}
			case CUSTOM ->
			{
				centerX = centerOverrideMode != null ? centerOverrideX : Config.CENTER_X.get();
				centerZ = centerOverrideMode != null ? centerOverrideZ : Config.CENTER_Z.get();
			}
			default ->
			{
				centerX = spawn.getX() + 0.5;
				centerZ = spawn.getZ() + 0.5;
			}
		}

		finalSizeStored = finalSize;
		shrinkEndTick = level.getGameTime() + shrinkSeconds * 20L;
		announcedSeconds.clear();
		tickCounter = 0;
		zoneClosedTick = 0;
		nextWitherTick = 0;
		withersSpawned = 0;
		spawnedWithers.clear();
		luckyBlocks.clear();

		border.setCenter(centerX, centerZ);
		border.setSize(initialSize);
		border.setDamagePerBlock(Config.BORDER_DAMAGE_PER_BLOCK.get());
		border.setDamageSafeZone(0.0);

		// Mischia i giocatori e crea coppie casuali
		Collections.shuffle(players, random);

		Scoreboard scoreboard = server.getScoreboard();

		for (int i = 0; i < teamCount; i++)
		{
			String name = "battle_team_" + (i + 1);

			// Rimuovi eventuali team rimasti da partite precedenti
			PlayerTeam stale = scoreboard.getPlayerTeam(name);
			if (stale != null)
				scoreboard.removePlayerTeam(stale);

			PlayerTeam team = scoreboard.addPlayerTeam(name);
			ChatFormatting color = TEAM_COLORS[i % TEAM_COLORS.length];
			Component display = Component.literal("Team " + (i + 1)).withStyle(color);
			team.setDisplayName(display);
			team.setColor(color);
			team.setAllowFriendlyFire(false);
			team.setSeeFriendlyInvisibles(true);

			teamNames.add(name);
			teamDisplayNames.put(name, display);
		}

		// Assegna i giocatori alle squadre (a coppie; con numero dispari
		// l'ultimo team ha un solo membro)
		for (int i = 0; i < players.size(); i++)
		{
			ServerPlayer p = players.get(i);
			String teamName = teamNames.get(i / teamSize);
			PlayerTeam team = scoreboard.getPlayerTeam(teamName);
			if (team != null)
				scoreboard.addPlayerToTeam(p.getScoreboardName(), team);

			playerTeams.put(p.getUUID(), teamName);
			alivePlayers.add(p.getUUID());
		}

		// Teletrasporta ogni team (compagni insieme) in un punto casuale
		// dentro la barriera iniziale
		double spreadRadius = initialSize / 2.0 * 0.8;
		double minSep = Config.MIN_TEAM_SEPARATION.get();
		List<double[]> usedSpots = new ArrayList<>();

		for (int t = 0; t < teamCount; t++)
		{
			double[] spot = findTeamSpot(level, centerX, centerZ, spreadRadius, minSep, usedSpots);
			usedSpots.add(spot);

			int member = 0;
			for (int i = 0; i < players.size(); i++)
			{
				if (i / teamSize != t)
					continue;

				ServerPlayer p = players.get(i);

				// Compagni disposti in una piccola griglia attorno al punto
				double px = spot[0] + (member % 3) * 3;
				double pz = spot[1] + (member / 3) * 3;
				int py = groundY(level, (int) Math.floor(px), (int) Math.floor(pz));

				preparePlayer(p, maxHealth);
				p.teleportTo(level, px + 0.5, py, pz + 0.5, random.nextFloat() * 360f, 0f);
				member++;
			}
		}

		// Avvia la registrazione delle modifiche al mondo per il ripristino
		if (Config.RESTORE_WORLD.get())
			Rollback.begin(level);

		// Lucky block sparsi nella zona
		if (isLuckyEnabled())
		{
			placeLuckyBlocks(level, initialSize);
			server.getPlayerList().broadcastSystemMessage(
				Component.literal("🍀 Modalità LUCKY BLOCK attiva: " + luckyBlocks.size() +
					" blocchi d'oro sparsi nella zona, con " + LuckyEffects.count() +
					" effetti possibili. Rompili... se hai coraggio!").withStyle(ChatFormatting.GOLD), false);
		}

		// Avvia il restringimento della barriera
		border.lerpSizeBetween(initialSize, finalSize, shrinkSeconds * 1000L);

		running = true;

		// Annunci
		broadcastTitle(Component.literal("LA BATTAGLIA È INIZIATA!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
			Component.literal("Ultimo team vivo vince").withStyle(ChatFormatting.YELLOW));

		server.getPlayerList().broadcastSystemMessage(
			Component.literal("Barriera: da " + (int) initialSize + " a " + (int) finalSize +
				" blocchi in " + shrinkSeconds + " secondi. Vita massima: " + (int) maxHealth +
				". Centro: " + (int) centerX + ", " + (int) centerZ + ".").withStyle(ChatFormatting.GRAY), false);

		if (Config.SHOW_FINAL_ZONE.get())
			server.getPlayerList().broadcastSystemMessage(
				Component.literal("La zona finale è segnata dal perimetro di particelle rosse.").withStyle(ChatFormatting.RED), false);

		for (String name : teamNames)
		{
			StringBuilder members = new StringBuilder();
			for (Map.Entry<UUID, String> e : playerTeams.entrySet())
			{
				if (!e.getValue().equals(name))
					continue;
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null)
				{
					if (members.length() > 0)
						members.append(", ");
					members.append(p.getGameProfile().getName());
				}
			}
			server.getPlayerList().broadcastSystemMessage(
				teamDisplayNames.get(name).copy().append(Component.literal(": " + members).withStyle(ChatFormatting.WHITE)), false);
		}

		return Component.literal("Partita avviata con " + players.size() + " giocatori in " + teamCount +
			" team da " + teamSize + (teamSize == 1 ? " (tutti contro tutti)." : " membri."));
	}

	private void preparePlayer(ServerPlayer p, double maxHealth)
	{
		p.setGameMode(GameType.SURVIVAL);

		// Inventario pulito, poi il kit iniziale configurato con /battle kit set
		p.getInventory().clearContent();
		KitManager.applyTo(p);

		AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
		if (attr != null)
			attr.setBaseValue(maxHealth);

		p.setHealth((float) maxHealth);
		p.getFoodData().setFoodLevel(20);
		p.getFoodData().setSaturation(5.0f);
		p.removeAllEffects();
		p.setRemainingFireTicks(0);
		p.fallDistance = 0;
	}

	// Cerca un punto per un team: dentro il raggio, lontano dagli altri team
	// (best effort), non in acqua/lava se possibile
	private double[] findTeamSpot(ServerLevel level, double cx, double cz, double radius, double minSep, List<double[]> used)
	{
		double[] best = null;

		for (int attempt = 0; attempt < 48; attempt++)
		{
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = Math.sqrt(random.nextDouble()) * radius;
			double x = cx + Math.cos(angle) * dist;
			double z = cz + Math.sin(angle) * dist;

			boolean farEnough = true;
			for (double[] u : used)
			{
				double dx = u[0] - x, dz = u[1] - z;
				if (Math.sqrt(dx * dx + dz * dz) < minSep)
				{
					farEnough = false;
					break;
				}
			}

			// Dopo metà tentativi accetta anche punti più vicini
			if (!farEnough && attempt < 24)
				continue;

			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			int y = groundY(level, bx, bz);

			boolean dry = level.getBlockState(new BlockPos(bx, y - 1, bz)).getFluidState().isEmpty();

			best = new double[] { x, z };

			if (dry)
				return best;
		}

		return best != null ? best : new double[] { cx, cz };
	}

	private int groundY(ServerLevel level, int x, int z)
	{
		// Forza il caricamento del chunk prima di leggere l'altezza
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	// ---------------------------------------------------------------
	// Tick di gioco: glowing compagni, countdown zona, particelle zona finale
	// ---------------------------------------------------------------
	public void tick()
	{
		Rollback.tickRestore();

		if (!running || server == null)
			return;

		tickCounter++;

		// Ogni secondo: glowing dei compagni + annunci countdown
		if (tickCounter % 20 == 0)
		{
			if (Config.TEAMMATE_GLOW.get())
				sendTeammateGlow(true);

			if (Config.ANNOUNCE_SHRINK.get())
				announceShrinkCountdown();

			updateWitherPhase();
		}

		// Ogni 2 secondi: perimetro della zona finale
		if (Config.SHOW_FINAL_ZONE.get() && tickCounter % 40 == 0)
			showFinalZoneParticles();
	}

	// Invia ai soli compagni di squadra un pacchetto di metadata con il flag
	// glowing attivo: l'effetto è visibile solo a loro (col colore del team)
	private void sendTeammateGlow(boolean glowing)
	{
		for (UUID id : new ArrayList<>(alivePlayers))
		{
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null)
				continue;

			String teamName = playerTeams.get(id);
			byte flags = buildSharedFlags(p, glowing);

			var packet = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
				p.getId(),
				java.util.List.of(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags)));

			for (Map.Entry<UUID, String> e : playerTeams.entrySet())
			{
				if (!e.getValue().equals(teamName) || e.getKey().equals(id))
					continue;

				ServerPlayer mate = server.getPlayerList().getPlayer(e.getKey());
				if (mate != null)
					mate.connection.send(packet);
			}
		}
	}

	// Ricostruisce il byte dei flag condivisi dell'entità (indice 0 dei metadata)
	private byte buildSharedFlags(ServerPlayer p, boolean glowing)
	{
		byte flags = 0;
		if (p.isOnFire()) flags |= 0x01;
		if (p.isShiftKeyDown()) flags |= 0x02;
		if (p.isSprinting()) flags |= 0x08;
		if (p.isSwimming()) flags |= 0x10;
		if (p.isInvisible()) flags |= 0x20;
		if (glowing) flags |= 0x40;
		if (p.isFallFlying()) flags |= (byte) 0x80;
		return flags;
	}

	private void announceShrinkCountdown()
	{
		long now = server.overworld().getGameTime();
		long remainingTicks = shrinkEndTick - now;

		if (remainingTicks < 0)
			return;

		int remaining = (int) Math.ceil(remainingTicks / 20.0);

		int[] milestones = { 1800, 1200, 900, 600, 300, 180, 120, 60, 30, 15, 10, 5, 4, 3, 2, 1 };
		for (int m : milestones)
		{
			if (remaining == m && !announcedSeconds.contains(m))
			{
				announcedSeconds.add(m);
				String text = m >= 60
					? "La zona si chiude completamente tra " + (m / 60) + " minut" + (m == 60 ? "o" : "i") + "!"
					: "La zona si chiude completamente tra " + m + " second" + (m == 1 ? "o" : "i") + "!";
				server.getPlayerList().broadcastSystemMessage(
					Component.literal("⚠ " + text).withStyle(ChatFormatting.YELLOW), false);
				return;
			}
		}

		if (remaining == 0 && !announcedSeconds.contains(0))
		{
			announcedSeconds.add(0);
			server.getPlayerList().broadcastSystemMessage(
				Component.literal("⚠ La zona ha raggiunto la dimensione finale!").withStyle(ChatFormatting.RED), false);
		}
	}

	// Disegna con particelle il perimetro della zona finale, solo nei tratti
	// vicini a ciascun giocatore (per non sprecare particelle)
	private void showFinalZoneParticles()
	{
		ServerLevel level = server.overworld();
		double half = finalSizeStored / 2.0;
		double minX = centerX - half, maxX = centerX + half;
		double minZ = centerZ - half, maxZ = centerZ + half;
		double range = 64;

		for (UUID id : alivePlayers)
		{
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null)
				continue;

			double py = p.getY() + 1.2;

			// Bordi verticali (x fisso)
			for (double x : new double[] { minX, maxX })
			{
				if (Math.abs(p.getX() - x) > range)
					continue;
				double from = Math.max(minZ, p.getZ() - range);
				double to = Math.min(maxZ, p.getZ() + range);
				for (double z = from; z <= to; z += 2.0)
					level.sendParticles(p, ZONE_PARTICLE, false, x, py, z, 1, 0, 0.6, 0, 0);
			}

			// Bordi orizzontali (z fisso)
			for (double z : new double[] { minZ, maxZ })
			{
				if (Math.abs(p.getZ() - z) > range)
					continue;
				double from = Math.max(minX, p.getX() - range);
				double to = Math.min(maxX, p.getX() + range);
				for (double x = from; x <= to; x += 2.0)
					level.sendParticles(p, ZONE_PARTICLE, false, x, py, z, 1, 0, 0.6, 0, 0);
			}
		}
	}

	// ---------------------------------------------------------------
	// Lucky block
	// ---------------------------------------------------------------
	private void placeLuckyBlocks(ServerLevel level, double borderSize)
	{
		double radius = borderSize / 2.0 * 0.9;
		int target = getLuckyCount();

		for (int i = 0; i < target; i++)
		{
			for (int attempt = 0; attempt < 8; attempt++)
			{
				double x = centerX + (random.nextDouble() * 2 - 1) * radius;
				double z = centerZ + (random.nextDouble() * 2 - 1) * radius;
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				int y = groundY(level, bx, bz);

				BlockPos pos = new BlockPos(bx, y, bz);

				// Evita acqua/lava e posizioni già usate
				if (!level.getBlockState(pos.below()).getFluidState().isEmpty())
					continue;
				if (luckyBlocks.contains(pos))
					continue;

				Rollback.record(level, pos);
				level.setBlock(pos, LuckyBlockPool.pickRandom(random).defaultBlockState(), 3);
				luckyBlocks.add(pos.immutable());
				break;
			}
		}
	}

	// Chiamato dall'evento di rottura blocchi: true se la rottura va annullata
	// (cioè: era un lucky block e gli effetti li gestiamo noi)
	public boolean handleLuckyBreak(ServerPlayer player, ServerLevel level, BlockPos pos)
	{
		if (!running || !luckyBlocks.remove(pos))
			return false;

		// Con gli effetti della mod disattivati, il blocco si rompe normalmente:
		// se viene da una mod di lucky block, sarà lei a fare il suo spettacolo
		if (!LuckyBlockPool.useModEffects())
			return false;

		level.removeBlock(pos, false);
		LuckyEffects.trigger(level, player, pos);
		return true;
	}

	// ---------------------------------------------------------------
	// Fase Wither: countdown in bossbar e spawn periodico
	// ---------------------------------------------------------------
	private void updateWitherPhase()
	{
		ServerLevel level = server.overworld();
		long now = level.getGameTime();

		// Rileva la chiusura completa della zona
		if (zoneClosedTick == 0)
		{
			if (now >= shrinkEndTick)
			{
				zoneClosedTick = now;

				if (Config.WITHER_ENABLED.get())
				{
					nextWitherTick = now + Config.WITHER_DELAY_SECONDS.get() * 20L;

					witherBar = new ServerBossEvent(
						Component.literal("Wither in arrivo"),
						BossEvent.BossBarColor.PURPLE,
						BossEvent.BossBarOverlay.PROGRESS);

					for (ServerPlayer p : server.getPlayerList().getPlayers())
						witherBar.addPlayer(p);
				}
			}
			return;
		}

		if (!Config.WITHER_ENABLED.get() || nextWitherTick == 0)
			return;

		long remainingTicks = nextWitherTick - now;
		long totalTicks = (withersSpawned == 0
			? Config.WITHER_DELAY_SECONDS.get()
			: Config.WITHER_INTERVAL_SECONDS.get()) * 20L;

		if (remainingTicks <= 0)
		{
			spawnWither(level);
			withersSpawned++;
			nextWitherTick = now + Config.WITHER_INTERVAL_SECONDS.get() * 20L;
			remainingTicks = nextWitherTick - now;
			totalTicks = Config.WITHER_INTERVAL_SECONDS.get() * 20L;
		}

		if (witherBar != null)
		{
			int remSec = (int) Math.ceil(remainingTicks / 20.0);
			String label = (withersSpawned == 0 ? "☠ Primo Wither in arrivo: " : "☠ Prossimo Wither tra: ")
				+ (remSec / 60) + ":" + String.format("%02d", remSec % 60);

			witherBar.setName(Component.literal(label).withStyle(ChatFormatting.DARK_PURPLE));
			witherBar.setProgress((float) Math.max(0.0, Math.min(1.0, remainingTicks / (double) totalTicks)));
		}
	}

	private void spawnWither(ServerLevel level)
	{
		double half = finalSizeStored / 2.0 * 0.8;
		double x = centerX + (random.nextDouble() * 2 - 1) * half;
		double z = centerZ + (random.nextDouble() * 2 - 1) * half;
		int y = groundY(level, (int) Math.floor(x), (int) Math.floor(z));

		Entity wither = EntityType.WITHER.spawn(level, BlockPos.containing(x, y + 2, z), MobSpawnType.EVENT);
		if (wither != null)
			spawnedWithers.add(wither.getUUID());

		server.getPlayerList().broadcastSystemMessage(
			Component.literal("☠ Un Wither è comparso nella zona!").withStyle(ChatFormatting.DARK_PURPLE), false);
	}

	// ---------------------------------------------------------------
	// Morti, abbandoni, vittoria
	// ---------------------------------------------------------------
	public void onPlayerDeath(ServerPlayer p)
	{
		if (!running)
			return;

		UUID id = p.getUUID();
		if (!alivePlayers.remove(id))
			return;

		String teamName = playerTeams.get(id);
		if (teamName != null && !isTeamAlive(teamName))
		{
			server.getPlayerList().broadcastSystemMessage(
				teamDisplayNames.get(teamName).copy()
					.append(Component.literal(" è stato eliminato!").withStyle(ChatFormatting.RED)), false);
		}

		checkWin();
	}

	public void onPlayerRespawn(ServerPlayer p)
	{
		if (!running)
			return;

		// I partecipanti morti respawnano da spettatori
		if (playerTeams.containsKey(p.getUUID()) && !alivePlayers.contains(p.getUUID()))
			p.setGameMode(GameType.SPECTATOR);
	}

	public void onPlayerLogout(ServerPlayer p)
	{
		if (!running)
			return;

		// Chi si disconnette durante la partita conta come eliminato
		if (alivePlayers.contains(p.getUUID()))
			onPlayerDeath(p);
	}

	public void onPlayerLogin(ServerPlayer p)
	{
		if (!running)
			return;

		UUID id = p.getUUID();

		if (playerTeams.containsKey(id) && !alivePlayers.contains(id))
			p.setGameMode(GameType.SPECTATOR);
		else if (!playerTeams.containsKey(id))
			p.setGameMode(GameType.SPECTATOR);	// chi entra a partita in corso guarda

		if (witherBar != null)
			witherBar.addPlayer(p);
	}

	private boolean isTeamAlive(String teamName)
	{
		for (UUID id : alivePlayers)
		{
			if (teamName.equals(playerTeams.get(id)))
				return true;
		}
		return false;
	}

	private void checkWin()
	{
		Set<String> aliveTeams = new HashSet<>();
		for (UUID id : alivePlayers)
			aliveTeams.add(playerTeams.get(id));

		if (aliveTeams.size() > 1)
			return;

		if (aliveTeams.size() == 1)
		{
			String winner = aliveTeams.iterator().next();
			Component display = teamDisplayNames.get(winner);

			StringBuilder members = new StringBuilder();
			for (Map.Entry<UUID, String> e : playerTeams.entrySet())
			{
				if (!e.getValue().equals(winner))
					continue;
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null)
				{
					if (members.length() > 0)
						members.append(" e ");
					members.append(p.getGameProfile().getName());
				}
			}

			broadcastTitle(display.copy().append(Component.literal(" HA VINTO!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)),
				Component.literal(members.toString()).withStyle(ChatFormatting.YELLOW));

			server.getPlayerList().broadcastSystemMessage(
				display.copy().append(Component.literal(" ha vinto la battaglia! (" + members + ")").withStyle(ChatFormatting.GOLD)), false);
		}
		else
		{
			broadcastTitle(Component.literal("PAREGGIO!").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD),
				Component.literal("Nessun sopravvissuto").withStyle(ChatFormatting.DARK_GRAY));
		}

		endGame();
	}

	// ---------------------------------------------------------------
	// Fine / stop partita
	// ---------------------------------------------------------------
	public Component stop()
	{
		if (!running)
			return Component.literal("Nessuna partita in corso.");

		server.getPlayerList().broadcastSystemMessage(
			Component.literal("Partita annullata da un amministratore.").withStyle(ChatFormatting.RED), false);

		endGame();
		return Component.literal("Partita fermata e stato ripristinato.");
	}

	private void endGame()
	{
		if (server != null && Config.TEAMMATE_GLOW.get())
			sendTeammateGlow(false);

		running = false;

		if (server == null)
			return;

		// Rimuovi bossbar e Wither della partita
		if (witherBar != null)
		{
			witherBar.removeAllPlayers();
			witherBar = null;
		}

		ServerLevel witherLevel = server.overworld();
		for (UUID wid : spawnedWithers)
		{
			Entity w = witherLevel.getEntity(wid);
			if (w != null)
				w.discard();
		}
		spawnedWithers.clear();
		zoneClosedTick = 0;
		nextWitherTick = 0;
		withersSpawned = 0;

		// Rimuovi i lucky block rimasti nel mondo (solo posizioni piazzate
		// da noi e mai rotte, quindi qualunque tipo di blocco del pool)
		for (BlockPos pos : luckyBlocks)
		{
			if (!witherLevel.getBlockState(pos).isAir())
				witherLevel.removeBlock(pos, false);
		}
		luckyBlocks.clear();

		// Ripristina il bordo com'era prima della partita
		ServerLevel level = server.overworld();
		WorldBorder border = level.getWorldBorder();
		if (prevBorderSize > 0)
		{
			border.setCenter(prevBorderCenterX, prevBorderCenterZ);
			border.setSize(prevBorderSize);
			border.setDamagePerBlock(prevDamagePerBlock);
		}

		Scoreboard scoreboard = server.getScoreboard();

		// Ripristina i partecipanti: survival, vita vanilla
		for (UUID id : playerTeams.keySet())
		{
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null)
				continue;

			p.setGameMode(GameType.SURVIVAL);

			AttributeInstance attr = p.getAttribute(Attributes.MAX_HEALTH);
			if (attr != null)
				attr.setBaseValue(20.0);

			if (p.getHealth() > 20.0f)
				p.setHealth(20.0f);
		}

		// Rimuovi i team
		for (String name : teamNames)
		{
			PlayerTeam team = scoreboard.getPlayerTeam(name);
			if (team != null)
				scoreboard.removePlayerTeam(team);
		}

		playerTeams.clear();
		teamDisplayNames.clear();
		alivePlayers.clear();
		teamNames.clear();

		// Riporta la zona com'era prima della partita
		if (Rollback.isRecording() || Rollback.size() > 0)
			Rollback.startRestore();
	}

	// ---------------------------------------------------------------
	private void broadcastTitle(Component title, Component subtitle)
	{
		for (ServerPlayer p : server.getPlayerList().getPlayers())
		{
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		}
	}
}
