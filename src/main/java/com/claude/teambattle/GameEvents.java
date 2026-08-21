package com.claude.teambattle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class GameEvents
{
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event)
	{
		BattleCommand.register(event.getDispatcher(), event.getBuildContext());
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END)
			GameManager.INSTANCE.tick();
	}

	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event)
	{
		if (!(event.getPlayer() instanceof ServerPlayer player)
			|| !(event.getLevel() instanceof ServerLevel level))
			return;

		if (GameManager.INSTANCE.handleLuckyBreak(player, level, event.getPos()))
		{
			event.setCanceled(true);
			return;
		}

		// Registra il blocco prima che venga distrutto, per il ripristino
		Rollback.record(level, event.getPos());
	}

	@SubscribeEvent
	public static void onBlockPlace(BlockEvent.EntityPlaceEvent event)
	{
		// L'evento scatta DOPO il piazzamento: lo stato originale (di solito
		// aria) va preso dallo snapshot del blocco sostituito, così a fine
		// partita i blocchi piazzati dai giocatori vengono rimossi
		if (event.getLevel() instanceof ServerLevel level)
			Rollback.record(level, event.getPos(), event.getBlockSnapshot().getReplacedBlock());
	}

	@SubscribeEvent
	public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event)
	{
		// Piazzamenti che occupano piu' blocchi in un colpo (porte, letti...)
		if (!(event.getLevel() instanceof ServerLevel level))
			return;

		for (var snap : event.getReplacedBlockSnapshots())
			Rollback.record(level, snap.getPos(), snap.getReplacedBlock());
	}

	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load event)
	{
		if (event.getLevel() instanceof ServerLevel level)
		{
			var pos = event.getChunk().getPos();
			GameManager.INSTANCE.onChunkLoad(level, pos.x, pos.z);
		}
	}

	@SubscribeEvent
	public static void onExplosion(ExplosionEvent.Detonate event)
	{
		if (!(event.getLevel() instanceof ServerLevel level))
			return;

		for (BlockPos pos : event.getAffectedBlocks())
			Rollback.record(level, pos);
	}

	@SubscribeEvent
	public static void onLivingDestroyBlock(LivingDestroyBlockEvent event)
	{
		if (event.getEntity().level() instanceof ServerLevel level)
			Rollback.record(level, event.getPos());
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event)
	{
		if (event.getEntity() instanceof ServerPlayer player)
			GameManager.INSTANCE.onPlayerDeath(player);
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
	{
		if (event.getEntity() instanceof ServerPlayer player)
			GameManager.INSTANCE.onPlayerRespawn(player);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
	{
		if (event.getEntity() instanceof ServerPlayer player)
			GameManager.INSTANCE.onPlayerLogout(player);
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
	{
		if (event.getEntity() instanceof ServerPlayer player)
			GameManager.INSTANCE.onPlayerLogin(player);
	}
}
