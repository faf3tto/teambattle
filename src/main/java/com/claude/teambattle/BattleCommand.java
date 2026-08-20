package com.claude.teambattle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class BattleCommand
{
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
	{
		dispatcher.register(Commands.literal("battle")
			.requires(src -> src.hasPermission(2))
			.then(Commands.literal("start")
				.executes(ctx -> start(ctx,
					Config.INITIAL_BORDER_SIZE.get(),
					Config.FINAL_BORDER_SIZE.get(),
					Config.SHRINK_SECONDS.get(),
					Config.MAX_HEALTH.get()))
				.then(Commands.argument("initialSize", DoubleArgumentType.doubleArg(20, 30000))
					.executes(ctx -> start(ctx,
						DoubleArgumentType.getDouble(ctx, "initialSize"),
						Config.FINAL_BORDER_SIZE.get(),
						Config.SHRINK_SECONDS.get(),
						Config.MAX_HEALTH.get()))
					.then(Commands.argument("finalSize", DoubleArgumentType.doubleArg(5, 30000))
						.executes(ctx -> start(ctx,
							DoubleArgumentType.getDouble(ctx, "initialSize"),
							DoubleArgumentType.getDouble(ctx, "finalSize"),
							Config.SHRINK_SECONDS.get(),
							Config.MAX_HEALTH.get()))
						.then(Commands.argument("shrinkSeconds", IntegerArgumentType.integer(10, 86400))
							.executes(ctx -> start(ctx,
								DoubleArgumentType.getDouble(ctx, "initialSize"),
								DoubleArgumentType.getDouble(ctx, "finalSize"),
								IntegerArgumentType.getInteger(ctx, "shrinkSeconds"),
								Config.MAX_HEALTH.get()))
							.then(Commands.argument("maxHealth", DoubleArgumentType.doubleArg(1, 1024))
								.executes(ctx -> start(ctx,
									DoubleArgumentType.getDouble(ctx, "initialSize"),
									DoubleArgumentType.getDouble(ctx, "finalSize"),
									IntegerArgumentType.getInteger(ctx, "shrinkSeconds"),
									DoubleArgumentType.getDouble(ctx, "maxHealth"))))))))
			.then(Commands.literal("center")
				.then(Commands.literal("spawn")
					.executes(ctx -> {
						GameManager.INSTANCE.setCenterOverride(Config.CenterMode.SPAWN, 0, 0);
						ctx.getSource().sendSuccess(() -> GameManager.INSTANCE.describeCenter(), true);
						return 1;
					}))
				.then(Commands.literal("random")
					.executes(ctx -> {
						GameManager.INSTANCE.setCenterOverride(Config.CenterMode.RANDOM, 0, 0);
						ctx.getSource().sendSuccess(() -> GameManager.INSTANCE.describeCenter(), true);
						return 1;
					}))
				.then(Commands.argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000))
					.then(Commands.argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000))
						.executes(ctx -> {
							GameManager.INSTANCE.setCenterOverride(Config.CenterMode.CUSTOM,
								DoubleArgumentType.getDouble(ctx, "x"),
								DoubleArgumentType.getDouble(ctx, "z"));
							ctx.getSource().sendSuccess(() -> GameManager.INSTANCE.describeCenter(), true);
							return 1;
						})))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> GameManager.INSTANCE.describeCenter(), false);
					return 1;
				}))
			.then(Commands.literal("kit")
				.then(Commands.literal("set")
					.executes(ctx -> {
						var player = ctx.getSource().getPlayerOrException();
						KitManager.captureFrom(player);
						ctx.getSource().sendSuccess(() -> Component.literal(
							"Kit iniziale salvato dal tuo inventario (" + KitManager.itemCount() +
							" oggetti, armatura e mano secondaria comprese)."), true);
						return 1;
					}))
				.then(Commands.literal("clear")
					.executes(ctx -> {
						KitManager.clear();
						ctx.getSource().sendSuccess(() -> Component.literal(
							"Kit iniziale svuotato: i giocatori partiranno a mani vuote."), true);
						return 1;
					}))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal(
						"Kit iniziale attuale: " + KitManager.itemCount() + " oggetti." +
						"\nRiempi il TUO inventario come vuoi che partano i giocatori e usa /battle kit set." +
						"\n/battle kit clear per svuotarlo."), false);
					return 1;
				}))
			.then(Commands.literal("teamsize")
				.then(Commands.argument("size", IntegerArgumentType.integer(1, 16))
					.executes(ctx -> {
						int size = IntegerArgumentType.getInteger(ctx, "size");
						GameManager.INSTANCE.setTeamSizeOverride(size);
						ctx.getSource().sendSuccess(() -> Component.literal(
							size == 1
								? "Team da 1: modalità tutti contro tutti!"
								: "Dimensione dei team impostata a " + size + " membri."), true);
						return 1;
					}))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal(
						"Dimensione attuale dei team: " + GameManager.INSTANCE.getTeamSize() +
						". Cambiala con /battle teamsize <numero> (1-16)."), false);
					return 1;
				}))
			.then(Commands.literal("luckyblocks")
				.then(Commands.literal("on")
					.executes(ctx -> {
						GameManager.INSTANCE.setLuckyOverride(true);
						ctx.getSource().sendSuccess(() -> Component.literal(
							"🍀 Modalità Lucky Block ATTIVATA per le prossime partite (" +
							LuckyEffects.count() + " effetti caricati)."), true);
						return 1;
					}))
				.then(Commands.literal("off")
					.executes(ctx -> {
						GameManager.INSTANCE.setLuckyOverride(false);
						ctx.getSource().sendSuccess(() -> Component.literal(
							"Modalità Lucky Block disattivata."), true);
						return 1;
					}))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal(
						"Lucky Block: " + (GameManager.INSTANCE.isLuckyEnabled() ? "ATTIVI" : "disattivati") +
						" — " + Config.LUCKY_COUNT.get() + " blocchi per partita, " +
						LuckyEffects.count() + " effetti disponibili." +
						"\nUsa /battle luckyblocks on oppure off."), false);
					return 1;
				}))
			.then(Commands.literal("stop")
				.executes(ctx -> {
					Component result = GameManager.INSTANCE.stop();
					ctx.getSource().sendSuccess(() -> result, true);
					return 1;
				}))
			.then(Commands.literal("config")
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.literal(
						"Config attuale (config/teambattle-common.toml):" +
						"\n- initialBorderSize: " + Config.INITIAL_BORDER_SIZE.get() +
						"\n- finalBorderSize: " + Config.FINAL_BORDER_SIZE.get() +
						"\n- shrinkSeconds: " + Config.SHRINK_SECONDS.get() +
						"\n- maxHealth: " + Config.MAX_HEALTH.get() +
						"\n- borderDamagePerBlock: " + Config.BORDER_DAMAGE_PER_BLOCK.get() +
						"\n- minTeamSeparation: " + Config.MIN_TEAM_SEPARATION.get() +
						"\n- centerMode: " + Config.CENTER_MODE.get() +
						"\n- teammateGlow: " + Config.TEAMMATE_GLOW.get() +
						"\n- announceShrink: " + Config.ANNOUNCE_SHRINK.get() +
						"\n- showFinalZone: " + Config.SHOW_FINAL_ZONE.get() +
						"\nUso: /battle start [initialSize] [finalSize] [shrinkSeconds] [maxHealth]" +
						"\n- teamSize: " + GameManager.INSTANCE.getTeamSize() +
						"\n- witherEnabled: " + Config.WITHER_ENABLED.get() +
						" (primo dopo " + Config.WITHER_DELAY_SECONDS.get() + "s, poi ogni " + Config.WITHER_INTERVAL_SECONDS.get() + "s)" +
						"\nCentro: /battle center spawn | random | <x> <z>" +
						"\nKit: /battle kit set | clear"), false);
					return 1;
				})));
	}

	private static int start(CommandContext<CommandSourceStack> ctx, double initial, double fin, int seconds, double maxHealth)
	{
		if (fin > initial)
		{
			ctx.getSource().sendFailure(Component.literal("La dimensione finale deve essere minore di quella iniziale."));
			return 0;
		}

		Component result = GameManager.INSTANCE.start(ctx.getSource().getServer(), initial, fin, seconds, maxHealth);
		ctx.getSource().sendSuccess(() -> result, true);
		return 1;
	}
}
