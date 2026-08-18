package com.claude.teambattle;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.network.NetworkConstants;

@Mod(TeamBattleMod.MODID)
public class TeamBattleMod
{
	public static final String MODID = "teambattle";

	public TeamBattleMod()
	{
		// Config (file: config/teambattle-common.toml, generato al primo avvio)
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

		// Mod solo server: i client vanilla possono connettersi senza averla
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
			() -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));

		MinecraftForge.EVENT_BUS.register(GameEvents.class);
	}
}
