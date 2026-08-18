package com.claude.teambattle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class GameEvents
{
	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event)
	{
		BattleCommand.register(event.getDispatcher());
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END)
			GameManager.INSTANCE.tick();
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
