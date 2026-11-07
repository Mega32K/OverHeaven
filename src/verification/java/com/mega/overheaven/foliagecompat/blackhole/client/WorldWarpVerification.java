package com.mega.overheaven.foliagecompat.blackhole.client;

import com.mega.overheaven.foliagecompat.worldwarp.*;
import com.mega.overheaven.foliagecompat.worldwarp.client.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Numerical contracts and a closed, independently rendered corridor; never starts Minecraft. */
public final class WorldWarpVerification {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void near(Vec3 a, Vec3 b, double epsilon, String why) {
        check(a.distanceTo(b) < epsilon, why + ": " + a + " / " + b);
    }
    private static void cpu() {
        var state = new WorldWarpState(new ResourceLocation("minecraft", "overworld"), Vec3.ZERO,
                new Vec3(0, 0, 1), false, 1, 500, 0, -1, 0);
        check(Math.abs(state.slope(0)) < 1e-12 && Math.abs(state.slope(125) - 0.03) < 1e-12, "phase/amplitude");
        check(Math.abs(state.slope(250)) < 1e-12 && Math.abs(state.slope(375) + 0.03) < 1e-12, "reverse phase");
        check(Math.abs(state.slope(500)) < 1e-12, "500 tick period");
        var fade = state.stop(125, 20);
        check(fade.envelope(125) == 1 && fade.envelope(135) == 0.5 && fade.envelope(145) == 0, "smooth fade endpoints");
        check(fade.finished(145) && state.stop(100, 0).envelope(100) == 0, "immediate stop");
        Random random = new Random(3999);
        for (Vec3 axis : new Vec3[]{state.axis(), WorldWarpState.direction(37, -25), WorldWarpState.direction(90, 0), WorldWarpState.direction(0, 90)}) {
            Vec3 center = new Vec3(2.5, -1.5, 3);
            AABB source = new AABB(-4, -3, 10, 6, 8, 90), bound = WorldWarpMath.sweptBounds(source, center, axis);
            for (double offset : new double[]{0, center.dot(axis)}) for (int i = 0; i < 500; i++) {
                Vec3 p = new Vec3(-4 + 10 * random.nextDouble(), -3 + 11 * random.nextDouble(), 10 + 80 * random.nextDouble());
                double slope = (random.nextDouble() - 0.5) * 0.12;
                Vec3 visual = WorldWarpMath.warp(p, center, axis, slope, offset);
                near(WorldWarpMath.inverse(visual, center, axis, slope, offset), p, 1e-10, "inverse");
                check(bound.contains(visual), "swept bound must include curved interior");
                Vec3 nearPlane = p.subtract(axis.scale(p.dot(axis) - offset));
                near(WorldWarpMath.warp(nearPlane, center, axis, slope, offset), nearPlane, 1e-10, "zero-angle slice");
                Vec3 n = new Vec3(0.3, 0.8, -0.4).normalize();
                Vec3 tangent = n.cross(new Vec3(1, 0, 0)).normalize();
                Vec3 dn = WorldWarpMath.normal(p, n, center, axis, slope, offset);
                Vec3 dt = WorldWarpMath.warp(p.add(tangent.scale(1e-5)), center, axis, slope, offset).subtract(visual).normalize();
                check(Math.abs(dn.dot(dt)) < 2e-5, "normal must include axial shear");
            }
        }
        float[] centers = {4, 1, 5, -4, 2, 12, 2, -1, 20, 6, 0, 40};
        float[] distances = new float[4];
        WorldWarpFrame frame = new WorldWarpFrame(new Vec3(2, 1, 0), new Vec3(-2, -1, 3), state.axis(), 0.03, 3, true, 1);
        {
            check(WorldWarpSorting.distances(frame, centers, distances, 4, frame.camera()), "active transparency sort");
            for (int i = 0; i < 4; i++) {
                Vec3 p = new Vec3(centers[3*i]-2, centers[3*i+1]-1, centers[3*i+2]);
                double expected = WorldWarpMath.warp(p, frame.center(), frame.axis(), frame.slope(), frame.distanceOffset()).lengthSqr();
                check(Math.abs(distances[i] - expected) < 0.001, "translucent center distance");
            }
            ByteBuffer indices = ByteBuffer.allocate(4 * 6 * 4).order(ByteOrder.nativeOrder());
            WorldWarpSorting.indices(indices, centers, frame, frame.camera());
            float last = Float.POSITIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                int first = indices.getInt(i * 24);
                check(distances[first / 4] <= last, "far-to-near index order");
                last = distances[first / 4];
                int[] quad = {first, first + 1, first + 2, first + 2, first + 3, first};
                for (int k = 0; k < 6; k++) check(indices.getInt(i * 24 + k * 4) == quad[k], "host quad winding");
            }
            WorldWarpSorting.indices(indices, centers, WorldWarpFrame.OFF, Vec3.ZERO);
            for (int i = 0; i < 4; i++) check(indices.getInt(i * 24) == i * 4, "NONE restores initialized sequential indices");
            float[] before = distances.clone();
            check(!WorldWarpSorting.distances(WorldWarpFrame.OFF, centers, distances, 4, frame.camera()) && Arrays.equals(before, distances), "disabled sorter delegates");
        }
        System.out.println("PASS: timeline, signed distance, inverse/Jacobian, conservative bounds, transparent distances and delegation");
    }
    private static Vec3 worldWarp(Vec3 world, WorldWarpFrame frame) {
        return WorldWarpMath.warp(world.subtract(frame.camera()), frame.center(), frame.axis(), frame.slope(),
                frame.distanceOffset()).add(frame.camera());
    }
    private static void eyes() {
        Vec3 eyes = new Vec3(1000010.25, 67.62, -2000080.5), axis = WorldWarpState.direction(37, -25);
        var state = new WorldWarpState(new ResourceLocation("minecraft", "overworld"), Vec3.ZERO, axis, true, 1, 500, 0, -1, 0);
        Vec3 camera = eyes.add(7, 3, -9), lateral = axis.cross(new Vec3(0, 1, 0)).normalize().scale(5);
        var first = WorldWarpFrame.sample(state, eyes, eyes, 125, 1);
        var third = WorldWarpFrame.sample(state, camera, eyes, 125, 1);
        near(third.camera(), camera, 1e-12, "real camera must remain the render origin");
        near(third.center().add(camera), eyes, 1e-9, "axis follows the player's eyes");
        near(worldWarp(eyes.add(lateral), third), eyes.add(lateral), 1e-8, "eye slice stays fixed with detached camera");
        Vec3 point = eyes.add(axis.scale(64)).add(lateral);
        Vec3 expected = eyes.add(axis.scale(64)).add(WorldWarpMath.rotate(lateral, axis, 0.03 * 64));
        near(worldWarp(point, first), expected, 1e-8, "eye-relative signed distance");
        near(worldWarp(point, third), expected, 1e-8, "third person must not move the world twist");
        var detached = WorldWarpFrame.sample(state, eyes.add(-35, 19, 52), eyes, 125, 1);
        near(worldWarp(point, detached), expected, 1e-8, "cinematic camera must not move the world twist");
        Vec3 step = new Vec3(3, -0.35, 5);
        var moved = WorldWarpFrame.sample(state, camera, eyes.add(step), 125, 1);
        near(worldWarp(point.add(step), moved), expected.add(step), 1e-8, "moving/crouching player moves both pivot and slice");
        check(worldWarp(point, moved).distanceTo(expected) > 0.1, "stationary camera must still see the moving eye origin");
        check(moved.axis().equals(axis) && state.stop(125, 20).followEyes(), "fixed direction and follow mode survive stop fade");
        var fixed = new WorldWarpState(state.dimension(), new Vec3(2, 65, 3), axis, false, 1, 500, 0, -1, 0);
        var fixedFrame = WorldWarpFrame.sample(fixed, camera, eyes, 125, 1);
        near(fixedFrame.center().add(camera), fixed.center(), 1e-9, "fixed mode keeps its world center");
        check(fixedFrame.distanceOffset() == 0 && fixedFrame.equals(WorldWarpFrame.sample(fixed, camera, eyes.add(step), 125, 1)),
                "fixed mode remains independent of player eyes");
        System.out.println("PASS: player-eye pivot/slice, third-person and cinematic camera independence, motion, fixed-mode regression");
    }
    private static void uniforms(GlProgram p, Vec3 center, Vec3 axis, double slope) {
        p.use(); p.vector("ohWarpCenter", (float)center.x, (float)center.y, (float)center.z);
        p.vector("ohWarpAxis", (float)axis.x, (float)axis.y, (float)axis.z); p.scalar("ohWarpSlope", slope);
        p.scalar("ohWarpDistanceOffset", 0);
    }
    private static void feedback(String library) {
        String vertex = "#version 330 core\n#define MC_RENDER_STAGE_HAND_SOLID 17\n#define MC_RENDER_STAGE_HAND_TRANSLUCENT 24\n" + library + """
            layout(location=0) in vec3 point;
            out vec3 displayPoint;
            out vec3 originalPoint;
            out vec3 previousPoint;
            out vec3 visualNormal;
            void main() {
                displayPoint=ohWarp(point); originalPoint=ohUnwarp(displayPoint);
                previousPoint=ohWarpHistory(displayPoint);
                visualNormal=ohWarpNormal(point,normalize(vec3(0.3,0.8,-0.4)));
                gl_Position=vec4(0,0,0,1);
            }
            """;
        try (var p = new GlProgram(vertex, "#version 330 core\nout vec4 color; void main(){color=vec4(1);}")) {
            glTransformFeedbackVaryings(p.handle(), new String[]{"displayPoint","originalPoint","previousPoint","visualNormal"}, GL_INTERLEAVED_ATTRIBS);
            glLinkProgram(p.handle()); check(glGetProgrami(p.handle(), GL_LINK_STATUS) == GL_TRUE, glGetProgramInfoLog(p.handle()));
            Vec3 center = new Vec3(-2, -1, 0), axis = WorldWarpState.direction(37, -25);
            Vec3 previousCenter = new Vec3(-1, -1.5, 0.5), delta = previousCenter.subtract(center);
            uniforms(p, center, axis, 0.03);
            p.vector("ohWarpPreviousCenter", (float)previousCenter.x, (float)previousCenter.y, (float)previousCenter.z);
            p.vector("ohWarpPreviousAxis", (float)axis.x, (float)axis.y, (float)axis.z); p.scalar("ohWarpPreviousSlope", 0.029);
            p.vector("ohWarpCameraDelta", (float)delta.x, (float)delta.y, (float)delta.z);
            float[] input = new float[300]; Random random = new Random(3999);
            for(int i=0;i<input.length;i++) input[i]=(random.nextFloat()-0.5f)*128;
            int vao=glGenVertexArrays(), vbo=glGenBuffers(), output=glGenBuffers();
            try {
                glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo); glBufferData(GL_ARRAY_BUFFER,input,GL_STATIC_DRAW);
                glVertexAttribPointer(0,3,GL_FLOAT,false,12,0); glEnableVertexAttribArray(0);
                glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER,output); glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER,100L*12*4,GL_STREAM_READ);
                glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER,0,output); glEnable(GL_RASTERIZER_DISCARD);
                for (boolean following : new boolean[]{false, true}) {
                    // The player's previous eyes moved independently of the camera.
                    Vec3 oldCenter = following ? previousCenter.add(3, -0.35, 5) : previousCenter;
                    double offset = following ? center.dot(axis) : 0, oldOffset = following ? oldCenter.dot(axis) : 0;
                    p.scalar("ohWarpDistanceOffset", offset); p.scalar("ohWarpPreviousDistanceOffset", oldOffset);
                    p.vector("ohWarpPreviousCenter", (float)oldCenter.x, (float)oldCenter.y, (float)oldCenter.z);
                    glEnable(GL_RASTERIZER_DISCARD);
                    glBeginTransformFeedback(GL_POINTS); glDrawArrays(GL_POINTS,0,100); glEndTransformFeedback(); glDisable(GL_RASTERIZER_DISCARD);
                    float[] result=new float[1200]; glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER,0,result);
                    for(int i=0;i<100;i++) {
                        Vec3 point=new Vec3(input[3*i],input[3*i+1],input[3*i+2]);
                        Vec3[] expected={WorldWarpMath.warp(point,center,axis,0.03,offset),point,
                            WorldWarpMath.warp(point.add(delta),oldCenter,axis,0.029,oldOffset),
                            WorldWarpMath.normal(point,new Vec3(0.3,0.8,-0.4).normalize(),center,axis,0.03,offset)};
                        for(int j=0;j<4;j++) near(new Vec3(result[12*i+3*j],result[12*i+3*j+1],result[12*i+3*j+2]),expected[j],0.0001,"GPU transform/history/normal");
                    }
                }
            } finally { glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER,0,0); glDeleteBuffers(output); glDeleteBuffers(vbo); glDeleteVertexArrays(vao); }
        }
        System.out.println("PASS: production GLSL transform feedback matches CPU, including previous-frame reprojection");
    }
    private static void quad(ArrayList<Float> mesh, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        for (Vec3 p : new Vec3[]{a,b,c,a,c,d}) { mesh.add((float)p.x); mesh.add((float)p.y); mesh.add((float)p.z); }
    }
    private static byte[] corridor(GlProgram p, int count, Vec3 camera, double slope, int stage) {
        uniforms(p, camera.scale(-1), new Vec3(0,0,1), slope);
        p.vector("camera",(float)camera.x,(float)camera.y,(float)camera.z); p.integer("stage",stage);
        p.matrix("projection",new Matrix4f().perspective((float)Math.toRadians(85),640f/360,0.05f,128));
        p.matrix("view",new Matrix4f().lookAt(0,0,0,0,0,1,0,1,0));
        glClearColor(0,0,0,0); glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT); glDrawArrays(GL_TRIANGLES,0,count);
        ByteBuffer bytes=BufferUtils.createByteBuffer(640*360*4);glReadPixels(0,0,640,360,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        byte[] result=new byte[bytes.remaining()];bytes.get(result);return result;
    }
    private static void png(byte[] data, Path file) throws Exception {
        BufferedImage image=new BufferedImage(640,360,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<360;y++)for(int x=0;x<640;x++) {
            int i=(y*640+x)*4;
            image.setRGB(x,359-y,0xff000000|((data[i]&255)<<16)|((data[i+1]&255)<<8)|(data[i+2]&255));
        }
        ImageIO.write(image,"PNG",file.toFile());
    }
    private static void corridor(String library, Path output) throws Exception {
        String vertex="#version 330 core\n#define MC_RENDER_STAGE_HAND_SOLID 17\n#define MC_RENDER_STAGE_HAND_TRANSLUCENT 24\n"+library+"""
            layout(location=0) in vec3 point;
            uniform mat4 projection,view; uniform vec3 camera; uniform int stage;
            out vec3 source;
            void main(){source=point; vec3 p=point-camera; gl_Position=projection*view*vec4(ohWarpStage(stage)?ohWarp(p):p,1);}
            """;
        String fragment="""
            #version 330 core
            in vec3 source; out vec4 color;
            void main(){
                vec3 grid=abs(fract(source)-0.5);
                float seam=step(0.475,min(grid.z,abs(source.x)>3.9?grid.y:grid.x));
                vec3 base=abs(source.x)>3.9?vec3(0.60,0.53,0.40):vec3(0.35,0.40,0.45);
                base*=0.65+0.35*exp2(-max(source.z,0.0)/48.0);
                color=vec4(mix(base,base*0.35,seam),1);
            }
            """;
        var mesh=new ArrayList<Float>();
        for(int z=-2;z<96;z++) {
            quad(mesh,new Vec3(-4,-3,z),new Vec3(4,-3,z),new Vec3(4,-3,z+1),new Vec3(-4,-3,z+1));
            quad(mesh,new Vec3(-4,3,z),new Vec3(-4,3,z+1),new Vec3(4,3,z+1),new Vec3(4,3,z));
            quad(mesh,new Vec3(-4,-3,z),new Vec3(-4,-3,z+1),new Vec3(-4,3,z+1),new Vec3(-4,3,z));
            quad(mesh,new Vec3(4,-3,z),new Vec3(4,3,z),new Vec3(4,3,z+1),new Vec3(4,-3,z+1));
        }
        quad(mesh,new Vec3(-4,-3,96),new Vec3(4,-3,96),new Vec3(4,3,96),new Vec3(-4,3,96));
        float[] vertices=new float[mesh.size()];for(int i=0;i<vertices.length;i++)vertices[i]=mesh.get(i);
        int vao=glGenVertexArrays(),vbo=glGenBuffers(),fbo=glGenFramebuffers(),texture=glGenTextures(),depth=glGenRenderbuffers();
        try(var program=new GlProgram(vertex,fragment)) {
            glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW);
            glVertexAttribPointer(0,3,GL_FLOAT,false,12,0);glEnableVertexAttribArray(0);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,640,360,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            glBindRenderbuffer(GL_RENDERBUFFER,depth);glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,640,360);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"fixture framebuffer");
            glViewport(0,0,640,360);glEnable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
            int count=vertices.length/3;
            byte[] baseline=corridor(program,count,Vec3.ZERO,0,0);
            byte[] warped=corridor(program,count,Vec3.ZERO,0.03,0);
            byte[] offaxis=corridor(program,count,new Vec3(1.5,0.5,0),0.03,0);
            check(!Arrays.equals(baseline,warped),"corridor must visibly twist");
            for(byte[] frame:new byte[][]{baseline,warped,offaxis})for(int i=3;i<frame.length;i+=4)check((frame[i]&255)==255,"closed corridor must not develop holes");
            check(Arrays.equals(baseline,corridor(program,count,Vec3.ZERO,0.03,17)),"hand stage must bypass deformation");
            check(Arrays.equals(baseline,corridor(program,count,Vec3.ZERO,0.03,24)),"translucent hand must bypass deformation");
            png(baseline,output.resolve("corridor-original.png"));png(warped,output.resolve("corridor-warped.png"));png(offaxis,output.resolve("corridor-off-axis.png"));
            check(glGetError()==GL_NO_ERROR,"GPU error");
        } finally {glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteRenderbuffers(depth);glDeleteTextures(texture);glDeleteFramebuffers(fbo);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);}
        System.out.println("PASS: closed corridor, off-axis view, hand bypass; independent previews saved");
    }
    private static void transparency(String library) {
        String vertex="#version 330 core\n#define MC_RENDER_STAGE_HAND_SOLID 17\n#define MC_RENDER_STAGE_HAND_TRANSLUCENT 24\n"+library+"""
            layout(location=0) in vec3 point; layout(location=1) in vec3 tint;
            uniform mat4 projection,view; out vec3 color;
            void main(){color=tint;gl_Position=projection*view*vec4(ohWarp(point),1);}
            """;
        String fragment="#version 330 core\nin vec3 color;out vec4 result;void main(){result=vec4(color,0.5);}";
        WorldWarpFrame f=new WorldWarpFrame(Vec3.ZERO,new Vec3(-160,0,0),new Vec3(0,0,1),0.03,0,true,1);
        float[] vertices=new float[48], centers=new float[6];
        for(int q=0;q<2;q++) {
            double z=q==0?120:160;
            Vec3 c=WorldWarpMath.inverse(new Vec3(0,0,z),f.center(),f.axis(),f.slope(),f.distanceOffset());
            centers[q*3]=(float)c.x;centers[q*3+1]=(float)c.y;centers[q*3+2]=(float)c.z;
            for(int v=0;v<4;v++) {
                Vec3 source=WorldWarpMath.inverse(new Vec3((v==0||v==3)?-20:20,v<2?-20:20,z),f.center(),f.axis(),f.slope(),f.distanceOffset());
                int i=(q*4+v)*6;
                vertices[i]=(float)source.x;vertices[i+1]=(float)source.y;vertices[i+2]=(float)source.z;
                vertices[i+3]=q==0?1:0;vertices[i+5]=q==1?1:0;
            }
        }
        check(new Vec3(centers[0],centers[1],centers[2]).lengthSqr()>new Vec3(centers[3],centers[4],centers[5]).lengthSqr(),"fixture must reverse original distance order");
        ByteBuffer indices=BufferUtils.createByteBuffer(48);
        WorldWarpSorting.indices(indices,centers,f,Vec3.ZERO);
        check(indices.order(ByteOrder.nativeOrder()).getInt(0)==4,"warped far panel must be drawn first");
        int vao=glGenVertexArrays(),vbo=glGenBuffers(),ibo=glGenBuffers(),fbo=glGenFramebuffers(),texture=glGenTextures();
        try(var p=new GlProgram(vertex,fragment)) {
            glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,vertices,GL_STATIC_DRAW);
            glVertexAttribPointer(0,3,GL_FLOAT,false,24,0);glEnableVertexAttribArray(0);
            glVertexAttribPointer(1,3,GL_FLOAT,false,24,12);glEnableVertexAttribArray(1);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ibo);glBufferData(GL_ELEMENT_ARRAY_BUFFER,indices,GL_STATIC_DRAW);
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);glBindTexture(GL_TEXTURE_2D,texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,32,32,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"transparent fixture framebuffer");
            uniforms(p,f.center(),f.axis(),f.slope());
            p.matrix("projection",new Matrix4f().perspective((float)Math.toRadians(60),1,0.05f,256));
            p.matrix("view",new Matrix4f().lookAt(0,0,0,0,0,1,0,1,0));
            glViewport(0,0,32,32);glDisable(GL_DEPTH_TEST);glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);glDrawElements(GL_TRIANGLES,12,GL_UNSIGNED_INT,0);
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);glReadPixels(16,16,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
            check(Math.abs((pixel.get(0)&255)-128)<=1 && Math.abs((pixel.get(2)&255)-64)<=1,"GPU blend must place red near panel over blue far panel");
            check(glGetError()==GL_NO_ERROR,"transparent GPU error");
        } finally {glDisable(GL_BLEND);glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteTextures(texture);glDeleteFramebuffers(fbo);glDeleteBuffers(ibo);glDeleteBuffers(vbo);glDeleteVertexArrays(vao);}
        System.out.println("PASS: off-axis transparent geometry uses warped centers and only rewritten indices");
    }
    public static void main(String[] args) throws Exception {
        cpu(); eyes(); Path output=Path.of(args[1]);Files.createDirectories(output);
        String library=Files.readString(Path.of(args[0]));
        check(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(16,16,"worldWarp isolated GPU",0,0);check(window!=0,"GL context");
        glfwMakeContextCurrent(window);GL.createCapabilities();
        try {feedback(library);corridor(library,output);transparency(library);} finally {glfwDestroyWindow(window);glfwTerminate();}
    }
}
