package com.claude.teambattle;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Motore dei Lucky Block: carica il catalogo di 500 effetti da
// /assets/teambattle/luckyblock_effects.json ed esegue l'effetto estratto
// quando un giocatore rompe uno dei blocchi piazzati dalla partita.
public class LuckyEffects
{
	private static final Random random = new Random();
	private static List<JsonObject> effects = null;

	private static void ensureLoaded()
	{
		if (effects != null)
			return;

		effects = new ArrayList<>();

		try (InputStream in = LuckyEffects.class.getResourceAsStream("/assets/teambattle/luckyblock_effects.json"))
		{
			if (in == null)
			{
				System.err.println("[TeamBattle] Catalogo lucky block non trovato!");
				return;
			}

			JsonArray arr = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonArray.class);
			for (int i = 0; i < arr.size(); i++)
				effects.add(arr.get(i).getAsJsonObject());

			System.out.println("[TeamBattle] Caricati " + effects.size() + " effetti lucky block.");
		}
		catch (Exception ex)
		{
			System.err.println("[TeamBattle] Errore caricando gli effetti lucky block: " + ex);
		}
	}

	public static int count()
	{
		ensureLoaded();
		return effects.size();
	}

	public static void trigger(ServerLevel level, ServerPlayer player, BlockPos pos)
	{
		ensureLoaded();
		if (effects.isEmpty())
			return;

		JsonObject e = effects.get(random.nextInt(effects.size()));

		String msg = str(e, "m", "");
		if (!msg.isEmpty())
		{
			player.sendSystemMessage(Component.literal("🍀 " + msg).withStyle(ChatFormatting.GOLD));
		}

		try
		{
			execute(e, level, player, pos);
		}
		catch (Exception ex)
		{
			System.err.println("[TeamBattle] Errore effetto lucky block '" + msg + "': " + ex);
		}
	}

	// ---------------------------------------------------------------
	private static void execute(JsonObject e, ServerLevel level, ServerPlayer p, BlockPos pos)
	{
		String type = str(e, "t", "msg");
		double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;

		switch (type)
		{
		case "give":
		{
			ItemStack stack = makeStack(e);
			if (!stack.isEmpty())
				dropAt(level, x, y + 0.5, z, stack);
			break;
		}
		case "rain":
		{
			// Oggetti che piovono sparsi attorno al blocco
			ItemStack proto = makeStack(e);
			int total = num(e, "c", 8);
			for (int i = 0; i < total; i++)
			{
				ItemStack one = proto.copy();
				one.setCount(1);
				dropAt(level,
					x + (random.nextDouble() * 6 - 3),
					y + 4 + random.nextDouble() * 3,
					z + (random.nextDouble() * 6 - 3), one);
			}
			break;
		}
		case "chest":
		{
			Rollback.record(level, pos);
			level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof ChestBlockEntity chest)
			{
				JsonArray loot = e.getAsJsonArray("loot");
				if (loot != null)
				{
					for (int i = 0; i < loot.size() && i < 27; i++)
					{
						JsonObject it = loot.get(i).getAsJsonObject();
						chest.setItem(random.nextInt(27), makeStack(it));
					}
				}
			}
			break;
		}
		case "mob":
		{
			String id = str(e, "i", "minecraft:zombie");
			int count = num(e, "c", 1);
			String name = str(e, "n", "");
			boolean baby = bool(e, "baby");
			boolean charged = bool(e, "charged");

			EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(id));
			for (int i = 0; i < count; i++)
			{
				Entity ent = et.spawn(level, pos.above(), MobSpawnType.EVENT);
				if (ent == null)
					continue;
				if (!name.isEmpty())
				{
					ent.setCustomName(Component.literal(name));
					ent.setCustomNameVisible(true);
				}
				if (baby && ent instanceof Mob mob)
					mob.setBaby(true);
				if (charged && ent instanceof net.minecraft.world.entity.monster.Creeper creeper)
				{
					// Il flag "carico" non ha un setter pubblico: si imposta via NBT
					var tag = new net.minecraft.nbt.CompoundTag();
					creeper.saveWithoutId(tag);
					tag.putBoolean("powered", true);
					creeper.load(tag);
				}
			}
			break;
		}
		case "fx":
		{
			applyEffect(p, e);
			break;
		}
		case "fxcombo":
		{
			JsonArray list = e.getAsJsonArray("list");
			if (list != null)
				for (int i = 0; i < list.size(); i++)
					applyEffect(p, list.get(i).getAsJsonObject());
			break;
		}
		case "fxall":
		{
			double r = num(e, "r", 20);
			for (ServerPlayer other : level.players())
				if (other.distanceToSqr(x, y, z) <= r * r)
					applyEffect(other, e);
			break;
		}
		case "tnt":
		{
			int count = num(e, "c", 1);
			int fuse = num(e, "fuse", 60);
			for (int i = 0; i < count; i++)
			{
				PrimedTnt tnt = new PrimedTnt(level,
					x + (random.nextDouble() * 2 - 1) * (count > 1 ? 2 : 0),
					y + 1, z + (random.nextDouble() * 2 - 1) * (count > 1 ? 2 : 0), null);
				tnt.setFuse(fuse);
				level.addFreshEntity(tnt);
			}
			break;
		}
		case "lightning":
		{
			int count = num(e, "c", 1);
			for (int i = 0; i < count; i++)
			{
				var bolt = EntityType.LIGHTNING_BOLT.create(level);
				if (bolt != null)
				{
					bolt.moveTo(x + (random.nextDouble() * 4 - 2), y, z + (random.nextDouble() * 4 - 2));
					level.addFreshEntity(bolt);
				}
			}
			break;
		}
		case "fireworks":
		{
			int count = num(e, "c", 3);
			for (int i = 0; i < count; i++)
			{
				try
				{
					ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);
					fw.setTag(TagParser.parseTag(
						"{Fireworks:{Flight:1b,Explosions:[{Type:" + random.nextInt(5) +
						"b,Colors:[I;" + (0x100000 + random.nextInt(0xEFFFFF)) + "]}]}}"));
					level.addFreshEntity(new FireworkRocketEntity(level,
						x + random.nextDouble() * 2 - 1, y + 1, z + random.nextDouble() * 2 - 1, fw));
				}
				catch (Exception ignored) {}
			}
			break;
		}
		case "xp":
		{
			p.giveExperienceLevels(num(e, "l", 5));
			break;
		}
		case "launch":
		{
			double power = dbl(e, "p", 1.5);
			p.setDeltaMovement(p.getDeltaMovement().x, power, p.getDeltaMovement().z);
			p.hurtMarked = true;	// forza l'invio del movimento al client
			if (bool(e, "slowfall"))
				p.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20 * 15, 0));
			break;
		}
		case "tprandom":
		{
			double r = num(e, "r", 30);
			double nx = x + (random.nextDouble() * 2 - 1) * r;
			double nz = z + (random.nextDouble() * 2 - 1) * r;
			int ny = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(int) Math.floor(nx), (int) Math.floor(nz));
			p.teleportTo(level, nx, ny, nz, p.getYRot(), p.getXRot());
			break;
		}
		case "swap":
		{
			List<ServerPlayer> others = new ArrayList<>(level.players());
			others.removeIf(o -> o == p || o.isSpectator());
			if (!others.isEmpty())
			{
				ServerPlayer other = others.get(random.nextInt(others.size()));
				double ox = other.getX(), oy = other.getY(), oz = other.getZ();
				other.teleportTo(level, p.getX(), p.getY(), p.getZ(), other.getYRot(), other.getXRot());
				p.teleportTo(level, ox, oy, oz, p.getYRot(), p.getXRot());
				other.sendSystemMessage(Component.literal("🍀 Ti sei scambiato di posto con " +
					p.getGameProfile().getName() + "!").withStyle(ChatFormatting.GOLD));
			}
			break;
		}
		case "blockring":
		{
			// Anello/gabbia di blocchi attorno al giocatore
			String id = str(e, "i", "minecraft:glass");
			var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
			BlockPos c = p.blockPosition();
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++)
					for (int dy = 0; dy <= 2; dy++)
					{
						boolean wall = Math.abs(dx) == 1 || Math.abs(dz) == 1;
						boolean cap = dy == 2 && dx == 0 && dz == 0;
						if (wall || cap)
						{
							BlockPos bp = c.offset(dx, dy, dz);
							if (level.getBlockState(bp).isAir())
							{
								Rollback.record(level, bp);
								level.setBlock(bp, block.defaultBlockState(), 3);
							}
						}
					}
			break;
		}
		case "pool":
		{
			// Pozza di liquido o blocchi sotto i piedi
			String id = str(e, "i", "minecraft:water");
			var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
			int r = num(e, "r", 2);
			BlockPos c = p.blockPosition();
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++)
					if (dx * dx + dz * dz <= r * r)
					{
						BlockPos bp = c.offset(dx, -1, dz);
						Rollback.record(level, bp);
						level.setBlock(bp, block.defaultBlockState(), 3);
					}
			break;
		}
		case "anvils":
		{
			int count = num(e, "c", 3);
			for (int i = 0; i < count; i++)
			{
				BlockPos bp = p.blockPosition().offset(random.nextInt(3) - 1, 6 + random.nextInt(3), random.nextInt(3) - 1);
				if (level.getBlockState(bp).isAir())
				{
					Rollback.record(level, bp);
					level.setBlock(bp, Blocks.ANVIL.defaultBlockState(), 3);
				}
			}
			break;
		}
		case "arrows":
		{
			int count = num(e, "c", 10);
			for (int i = 0; i < count; i++)
			{
				var arrow = EntityType.ARROW.create(level);
				if (arrow != null)
				{
					arrow.moveTo(x + random.nextDouble() * 8 - 4, y + 12, z + random.nextDouble() * 8 - 4);
					arrow.setDeltaMovement(0, -0.5, 0);
					level.addFreshEntity(arrow);
				}
			}
			break;
		}
		case "sound":
		{
			String id = str(e, "i", "minecraft:entity.ghast.scream");
			SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(id));
			if (sound != null)
				level.playSound(null, pos, sound, SoundSource.MASTER, 2.0f,
					0.8f + random.nextFloat() * 0.4f);
			break;
		}
		case "heal":
		{
			p.setHealth(p.getMaxHealth());
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(10f);
			break;
		}
		case "dropinv":
		{
			// Fa cadere a terra un oggetto a caso dell'inventario
			List<Integer> filled = new ArrayList<>();
			for (int i = 0; i < 36; i++)
				if (!p.getInventory().getItem(i).isEmpty())
					filled.add(i);
			if (!filled.isEmpty())
			{
				int slot = filled.get(random.nextInt(filled.size()));
				ItemStack s = p.getInventory().getItem(slot);
				p.getInventory().setItem(slot, ItemStack.EMPTY);
				dropAt(level, x, y + 1, z, s);
			}
			break;
		}
		case "msg":
		default:
			// Solo messaggio: già inviato sopra
			break;
		}
	}

	// ---------------------------------------------------------------
	private static void applyEffect(ServerPlayer p, JsonObject e)
	{
		String id = str(e, "i", "minecraft:speed");
		int seconds = num(e, "d", 15);
		int amp = num(e, "a", 0);

		MobEffect eff = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(id));
		if (eff != null)
			p.addEffect(new MobEffectInstance(eff, seconds * 20, amp));
	}

	private static ItemStack makeStack(JsonObject e)
	{
		String id = str(e, "i", "minecraft:stone");
		int count = num(e, "c", 1);

		var item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
		ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, count)));

		String name = str(e, "n", "");
		if (!name.isEmpty())
			stack.setHoverName(Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE));

		JsonArray ench = e.getAsJsonArray("e");
		if (ench != null)
		{
			for (int i = 0; i < ench.size(); i++)
			{
				JsonArray pair = ench.get(i).getAsJsonArray();
				Enchantment en = BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation(pair.get(0).getAsString()));
				if (en != null)
					stack.enchant(en, pair.get(1).getAsInt());
			}
		}

		return stack;
	}

	private static void dropAt(ServerLevel level, double x, double y, double z, ItemStack stack)
	{
		ItemEntity item = new ItemEntity(level, x, y, z, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	private static String str(JsonObject o, String k, String def)
	{
		return o.has(k) ? o.get(k).getAsString() : def;
	}

	private static int num(JsonObject o, String k, int def)
	{
		return o.has(k) ? o.get(k).getAsInt() : def;
	}

	private static double dbl(JsonObject o, String k, double def)
	{
		return o.has(k) ? o.get(k).getAsDouble() : def;
	}

	private static boolean bool(JsonObject o, String k)
	{
		return o.has(k) && o.get(k).getAsBoolean();
	}
}
