package com.mega.overheaven.foliagecompat.blackhole.client;

import com.google.common.collect.ImmutableSet;
import com.mega.overheaven.foliagecompat.PatchLog;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CapturedRenderingState;

public final class OculusBridge {
    private static String warned="";
    private static Object checkedPack;
    private static boolean fingerprint;
    private static final it.unimi.dsi.fastutil.ints.IntSet REFRACTION_PROGRAMS=new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    private static boolean pending;
    public static boolean active() { return IrisApi.getInstance().isShaderPackInUse(); }
    public static boolean shadow() { return IrisApi.getInstance().isRenderingShadowPass(); }
    private static int texture(RenderTargets targets,ImmutableSet<Integer> flipped,int index) {
        var target=targets.get(index);
        if(target==null) throw new IllegalStateException("Missing Oculus colortex"+index);
        return flipped.contains(index)?target.getAltTexture():target.getMainTexture();
    }
    private static boolean supported(RenderTargets targets) {
        if(!active() || shadow() || !BlackHoleRenderer.INSTANCE.active()) return false;
        String pack=Iris.getCurrentPackName();
        var color=targets.get(0); var surface=targets.get(3);
        var main=net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        boolean supported="ComplementaryUnbound_r5.9.3.zip".equals(pack) && fingerprint() && !REFRACTION_PROGRAMS.isEmpty() && color!=null && surface!=null && color.getWidth()==surface.getWidth() && color.getHeight()==surface.getHeight() && color.getWidth()==main.width && color.getHeight()==main.height;
        if(!supported && !warned.equals(pack)) { warned=pack; PatchLog.LOGGER.error("Black hole Oculus adapter disabled: pack/hash/program/buffer layout mismatch '{}'; expected audited ComplementaryUnbound_r5.9.3.zip, composite1, full-size colortex0/3",pack); }
        return supported;
    }
    private static boolean fingerprint() {
        Object pack=Iris.getCurrentPack().orElse(null);
        if(pack==checkedPack) return fingerprint;
        checkedPack=pack; fingerprint=false;
        try(var input=java.nio.file.Files.newInputStream(Iris.getShaderpacksDirectory().resolve("ComplementaryUnbound_r5.9.3.zip"))) {
            var digest=java.security.MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[65536]; int count;
            while((count=input.read(buffer))!=-1) digest.update(buffer,0,count);
            fingerprint=java.util.HexFormat.of().formatHex(digest.digest()).equals("98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a");
        } catch(Exception exception) { PatchLog.LOGGER.error("Cannot verify black hole shaderpack adapter: {}",exception.toString()); }
        return fingerprint;
    }
    public static void before(RenderTargets targets,ImmutableSet<Integer> flipped) {
        pending=false;
        if(!supported(targets)) return;
        var color=targets.get(0);
        BlackHoleRenderer.INSTANCE.matrices(CapturedRenderingState.INSTANCE.getGbufferModelView(),CapturedRenderingState.INSTANCE.getGbufferProjection());
        BlackHoleRenderer.INSTANCE.captureOpaque(texture(targets,flipped,0),targets.getDepthTextureNoTranslucents().getTextureId(),color.getWidth(),color.getHeight());
    }
    public static void after(RenderTargets targets,ImmutableSet<Integer> flipped) {
        if(!supported(targets)) return;
        BlackHoleRenderer.INSTANCE.captureSurface(texture(targets,flipped,3),targets.getDepthTexture(),2);
        BlackHoleRenderer.INSTANCE.finishOculus(texture(targets,flipped,0));
        pending=BlackHoleRenderer.INSTANCE.active();
    }
    public static void tag(String name,int program) { if(name.equals("composite1")) REFRACTION_PROGRAMS.add(program); else REFRACTION_PROGRAMS.remove(program); }
    public static void destroyed(int program) { REFRACTION_PROGRAMS.remove(program); }
    public static void quad() {
        if(!pending || !REFRACTION_PROGRAMS.contains(org.lwjgl.opengl.GL33C.glGetInteger(org.lwjgl.opengl.GL33C.GL_CURRENT_PROGRAM))) return;
        pending=false;
        int framebuffer=org.lwjgl.opengl.GL33C.glGetInteger(org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
        if(framebuffer==0) { BlackHoleRenderer.INSTANCE.suspend("Oculus composite1 resolved into the default framebuffer"); return; }
        int type=org.lwjgl.opengl.GL33C.glGetFramebufferAttachmentParameteri(org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER,org.lwjgl.opengl.GL33C.GL_COLOR_ATTACHMENT0,org.lwjgl.opengl.GL33C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if(type!=org.lwjgl.opengl.GL33C.GL_TEXTURE) { BlackHoleRenderer.INSTANCE.suspend("Oculus composite1 color attachment is not a texture"); return; }
        int texture=org.lwjgl.opengl.GL33C.glGetFramebufferAttachmentParameteri(org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER,org.lwjgl.opengl.GL33C.GL_COLOR_ATTACHMENT0,org.lwjgl.opengl.GL33C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if(texture==0) { BlackHoleRenderer.INSTANCE.suspend("Oculus composite1 color attachment is missing"); return; }
        BlackHoleRenderer.INSTANCE.afterOculusRefraction(texture,framebuffer);
    }
    public static void completed() { if(pending) { pending=false; BlackHoleRenderer.INSTANCE.suspend("Oculus composite1 front resolve was not reached"); } }
    public static void resetSession() { checkedPack=null; fingerprint=false; pending=false; warned=""; }
}
