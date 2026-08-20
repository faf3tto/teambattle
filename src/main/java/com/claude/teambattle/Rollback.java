package com.claude.teambattle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

// Ripristino della zona: durante la partita registra lo stato ORIGINALE di
// ogni blocco che viene modificato (giornale delle modifiche), e a fine
// partita li rimette a posto un po' alla volta per non lagggare il server.
public class Rollback
{
	private record Snapshot(BlockState state, CompoundTag blockEntityNbt) {}

	// Limite di sicurezza: oltre questo numero di blocchi smette di registrare
	private static final int MAX_ENTRIES = 2_000_000;

	private static final Map<BlockPos, Snapshot> journal = new HashMap<>();
	private static final ArrayDeque<BlockPos> restoreQueue = new ArrayDeque<>();

	private static boolean recording = false;
	private static boolean warnedFull = false;
	private static ServerLevel level = null;

	public static void begin(ServerLevel lvl)
	{
		journal.clear();
		restoreQueue.clear();
		recording = true;
		warnedFull = false;
		level = lvl;
	}

	public static void stopRecording()
	{
		recording = false;
	}

	public static boolean isRecording()
	{
		return recording;
	}

	public static int size()
	{
		return journal.size();
	}

	// Registra lo stato attuale di una posizione, se non già registrata.
	// Va chiamato PRIMA che il blocco venga modificato.
	public static void record(ServerLevel lvl, BlockPos pos)
	{
		if (!recording || lvl != level || journal.containsKey(pos))
			return;

		if (journal.size() >= MAX_ENTRIES)
		{
			if (!warnedFull)
			{
				warnedFull = true;
				System.err.println("[TeamBattle] Giornale del ripristino pieno (" + MAX_ENTRIES +
					" blocchi): le modifiche successive non verranno ripristinate.");
			}
			return;
		}

		try
		{
			BlockState state = lvl.getBlockState(pos);
			CompoundTag nbt = null;
			BlockEntity be = lvl.getBlockEntity(pos);
			if (be != null)
				nbt = be.saveWithFullMetadata();

			journal.put(pos.immutable(), new Snapshot(state, nbt));
		}
		catch (Exception ignored) {}
	}

	// Avvia il ripristino graduale (chiamato a fine partita)
	public static void startRestore()
	{
		recording = false;
		restoreQueue.clear();
		restoreQueue.addAll(journal.keySet());

		if (!restoreQueue.isEmpty() && level != null)
		{
			level.getServer().getPlayerList().broadcastSystemMessage(
				Component.literal("Ripristino della zona in corso: " + restoreQueue.size() +
					" blocchi da riportare com'erano...").withStyle(ChatFormatting.GRAY), false);
		}
	}

	// Chiamato ogni tick dal GameManager: processa un lotto di blocchi
	public static void tickRestore()
	{
		if (restoreQueue.isEmpty() || level == null)
			return;

		int budget = Config.RESTORE_SPEED.get();

		while (budget-- > 0 && !restoreQueue.isEmpty())
		{
			BlockPos pos = restoreQueue.poll();
			Snapshot snap = journal.remove(pos);
			if (snap == null)
				continue;

			try
			{
				level.setBlock(pos, snap.state(), 3);

				if (snap.blockEntityNbt() != null)
				{
					BlockEntity be = level.getBlockEntity(pos);
					if (be != null)
						be.load(snap.blockEntityNbt());
				}
			}
			catch (Exception ignored) {}
		}

		if (restoreQueue.isEmpty())
		{
			journal.clear();
			level.getServer().getPlayerList().broadcastSystemMessage(
				Component.literal("Zona ripristinata!").withStyle(ChatFormatting.GREEN), false);
		}
	}
}
