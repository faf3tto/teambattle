package com.claude.teambattle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

// Gestisce il kit iniziale: viene catturato dall'inventario di chi esegue
// /battle kit set (inclusi armatura e mano secondaria) e salvato su file,
// così sopravvive ai riavvii del server.
public class KitManager
{
	// Slot 0-35 = inventario, 36-39 = armatura, 40 = mano secondaria
	private static final int TOTAL_SLOTS = 41;

	private static final Map<Integer, ItemStack> kit = new HashMap<>();
	private static boolean loaded = false;

	private static File file()
	{
		return FMLPaths.CONFIGDIR.get().resolve("teambattle-kit.nbt").toFile();
	}

	public static void captureFrom(ServerPlayer p)
	{
		kit.clear();

		for (int i = 0; i < TOTAL_SLOTS; i++)
		{
			ItemStack s = p.getInventory().getItem(i);
			if (!s.isEmpty())
				kit.put(i, s.copy());
		}

		loaded = true;
		save();
	}

	public static void applyTo(ServerPlayer p)
	{
		ensureLoaded();

		for (Map.Entry<Integer, ItemStack> e : kit.entrySet())
			p.getInventory().setItem(e.getKey(), e.getValue().copy());

		p.inventoryMenu.broadcastChanges();
	}

	public static void clear()
	{
		kit.clear();
		loaded = true;
		save();
	}

	public static int itemCount()
	{
		ensureLoaded();
		return kit.size();
	}

	private static void ensureLoaded()
	{
		if (!loaded)
			load();
	}

	private static void save()
	{
		try
		{
			CompoundTag root = new CompoundTag();
			ListTag list = new ListTag();

			for (Map.Entry<Integer, ItemStack> e : kit.entrySet())
			{
				CompoundTag itemTag = e.getValue().save(new CompoundTag());
				itemTag.putInt("KitSlot", e.getKey());
				list.add(itemTag);
			}

			root.put("Items", list);
			NbtIo.write(root, file());
		}
		catch (Exception ex)
		{
			System.err.println("[TeamBattle] Impossibile salvare il kit: " + ex);
		}
	}

	private static void load()
	{
		loaded = true;
		kit.clear();

		try
		{
			File f = file();
			if (!f.exists())
				return;

			CompoundTag root = NbtIo.read(f);
			if (root == null)
				return;

			ListTag list = root.getList("Items", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++)
			{
				CompoundTag itemTag = list.getCompound(i);
				int slot = itemTag.getInt("KitSlot");
				ItemStack s = ItemStack.of(itemTag);
				if (!s.isEmpty() && slot >= 0 && slot < TOTAL_SLOTS)
					kit.put(slot, s);
			}
		}
		catch (Exception ex)
		{
			System.err.println("[TeamBattle] Impossibile caricare il kit: " + ex);
		}
	}
}
