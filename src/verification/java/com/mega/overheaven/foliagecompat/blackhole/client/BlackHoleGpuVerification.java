package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

public final class BlackHoleGpuVerification {
    private static int checks;
    private static int vertexArray;
    private static Path resources;
    private static float previewGlow;
    private static float materialTime=7;
    private static GlDiskTexture diskTexture;
    private static GlBloom pyramid;
    private static boolean currentBloom;
    private static int currentSteps=48;
    private static float centerDistance=40;
    private static float viewTilt=30, viewYaw=-83;
    private static final int WIDTH=960, HEIGHT=540;
    private static final Matrix4f PROJECTION=new Matrix4f().perspective((float)Math.toRadians(60),WIDTH/(float)HEIGHT,0.05f,4096);
    private static final Matrix4f INVERSE=new Matrix4f(PROJECTION).invert();
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); checks++; }
    private static String source(String name) throws Exception { String text=Files.readString(resources.resolve(name)); return text.contains("#include \"field.glsl\"")?text.replace("#include \"field.glsl\"",source("field.glsl")):text; }
    private static GlDiskTexture detail() throws Exception {
        try(var stream=Files.newInputStream(resources.resolve("../../textures/blackhole/disk_detail.png").normalize())) { return new GlDiskTexture(stream); }
    }
    private static GlProgram program(String name) throws Exception { return new GlProgram(source("fullscreen.vsh"),source(name+".fsh")); }
    private static GlProgram baseline(String[] arguments,String name) throws Exception {
        if(arguments.length<3) return null;
        Path directory=Path.of(arguments[2]);
        String fragment=Files.readString(directory.resolve(name+".fsh")).replace("#include \"field.glsl\"",Files.readString(directory.resolve("field.glsl")));
        return new GlProgram(source("fullscreen.vsh"),fragment);
    }
    private static double measure(Runnable frame) {
        for(int warm=0;warm<8;warm++) frame.run();
        int query=glGenQueries(); glBeginQuery(GL_TIME_ELAPSED,query);
        for(int repeat=0;repeat<24;repeat++) frame.run();
        glEndQuery(GL_TIME_ELAPSED); double elapsed=glGetQueryObjectui64(query,GL_QUERY_RESULT)/1000000.0/24; glDeleteQueries(query);
        return elapsed;
    }
    private static void draw() { glBindVertexArray(vertexArray); glDrawArrays(GL_TRIANGLES,0,3); }
    private static void clear(GlTarget target,float first,float second,float third,float alpha) { target.bind(); glClearBufferfv(GL_COLOR,0,new float[]{first,second,third,alpha}); }
    private static float depth(float distance) { var point=PROJECTION.transform(new org.joml.Vector4f(0,0,-distance,1)); return point.z/point.w*0.5f+0.5f; }
    private static void material(GlProgram program,GlTarget opaque,GlTarget surface,int steps,float brightness) {
        currentSteps=steps; program.scalar("Glow",previewGlow);
        program.matrix("VP",PROJECTION); program.matrix("InverseVP",INVERSE); program.vector("Eye",0,0,0); program.vector("Center",0,0,-centerDistance);
        double angle=Math.toRadians(viewTilt), yaw=Math.toRadians(viewYaw);
        program.vector("DiskNormal",(float)(Math.sin(yaw)*Math.sin(angle)),(float)Math.cos(angle),(float)(Math.cos(yaw)*Math.sin(angle)));
        program.vector("DiskRight",(float)Math.cos(yaw),0,(float)-Math.sin(yaw)); program.vector("DiskForward",(float)(-Math.sin(yaw)*Math.cos(angle)),(float)Math.sin(angle),(float)(-Math.cos(yaw)*Math.cos(angle)));
        program.vector("HotColor",1,0.976f,0.95f); program.vector("ColdColor",0.596f,0.463f,0.365f);
        program.scalar("Radius",3.2); program.scalar("Inner",3.8); program.scalar("Outer",11); program.scalar("Thickness",0.12); program.scalar("Brightness",brightness);
        program.scalar("Rotation",0.25); program.scalar("Infall",0.12); program.scalar("Lensing",1); program.scalar("Time",materialTime); program.scalar("Seed",12); program.integer("Steps",steps);
        program.texture("OpaqueDepth",8,opaque.textures[1]); program.texture("SurfaceDepth",9,surface.textures[1]);
        program.texture("DiskTexture",10,diskTexture.handle());
    }
    private static void solve(GlProgram program,GlTarget solution,GlTarget opaque,GlTarget surface,int steps,float brightness) {
        solution.bind(); program.use(); material(program,opaque,surface,steps,brightness); program.scalar("PixelAngle",2.0/(PROJECTION.m11()*solution.height)); draw();
    }
    private static void compose(GlProgram program,GlTarget output,GlTarget scene,GlTarget opaque,GlTarget surface,GlTarget solution,GlTarget bloom,int steps,float brightness) {
        output.bind(); program.use(); material(program,opaque,surface,steps,brightness);
        program.scalar("PixelAngle",2.0/(PROJECTION.m11()*output.height));
        program.texture("Scene",0,scene.textures[0]); program.texture("Opaque",1,opaque.textures[0]); program.texture("Transmission",2,surface.textures[0]);
        program.texture("FrontTexture",3,solution.textures[0]); program.texture("BackTexture",4,solution.textures[1]); program.texture("LensTexture",5,solution.textures[2]); program.texture("MetaTexture",6,solution.textures[3]); program.texture("Bloom",7,(currentBloom?pyramid.result():bloom).textures[0]);
        program.integer("DisplaySpace",0); program.integer("LayerMode",0); program.scalar("Glow",previewGlow); draw();
    }
    private static void bloom(GlProgram program,GlTarget first,GlTarget second,GlTarget solution,GlTarget surface,GlTarget opaque) {
        currentBloom=true; pyramid.resize(opaque.width,opaque.height,currentSteps);
        program.use(); material(program,opaque,surface,currentSteps,3.2f);
        program.scalar("PixelAngle",2.0/(PROJECTION.m11()*opaque.height)); program.pair("FullSize",opaque.width,opaque.height);
        program.texture("FrontTexture",0,solution.textures[0]); program.texture("BackTexture",1,solution.textures[1]); program.texture("Transmission",2,surface.textures[0]); program.texture("LensTexture",3,solution.textures[2]);
        pyramid.render(program,1,vertexArray);
    }
    private static void oldBloom(GlProgram program,GlTarget first,GlTarget second,GlTarget solution,GlTarget surface,GlTarget opaque) {
        currentBloom=false;
        first.bind(); program.use(); program.integer("Extract",1); program.texture("FrontTexture",0,solution.textures[0]); program.texture("BackTexture",1,solution.textures[1]); program.texture("Transmission",2,surface.textures[0]); program.texture("LensTexture",3,solution.textures[2]); program.texture("OpaqueDepth",8,opaque.textures[1]); program.matrix("InverseVP",INVERSE); program.vector("Eye",0,0,0); program.pair("Direction",2.0f/first.width,0); draw();
        second.bind(); program.integer("Extract",0); program.texture("Source",0,first.textures[0]); program.pair("Direction",0,2.0f/second.height); draw();
    }
    private static float[] pixel(GlTarget target,int horizontal,int vertical) { target.bind(); float[] result=new float[4]; glReadBuffer(GL_COLOR_ATTACHMENT0); glReadPixels(horizontal,vertical,1,1,GL_RGBA,GL_FLOAT,result); return result; }
    private static void near(float actual,float expected,String message) { check(Math.abs(actual-expected)<0.004,message+" "+actual+" != "+expected); }
    private static float[] pixels(GlTarget target) {
        float[] values=new float[target.width*target.height*4]; target.bind(); glReadBuffer(GL_COLOR_ATTACHMENT0);
        glReadPixels(0,0,target.width,target.height,GL_RGBA,GL_FLOAT,values); return values;
    }
    private static void image(GlTarget target,Path file) throws Exception {
        float[] values=pixels(target); var image=new BufferedImage(target.width,target.height,BufferedImage.TYPE_INT_RGB);
        for(int vertical=0;vertical<target.height;vertical++) for(int horizontal=0;horizontal<target.width;horizontal++) {
            int index=(vertical*target.width+horizontal)*4, rgb=0;
            for(int channel=0;channel<3;channel++) { check(Float.isFinite(values[index+channel]),"finite comparison image"); rgb|=Math.max(0,Math.min(255,(int)(values[index+channel]*255)))<<(16-channel*8); }
            image.setRGB(horizontal,target.height-1-vertical,rgb);
        }
        ImageIO.write(image,"png",file.toFile());
    }
    private static double energy(float[] values) {
        double total=0; for(int index=0;index<values.length;index+=4) total+=values[index]+values[index+1]+values[index+2]; return total;
    }
    private static void verifyBloom(String[] arguments,Path output,GlProgram integrate,GlProgram composite,GlProgram blur,
            GlTarget solution,GlTarget opaque,GlTarget surface,GlTarget scene,GlTarget result,GlTarget bloom,GlTarget bloomTemp) throws Exception {
        try(var previousIntegrate=baseline(arguments,"integrate"); var previousBlur=baseline(arguments,"bloom"); var previousComposite=baseline(arguments,"composite")) {
            String[] names={"reference","face","side","back","near"};
            float[][] views={{30,-83,17},{90,0,23},{0,0,23},{150,97,17},{30,-83,7}};
            clear(opaque,0.014f,0.019f,0.032f,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0});
            clear(scene,0.014f,0.019f,0.032f,1); clear(surface,1,1,1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0});
            solution.resize(WIDTH/2,HEIGHT/2);
            for(int view=0;view<views.length;view++) {
                viewTilt=views[view][0]; viewYaw=views[view][1]; centerDistance=views[view][2]; previewGlow=0.7f;
                if(previousIntegrate!=null) {
                    solve(previousIntegrate,solution,opaque,surface,48,3.2f); bloom(previousBlur,bloomTemp,bloom,solution,surface,opaque);
                    compose(previousComposite,result,scene,opaque,surface,solution,bloom,48,3.2f); previousComposite.integer("DisplaySpace",1); draw();
                    image(result,output.resolve(names[view]+"-before.png"));
                }
                solve(integrate,solution,opaque,surface,48,3.2f); bloom(blur,bloomTemp,bloom,solution,surface,opaque);
                compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f); composite.integer("DisplaySpace",1); draw();
                image(result,output.resolve(names[view]+"-after.png"));
            }
        }
        viewTilt=30; viewYaw=-83; centerDistance=17;
        double lastEnergy=-1;
        for(float strength:new float[]{0,0.7f,1.4f}) {
            previewGlow=strength; solve(integrate,solution,opaque,surface,48,3.2f);
            if(strength>0) bloom(blur,bloomTemp,bloom,solution,surface,opaque);
            compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f);
            double total=energy(pixels(result)); check(total>lastEnergy,"glow increases integrated light"); lastEnergy=total;
            composite.integer("DisplaySpace",1); draw(); image(result,output.resolve("glow-"+strength+".png"));
            System.out.println("Glow fixture: strength="+strength+", HDR energy="+total);
        }
        previewGlow=0; solve(integrate,solution,opaque,surface,48,3.2f); float[] noHaze=pixels(solution);
        previewGlow=0.7f; solve(integrate,solution,opaque,surface,48,3.2f); float[] haze=pixels(solution);
        double hazeGain=0;
        for(int index=0;index<haze.length;index+=4) { check(noHaze[index+3]==haze[index+3],"haze adds no opacity"); hazeGain+=haze[index]-noHaze[index]; }
        check(hazeGain>1,"finite-path haze contributes radiation");
        previewGlow=0; currentBloom=false; clear(bloom,0,0,0,1); compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f); float[] disabled=pixels(result);
        clear(bloom,100,100,100,0); compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f); float[] stale=pixels(result);
        for(int index=0;index<stale.length;index++) check(stale[index]==disabled[index],"glow zero never samples stale pyramid");
        previewGlow=0.7f;
        clear(scene,0.17f,0.23f,0.31f,1); clear(opaque,0.17f,0.23f,0.31f,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0});
        glEnable(GL_SCISSOR_TEST);
        for(int row=0;row<HEIGHT;row+=48) for(int column=0;column<WIDTH;column+=48) if(((row/48+column/48)&1)==0) {
            glScissor(column,row,48,48); glClearBufferfv(GL_COLOR,1,new float[]{depth(4),0,0,0});
        }
        glDisable(GL_SCISSOR_TEST);
        solve(integrate,solution,opaque,surface,48,3.2f); bloom(blur,bloomTemp,bloom,solution,surface,opaque);
        compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f); float[] cutout=pixels(result);
        for(int row=0;row<HEIGHT;row++) for(int column=0;column<WIDTH;column++) if(((row/48+column/48)&1)==0) {
            int index=(row*WIDTH+column)*4;
            near(cutout[index],0.17f,"bloom respects foreground cutout red"); near(cutout[index+1],0.23f,"bloom respects foreground cutout green"); near(cutout[index+2],0.31f,"bloom respects foreground cutout blue");
        }
        image(result,output.resolve("cutout-occlusion.png"));
        clear(opaque,0.17f,0.23f,0.31f,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(4),0,0,0});
        clear(scene,0.17f,0.23f,0.31f,1); solve(integrate,solution,opaque,surface,48,3.2f);
        bloom(blur,bloomTemp,bloom,solution,surface,opaque); check(energy(pixels(pyramid.result()))<0.01,"whole wall suppresses bloom extraction");
        compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f); near(pixel(result,WIDTH/2,HEIGHT/2)[0],0.17f,"whole wall remains unchanged with glow");
        clear(opaque,0,0,0,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0}); clear(scene,0,0,0,1);
        clear(surface,0,0,0,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(17),0,0,0});
        solve(integrate,solution,opaque,surface,48,3.2f); bloom(blur,bloomTemp,bloom,solution,surface,opaque);
        blur.use(); material(blur,opaque,surface,48,3.2f); blur.pair("FullSize",WIDTH,HEIGHT); blur.scalar("PixelAngle",2.0/(PROJECTION.m11()*HEIGHT));
        blur.texture("FrontTexture",0,solution.textures[0]); blur.texture("BackTexture",1,solution.textures[1]); blur.texture("Transmission",2,surface.textures[0]); blur.texture("LensTexture",3,solution.textures[2]);
        pyramid.render(blur,2,vertexArray); check(energy(pixels(pyramid.result()))<0.01,"zero transmission suppresses rear bloom");
        blur.texture("FrontTexture",0,solution.textures[0]); pyramid.render(blur,3,vertexArray);
        check(energy(pixels(pyramid.result()))>1,"front bloom survives zero transmission");
        clear(surface,1,1,1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0});
        clear(solution,0,0,0,1); glClearBufferfv(GL_COLOR,1,new float[]{0,0,0,1}); glClearBufferfv(GL_COLOR,2,new float[]{0,0,65504,65504});
        bloom(blur,bloomTemp,bloom,solution,surface,opaque);
        check(energy(pixels(pyramid.result()))>1,"core rim alone contributes to bloom source");
        int reused=pyramid.result().textures[0]; pyramid.resize(WIDTH,HEIGHT,48); check(pyramid.result().textures[0]==reused,"same-size pyramid is reused");
        glBindTexture(GL_TEXTURE_2D,reused); check(glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER)==GL_LINEAR,"bloom color uses linear filtering");
        glBindTexture(GL_TEXTURE_2D,opaque.textures[1]); check(glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER)==GL_NEAREST,"scene depth remains nearest");
        for(int steps:new int[]{96,24,48}) { pyramid.resize(WIDTH+7,HEIGHT+5,steps); check(pyramid.result().width==(WIDTH+10)/4,"odd-size pyramid resize"); }
        reused=pyramid.result().textures[0]; int oldFramebuffer=pyramid.result().framebuffer; pyramid.close();
        check(!glIsTexture(reused) && !glIsFramebuffer(oldFramebuffer),"pyramid release deletes GPU objects"); pyramid.resize(WIDTH,HEIGHT,48);
        check(glGetError()==GL_NO_ERROR,"bloom suite has no GL errors");
    }
    private static void comparison(Path output) throws Exception {
        String[] views={"reference","face","side","back","near"};
        for(String view:views) {
            Path before=output.resolve(view+"-before.png"); if(!Files.exists(before)) continue;
            var left=ImageIO.read(before.toFile()); var right=ImageIO.read(output.resolve(view+"-after.png").toFile());
            var canvas=new BufferedImage(WIDTH*2,HEIGHT+36,BufferedImage.TYPE_INT_RGB); var graphics=canvas.createGraphics();
            try {
                graphics.setColor(new java.awt.Color(20,23,28)); graphics.fillRect(0,0,canvas.getWidth(),canvas.getHeight());
                graphics.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,18)); graphics.setColor(java.awt.Color.WHITE);
                graphics.drawString("BEFORE / "+view+" / seed 12, time 7",18,25); graphics.drawString("AFTER / independent GPU fixture (not Minecraft)",WIDTH+18,25);
                graphics.drawImage(left,0,36,null); graphics.drawImage(right,WIDTH,36,null);
            } finally { graphics.dispose(); }
            ImageIO.write(canvas,"png",output.resolve(view+"-comparison.png").toFile());
        }
    }
    public static void main(String[] arguments) throws Exception {
        resources=Path.of(arguments[0]); Path output=Path.of(arguments[1]); Files.createDirectories(output);
        check(glfwInit(),"GLFW init"); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3); glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"OverHeaven shader verification",0,0); check(window!=0,"hidden GL context"); glfwMakeContextCurrent(window); GL.createCapabilities();
        System.out.println("GPU verification context: "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
        vertexArray=glGenVertexArrays();
        try(var disk=detail(); var ownedBloom=new GlBloom(); var copy=program("copy"); var integrate=program("integrate"); var composite=program("composite"); var blur=program("bloom"); var foreground=program("foreground");
            var opaque=new GlTarget(GL_RGBA16F,GL_R32F); var surface=new GlTarget(GL_RGBA16F,GL_R32F); var scene=new GlTarget(GL_RGBA16F);
            var solution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA32F,GL_RGBA32F); var bloom=new GlTarget(GL_RGBA16F); var bloomTemp=new GlTarget(GL_RGBA16F); var result=new GlTarget(GL_RGBA16F); var frontLayer=new GlTarget(GL_RGBA16F); var frontResolve=new GlTarget(GL_RGBA16F)) {
            diskTexture=disk; pyramid=ownedBloom;
            check(copy.handle()!=0 && integrate.handle()!=0 && composite.handle()!=0 && blur.handle()!=0 && foreground.handle()!=0,"five production shaders linked");
            for(String uniform:new String[]{"VP","InverseVP","Eye","Center","Steps","OpaqueDepth","SurfaceDepth","DiskTexture","PixelAngle"}) check(glGetUniformLocation(integrate.handle(),uniform)>=0,"integrator uniform "+uniform);
            for(String uniform:new String[]{"Scene","Transmission","Opaque","FrontTexture","BackTexture","LensTexture","MetaTexture","Bloom"}) check(glGetUniformLocation(composite.handle(),uniform)>=0,"composite uniform "+uniform);
            opaque.resize(WIDTH,HEIGHT); surface.resize(WIDTH,HEIGHT); scene.resize(WIDTH,HEIGHT); solution.resize(WIDTH/2,HEIGHT/2); bloom.resize(WIDTH/4,HEIGHT/4); bloomTemp.resize(WIDTH/4,HEIGHT/4); result.resize(WIDTH,HEIGHT); frontLayer.resize(WIDTH,HEIGHT); frontResolve.resize(WIDTH,HEIGHT);
            try(var seam=new GlProgram(source("fullscreen.vsh"),"#version 330 core\nlayout(location=0) out vec4 Color;\n"+source("field.glsl")+"\nvoid main(){ vec2 p=gl_FragCoord.x<1.5?vec2(-7.0,0.0001):vec2(-7.0,-0.0001); Color=vec4(diskMaterial(p,0.001),1.0); }")) {
                var probe=new GlTarget(GL_RGBA32F);
                try {
                    probe.resize(2,1); probe.bind(); seam.use(); material(seam,opaque,surface,48,3.2f); draw();
                    float[] left=pixel(probe,0,0), right=pixel(probe,1,0);
                    for(int channel=0;channel<3;channel++) near(left[channel],right[channel],"periodic angular seam");
                    materialTime=31; probe.bind(); seam.use(); material(seam,opaque,surface,48,3.2f); draw();
                    float[] animated=pixel(probe,0,0);
                    check(Math.abs(animated[0]-left[0])+Math.abs(animated[1]-left[1])>0.002,"time advances material rather than frozen lookup");
                    materialTime=7;
                } finally { probe.close(); }
            }
            GlState.fullscreen(); clear(opaque,0.2f,0.4f,0.6f,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0});
            clear(surface,1,1,1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0}); clear(scene,0.2f,0.4f,0.6f,1); clear(bloom,0,0,0,1);
            solve(integrate,solution,opaque,surface,48,0); compose(composite,result,scene,opaque,surface,solution,bloom,48,0);
            var center=pixel(result,WIDTH/2,HEIGHT/2); near(center[0],0,"black core red"); near(center[1],0,"black core green"); near(center[2],0,"black core blue");
            clear(surface,0.5f,0.5f,0.5f,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(10),0,0,0}); clear(scene,0.2f,0.25f,0.32f,1);
            solve(integrate,solution,opaque,surface,48,0); compose(composite,result,scene,opaque,surface,solution,bloom,48,0);
            center=pixel(result,WIDTH/2,HEIGHT/2); near(center[0],0.1f,"glass surface retained red"); near(center[1],0.05f,"glass surface retained green"); near(center[2],0.02f,"glass surface retained blue");
            surface.bind(); glClearBufferfv(GL_COLOR,1,new float[]{depth(80),0,0,0}); solve(integrate,solution,opaque,surface,48,0); compose(composite,result,scene,opaque,surface,solution,bloom,48,0);
            center=pixel(result,WIDTH/2,HEIGHT/2); near(center[0],0,"core in front of glass");
            frontLayer.bind(); composite.use(); composite.integer("LayerMode",2); draw();
            frontResolve.bind(); foreground.use(); foreground.texture("Scene",0,scene.textures[0]); foreground.texture("Foreground",1,frontLayer.textures[0]); draw();
            near(pixel(frontResolve,WIDTH/2,HEIGHT/2)[0],0,"front core applied after host refraction");
            surface.bind(); glClearBufferfv(GL_COLOR,1,new float[]{depth(10),0,0,0}); solve(integrate,solution,opaque,surface,48,0); compose(composite,result,scene,opaque,surface,solution,bloom,48,0);
            frontLayer.bind(); composite.use(); composite.integer("LayerMode",2); draw(); frontResolve.bind(); foreground.use(); foreground.texture("Scene",0,scene.textures[0]); foreground.texture("Foreground",1,frontLayer.textures[0]); draw();
            near(pixel(frontResolve,WIDTH/2,HEIGHT/2)[0],0.2f,"no front absorption when glass precedes core");
            opaque.bind(); glClearBufferfv(GL_COLOR,1,new float[]{depth(10),0,0,0}); surface.bind(); glClearBufferfv(GL_COLOR,1,new float[]{depth(10),0,0,0}); clear(scene,0.17f,0.23f,0.31f,1);
            solve(integrate,solution,opaque,surface,48,3.2f); compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f);
            center=pixel(result,WIDTH/2,HEIGHT/2); near(center[0],0.17f,"foreground wall unchanged red"); near(center[1],0.23f,"foreground wall unchanged green"); near(center[2],0.31f,"foreground wall unchanged blue");
            opaque.bind(); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0}); glEnable(GL_SCISSOR_TEST); glScissor(0,0,WIDTH/2,HEIGHT); glClearBufferfv(GL_COLOR,1,new float[]{depth(10),0,0,0}); glDisable(GL_SCISSOR_TEST);
            clear(surface,1,1,1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0});
            solve(integrate,solution,opaque,surface,48,0); compose(composite,result,scene,opaque,surface,solution,bloom,48,0);
            for(int offset=1;offset<12;offset++) { near(pixel(result,WIDTH/2-offset,HEIGHT/2)[0],0.17f,"partial wall foreground edge"); near(pixel(result,WIDTH/2+offset,HEIGHT/2)[0],0,"partial wall exposed core"); }
            opaque.bind(); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0}); clear(surface,1,1,1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0}); clear(scene,0.014f,0.019f,0.032f,1); clear(opaque,0.014f,0.019f,0.032f,1);
            centerDistance=17; double minimumMean=Double.POSITIVE_INFINITY, maximumMean=0;
            for(int steps:new int[]{24,48,96}) {
                double scale=steps==24?0.375:steps==96?0.75:0.5; solution.resize((int)Math.ceil(WIDTH*scale),(int)Math.ceil(HEIGHT*scale));
                previewGlow=0.7f; solve(integrate,solution,opaque,surface,steps,3.2f); bloom(blur,bloomTemp,bloom,solution,surface,opaque); compose(composite,result,scene,opaque,surface,solution,bloom,steps,3.2f);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(WIDTH*HEIGHT*4); result.bind(); glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_FLOAT,pixels);
                FloatBuffer display=BufferUtils.createFloatBuffer(WIDTH*HEIGHT*4); composite.integer("DisplaySpace",1); draw(); glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_FLOAT,display);
                var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB); double sum=0; float peak=0;
                for(int vertical=0;vertical<HEIGHT;vertical++) for(int horizontal=0;horizontal<WIDTH;horizontal++) {
                    int index=(vertical*WIDTH+horizontal)*4, rgb=0;
                    for(int channel=0;channel<3;channel++) { float value=pixels.get(index+channel); check(Float.isFinite(value),"finite radiance"); sum+=value; peak=Math.max(peak,value); int encoded=(int)(255*display.get(index+channel)); rgb|=Math.max(0,Math.min(255,encoded))<<(16-channel*8); }
                    image.setRGB(horizontal,HEIGHT-1-vertical,rgb);
                }
                double mean=sum/(WIDTH*HEIGHT*3); minimumMean=Math.min(minimumMean,mean); maximumMean=Math.max(maximumMean,mean);
                ImageIO.write(image,"png",output.resolve("reference-"+steps+".png").toFile());
                System.out.println("Synthetic fixture: steps="+steps+", mean="+sum/(WIDTH*HEIGHT*3)+", peak="+peak+" (not Minecraft performance or scene certification)");
            }
            check(maximumMean/minimumMean<1.10,"24/48/96 sample energy convergence within 10 percent");
            // A black transparent surface suppresses the rear gas but retains front emission.
            previewGlow=0; clear(bloom,0,0,0,1); clear(scene,0,0,0,1); clear(opaque,0,0,0,1);
            clear(surface,1,1,1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(17),0,0,0});
            solve(integrate,solution,opaque,surface,48,3.2f); compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f);
            float[] clearGlass=new float[WIDTH*HEIGHT*4]; result.bind(); glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_FLOAT,clearGlass);
            clear(surface,0,0,0,1); compose(composite,result,scene,opaque,surface,solution,bloom,48,3.2f);
            float[] blackGlass=new float[WIDTH*HEIGHT*4]; result.bind(); glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_FLOAT,blackGlass);
            double retained=0, attenuated=0;
            for(int index=0;index<clearGlass.length;index+=4) { retained+=blackGlass[index]; attenuated+=clearGlass[index]-blackGlass[index]; }
            check(retained>1 && attenuated>1,"transparent surface retains front disk light and attenuates rear disk light");
            verifyBloom(arguments,output,integrate,composite,blur,solution,opaque,surface,scene,result,bloom,bloomTemp);
            comparison(output);
            opaque.resize(1920,1080); surface.resize(1920,1080); scene.resize(1920,1080); result.resize(1920,1080); bloom.resize(480,270); bloomTemp.resize(480,270);
            clear(opaque,0.014f,0.019f,0.032f,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0}); clear(surface,1,1,1,1); glClearBufferfv(GL_COLOR,1,new float[]{depth(200),0,0,0}); clear(scene,0.014f,0.019f,0.032f,1);
            previewGlow=0.7f;
            try(var previousIntegrate=baseline(arguments,"integrate"); var previousBlur=baseline(arguments,"bloom"); var previousComposite=baseline(arguments,"composite"); var previousSolution=new GlTarget(GL_RGBA16F,GL_RGBA16F,GL_RGBA16F,GL_RGBA32F)) {
            for(int steps:new int[]{24,48,96}) {
                double scale=steps==24?0.375:steps==96?0.75:0.5; solution.resize((int)(1920*scale),(int)(1080*scale)); previousSolution.resize(solution.width,solution.height);
                solve(integrate,solution,opaque,surface,steps,3.2f); bloom(blur,bloomTemp,bloom,solution,surface,opaque); compose(composite,result,scene,opaque,surface,solution,bloom,steps,3.2f);
                Runnable current=() -> { solve(integrate,solution,opaque,surface,steps,3.2f); bloom(blur,bloomTemp,bloom,solution,surface,opaque); compose(composite,result,scene,opaque,surface,solution,bloom,steps,3.2f); };
                Runnable previous=previousIntegrate==null?current:() -> { solve(previousIntegrate,previousSolution,opaque,surface,steps,3.2f); bloom(previousBlur,bloomTemp,bloom,previousSolution,surface,opaque); compose(previousComposite,result,scene,opaque,surface,previousSolution,bloom,steps,3.2f); };
                double[] before=new double[3], after=new double[3];
                for(int round=0;round<3;round++) { before[round]=measure(previous); after[round]=measure(current); }
                java.util.Arrays.sort(before); java.util.Arrays.sort(after);
                System.out.println("Synthetic 1080p kernel benchmark: steps="+steps+", solve="+solution.width+"x"+solution.height+", GPU="+after[1]+" ms (median of 3 batches; full-screen fixture; excludes capture, Minecraft, Oculus and EndingLibrary)");
                if(previousIntegrate!=null) System.out.println("Same-context baseline: steps="+steps+", before="+before[1]+" ms, after="+after[1]+" ms, delta="+(after[1]-before[1])+" ms");
            }
            }
            int priorProgram=composite.handle(); glUseProgram(priorProgram); glViewport(3,5,71,89); glEnable(GL_BLEND); glEnable(GL_DEPTH_TEST); glEnable(GL_SCISSOR_TEST); glColorMask(false,true,false,true); glDepthMask(true);
            glActiveTexture(GL_TEXTURE0+10); glBindTexture(GL_TEXTURE_2D,scene.textures[0]); int priorFramebuffer=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            glDisablei(GL_BLEND,1); glColorMaski(1,true,false,true,false);
            org.lwjgl.opengl.ARBDrawBuffersBlend.glBlendFuncSeparateiARB(1,GL_ZERO,GL_ONE,GL_ONE,GL_ZERO);
            try(var state=GlState.capture()) { GlState.fullscreen(); bloom(blur,bloomTemp,bloom,solution,surface,opaque); glBlendFuncSeparate(GL_ONE,GL_SRC_ALPHA,GL_ZERO,GL_SRC_ALPHA); glViewport(0,0,10,10); glUseProgram(copy.handle()); glActiveTexture(GL_TEXTURE0+10); glBindTexture(GL_TEXTURE_2D,bloom.textures[0]); opaque.bind(); }
            int[] viewport=new int[4]; glGetIntegerv(GL_VIEWPORT,viewport);
            check(glGetInteger(GL_CURRENT_PROGRAM)==priorProgram && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==priorFramebuffer,"program/framebuffer restored");
            check(viewport[0]==3 && viewport[1]==5 && viewport[2]==71 && viewport[3]==89,"viewport restored");
            check(glIsEnabled(GL_BLEND) && glIsEnabled(GL_DEPTH_TEST) && glIsEnabled(GL_SCISSOR_TEST) && glGetBoolean(GL_DEPTH_WRITEMASK),"enables/depth mask restored");
            check(glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE0+10 && glGetInteger(GL_TEXTURE_BINDING_2D)==scene.textures[0],"texture unit restored");
            java.nio.ByteBuffer mask=BufferUtils.createByteBuffer(4); glGetBooleani_v(GL_COLOR_WRITEMASK,1,mask);
            check(!glIsEnabledi(GL_BLEND,1) && mask.get(0)!=0 && mask.get(1)==0 && mask.get(2)!=0 && mask.get(3)==0,"independent color mask/blend enable restored");
            check(glGetIntegeri(GL_BLEND_SRC_RGB,1)==GL_ZERO && glGetIntegeri(GL_BLEND_DST_RGB,1)==GL_ONE,"independent blend factors restored");
            int unpack=glGenBuffers(); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack); glBufferData(GL_PIXEL_UNPACK_BUFFER,16,GL_STATIC_DRAW); bloomTemp.resize(123,77);
            check(glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)==unpack,"texture allocation preserves PBO binding"); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0); glDeleteBuffers(unpack);
            glActiveTexture(GL_TEXTURE0+10); glBindTexture(GL_TEXTURE_2D,scene.textures[0]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack=glGenBuffers()); glBufferData(GL_PIXEL_UNPACK_BUFFER,16,GL_STATIC_DRAW);
            glPixelStorei(GL_UNPACK_ALIGNMENT,8); glPixelStorei(GL_UNPACK_ROW_LENGTH,113); glPixelStorei(GL_UNPACK_SKIP_ROWS,7); glPixelStorei(GL_UNPACK_SKIP_PIXELS,5);
            int uploaded;
            try(var reload=detail()) {
                uploaded=reload.handle();
                check(glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE0+10 && glGetInteger(GL_TEXTURE_BINDING_2D)==scene.textures[0],"material upload preserves texture state");
                check(glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)==unpack && glGetInteger(GL_UNPACK_ALIGNMENT)==8 && glGetInteger(GL_UNPACK_ROW_LENGTH)==113 && glGetInteger(GL_UNPACK_SKIP_ROWS)==7 && glGetInteger(GL_UNPACK_SKIP_PIXELS)==5,"material upload preserves unpack state");
                glBindTexture(GL_TEXTURE_2D,uploaded);
                check(glGetTexLevelParameteri(GL_TEXTURE_2D,9,GL_TEXTURE_WIDTH)==1,"complete material mip chain");
                check(glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S)==GL_REPEAT && glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T)==GL_REPEAT,"periodic material wrap");
            }
            check(!glIsTexture(uploaded),"resource reload deletes old material texture");
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0); glDeleteBuffers(unpack);
            glPixelStorei(GL_UNPACK_ALIGNMENT,4); glPixelStorei(GL_UNPACK_ROW_LENGTH,0); glPixelStorei(GL_UNPACK_SKIP_ROWS,0); glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);
            GlState.fullscreen(); int oldFramebuffer=bloom.framebuffer, oldTexture=bloom.textures[0]; bloom.close(); check(!glIsFramebuffer(oldFramebuffer) && !glIsTexture(oldTexture),"target deletion");
            bloom.resize(100,70); check(bloom.width==100 && bloom.height==70,"resize recreation");
            check(glGetError()==GL_NO_ERROR,"no GL errors");
        } finally { glDeleteVertexArrays(vertexArray); glfwDestroyWindow(window); glfwTerminate(); }
        System.out.println("PASS: "+checks+" independent OpenGL checks; no Minecraft client was started.");
    }
}
