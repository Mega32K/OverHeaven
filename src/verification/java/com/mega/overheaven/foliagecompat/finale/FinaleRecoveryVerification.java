package com.mega.overheaven.foliagecompat.finale;

import java.nio.file.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;


public final class FinaleRecoveryVerification {
    public static void main(String[] args)throws Exception {
        Path directory=Files.createTempDirectory("overheaven-recovery-verification-"); Path file=directory.resolve("journal.dat");
        var journal=new FinaleJournal(file); var key=new FinaleJournal.Key("minecraft:overworld",new BlockPos(4,70,-9).asLong()); var original=new CompoundTag(); original.putString("Name","minecraft:red_stained_glass_pane"); var properties=new CompoundTag(); properties.putString("north","true"); properties.putString("waterlogged","false"); original.put("Properties",properties);
        journal.entries.put(key,new FinaleJournal.Entry("run-1",original)); journal.flush();
        var restart=new FinaleJournal(file); if(!restart.entries.get(key).serialized().equals(original) || !restart.locked(key.dimension(),BlockPos.of(key.position()))) throw new AssertionError("crash recovery snapshot");
        byte[] before=Files.readAllBytes(file); Files.writeString(directory.resolve("journal.dat.tmp"),"incomplete interrupted generation");
        if(!new FinaleJournal(file).entries.get(key).serialized().equals(original)) throw new AssertionError("temp must not supersede committed journal");
        restart.entries.clear(); restart.flush(); if(!new FinaleJournal(file).entries.isEmpty()) throw new AssertionError("retired journal");
        Files.write(file,before); if(new FinaleJournal(file).entries.size()!=1) throw new AssertionError("restart retained restoration");
        Files.delete(directory.resolve("journal.dat")); Files.deleteIfExists(directory.resolve("journal.dat.tmp")); Files.delete(directory);
        System.out.println("Recovery journal actual NBT/disk roundtrip, interrupted write and retirement passed");
    }
}
