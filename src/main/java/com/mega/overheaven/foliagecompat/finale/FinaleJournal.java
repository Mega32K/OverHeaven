package com.mega.overheaven.foliagecompat.finale;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/** Durable write-ahead reservation. No world edit is permitted before flush succeeds. */
public final class FinaleJournal {
    public record Key(String dimension,long position) { }
    public static final class Entry {
        private final String run;
        private final CompoundTag state;
        private BlockState decoded;
        public Entry(String run,BlockState original) { this(run,NbtUtils.writeBlockState(original)); decoded=original; }
        public Entry(String run,CompoundTag original) {
            if(net.minecraft.resources.ResourceLocation.tryParse(original.getString("Name"))==null || original.getString("Name").equals("minecraft:air")) throw new IllegalArgumentException("Invalid recovery block identity");
            this.run=run; state=original.copy();
        }
        public String run() { return run; }
        public CompoundTag serialized() { return state.copy(); }
        public BlockState original() {
            if(decoded==null) { decoded=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),state); if(decoded.isAir()) { decoded=null; throw new IllegalStateException("Unknown recovery block "+state.getString("Name")); } }
            return decoded;
        }
    }
    private final Path file;
    final Map<Key,Entry> entries=new LinkedHashMap<>();
    public FinaleJournal(MinecraftServer server) throws IOException { this(server.getWorldPath(LevelResource.ROOT).resolve("data/overheaven_finale_recovery.dat")); }
    public FinaleJournal(Path file) throws IOException {
        this.file=file;
        if(Files.exists(file)) read(NbtIo.readCompressed(file.toFile()));
    }
    private void read(CompoundTag tag) throws IOException {
        if(tag==null || tag.getInt("schema")!=1) throw new IOException("Unsupported finale recovery journal; retained at "+file);
        ListTag list=tag.getList("blocks",10);
        if(list.size()>65536) throw new IOException("Recovery journal too large");
        for(int i=0;i<list.size();i++) {
            CompoundTag value=list.getCompound(i);
            try {
                if(net.minecraft.resources.ResourceLocation.tryParse(value.getString("dimension"))==null) throw new IllegalArgumentException("Invalid recovery dimension");
                var key=new Key(value.getString("dimension"),value.getLong("position"));
                if(entries.putIfAbsent(key,new Entry(value.getString("run"),value.getCompound("state")))!=null) throw new IllegalArgumentException("Duplicate recovery coordinate");
            }
            catch(IllegalArgumentException error) { throw new IOException("Invalid recovery state; journal retained",error); }
        }
    }
    public void flush() throws IOException {
        CompoundTag root=new CompoundTag(); root.putInt("schema",1); ListTag list=new ListTag();
        entries.forEach((key,entry)-> { CompoundTag value=new CompoundTag(); value.putString("dimension",key.dimension()); value.putLong("position",key.position()); value.putString("run",entry.run()); value.put("state",entry.serialized()); list.add(value); }); root.put("blocks",list);
        Files.createDirectories(file.getParent()); Path temp=file.resolveSibling(file.getFileName()+".tmp");
        NbtIo.writeCompressed(root,temp.toFile());
        try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)) { channel.force(true); }
        // Do not fall back to delete-then-rename: a crash must retain either complete generation.
        Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public boolean locked(String dimension,BlockPos pos) { return entries.containsKey(new Key(dimension,pos.asLong())); }
    public int count(String dimension) { return (int)entries.keySet().stream().filter(k->k.dimension().equals(dimension)).count(); }
}
