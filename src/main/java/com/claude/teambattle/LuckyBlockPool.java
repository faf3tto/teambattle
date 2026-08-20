package com.claude.teambattle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Pool dei blocchi usati come lucky block, con pesi per la rarità.
// Vuoto = si usa il blocco d'oro di default. Salvato in
// config/teambattle-luckyblocks.json, quindi sopravvive ai riavvii.
public class LuckyBlockPool
{
	public static class Entry
	{
		public final String id;
		public final int weight;

		Entry(String id, int weight)
		{
			this.id = id;
			this.weight = Math.max(1, weight);
		}
	}

	private static final List<Entry> entries = new ArrayList<>();
	// true = alla rottura scattano i 500 effetti di questa mod;
	// false = il blocco si rompe normalmente (gestito dalla sua mod di origine)
	private static boolean modEffects = true;
	private static boolean loaded = false;

	private static File file()
	{
		return FMLPaths.CONFIGDIR.get().resolve("teambattle-luckyblocks.json").toFile();
	}

	public static synchronized void addOrUpdate(String id, int weight)
	{
		ensureLoaded();
		entries.removeIf(e -> e.id.equals(id));
		entries.add(new Entry(id, weight));
		save();
	}

	public static synchronized boolean remove(String id)
	{
		ensureLoaded();
		boolean removed = entries.removeIf(e -> e.id.equals(id));
		if (removed)
			save();
		return removed;
	}

	public static synchronized List<Entry> list()
	{
		ensureLoaded();
		return new ArrayList<>(entries);
	}

	public static boolean useModEffects()
	{
		ensureLoaded();
		return modEffects;
	}

	public static void setModEffects(boolean v)
	{
		ensureLoaded();
		modEffects = v;
		save();
	}

	// Estrazione pesata: i blocchi di mod non installate vengono ignorati
	public static Block pickRandom(Random random)
	{
		ensureLoaded();

		List<Block> blocks = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;

		for (Entry e : entries)
		{
			Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(e.id));
			if (b == Blocks.AIR)
				continue;
			blocks.add(b);
			weights.add(e.weight);
			total += e.weight;
		}

		if (blocks.isEmpty() || total <= 0)
			return Blocks.GOLD_BLOCK;

		int roll = random.nextInt(total);
		for (int i = 0; i < blocks.size(); i++)
		{
			roll -= weights.get(i);
			if (roll < 0)
				return blocks.get(i);
		}
		return blocks.get(blocks.size() - 1);
	}

	private static void ensureLoaded()
	{
		if (!loaded)
			load();
	}

	private static void save()
	{
		try (FileWriter w = new FileWriter(file()))
		{
			JsonObject root = new JsonObject();
			root.addProperty("effectsEnabled", modEffects);

			JsonArray arr = new JsonArray();
			for (Entry e : entries)
			{
				JsonObject o = new JsonObject();
				o.addProperty("block", e.id);
				o.addProperty("weight", e.weight);
				arr.add(o);
			}
			root.add("blocks", arr);

			new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
		}
		catch (Exception ex)
		{
			System.err.println("[TeamBattle] Impossibile salvare il pool dei lucky block: " + ex);
		}
	}

	private static void load()
	{
		loaded = true;
		entries.clear();
		modEffects = true;

		File f = file();
		if (!f.exists())
			return;

		try (FileReader r = new FileReader(f))
		{
			JsonObject root = new Gson().fromJson(r, JsonObject.class);
			if (root == null)
				return;

			if (root.has("effectsEnabled"))
				modEffects = root.get("effectsEnabled").getAsBoolean();

			JsonArray arr = root.getAsJsonArray("blocks");
			if (arr != null)
			{
				for (int i = 0; i < arr.size(); i++)
				{
					JsonObject o = arr.get(i).getAsJsonObject();
					entries.add(new Entry(o.get("block").getAsString(), o.get("weight").getAsInt()));
				}
			}
		}
		catch (Exception ex)
		{
			System.err.println("[TeamBattle] Impossibile caricare il pool dei lucky block: " + ex);
		}
	}
}
