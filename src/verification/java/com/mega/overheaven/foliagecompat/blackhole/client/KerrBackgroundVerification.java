package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.RenderStabilityGpuVerification.image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Exercise each tier's production solver and composition on deterministic scene/depth pairs. */
final class KerrBackgroundVerification {
    private static final int DRY=0,WATER=1,CLEAR_WATER=2;
    private static final float[] WATER_RESIDUAL={.025f,.115f,.245f};
    private static final String FIXTURE="""
        #version 330 core
        in vec2 texCoord;
        layout(location=0) out vec4 Color;
        layout(location=1) out float Depth;
        uniform mat4 VP;
        uniform int Layer,Pattern,Wet;
        uniform float Plane;
        float depthAtZ(float z) {
            if(z<=0.) return 1.;
            vec4 clip=VP*vec4(0,0,-z,1);
            return clip.z/clip.w*.5+.5;
        }
        void main() {
            vec2 p=texCoord;
            float z=Plane,surfaceZ=Plane;
            vec3 base=vec3(p,.15),residual=vec3(0),transmission=vec3(1);
            if(Pattern==1) base=vec3(step(.5,fract(17.*p.x+3.*p.y)));
            if(Pattern==3) {
                bool land=p.y<.63+.04*sin(p.x*30.);
                z=surfaceZ=land?Plane:0.;
                base=land?vec3(0,1,0):Layer==2?vec3(p.y,0,1):vec3(1,0,0);
            }
            if(Pattern==2 || Pattern==4) {
                float ridge=.64+.065*sin(17.*p.x)+.045*cos(32.*p.x);
                ridge=floor(ridge*100.)/100.;
                z=p.y<ridge?80.:0.;
                base=z==0.?mix(vec3(.52,.70,.88),vec3(.10,.23,.47),p.y):
                    mix(vec3(.18,.28,.23),vec3(.30,.39,.31),step(.5,fract(p.y*71.+p.x*2.)));
                float ground=9./max(.001,.66-p.y);
                bool onGround=ground<100. && (z==0. || ground<z);
                if(onGround) {
                    z=ground;
                    vec2 world=(p-vec2(.5,.66))*vec2(2.5,1.)*ground;
                    float grass=.5+.5*sin(floor(world.x*3.)*.7+floor(ground*3.)*1.8);
                    base=mix(vec3(.15,.25,.10),vec3(.27,.38,.16),grass);
                }
                surfaceZ=z;
                if(Wet!=0 && z>15. && z<70. && abs(p.y-(.37+.065*sin(p.x*13.)))<.043) {
                    surfaceZ=z-1.;
                    if(Wet==1) { transmission=vec3(.35,.62,.80);residual=vec3(.025,.115,.245); }
                }
                if(Pattern==4) base=onGround?vec3(0,1,0):z==0.?vec3(0,0,1):vec3(1,0,0);
                else if(p.x>.72 && p.x<.75 && p.y>.36 && p.y<.70) {
                    z=surfaceZ=5.;base=vec3(1,0,1);transmission=vec3(1);residual=vec3(0);
                }
            }
            Depth=depthAtZ(Layer==1?surfaceZ:z);
            Color=vec4(Layer==0?base:Layer==1?transmission:residual+transmission*base,1);
        }
        """;

    private static void require(boolean condition,String label) {
        if(!condition) throw new AssertionError(label);
    }

    private static double colorDifference(float[] a,float[] b,int i) {
        return Math.max(Math.abs(a[i]-b[i]),Math.max(Math.abs(a[i+1]-b[i+1]),Math.abs(a[i+2]-b[i+2])));
    }

    private static void fill(RenderStabilityGpuVerification.Pipeline p,GlProgram fixture,int pattern,float plane,int water) {
        GlState.fullscreen();
        fixture.use();fixture.matrix("VP",p.vp);fixture.integer("Pattern",pattern);
        fixture.scalar("Plane",plane);fixture.integer("Wet",water);
        GlTarget[] layers={p.opaque,p.surface,p.scene};
        for(int i=0;i<layers.length;i++) {
            layers[i].bind();fixture.integer("Layer",i);
            glBindVertexArray(RenderStabilityGpuVerification.vao);glDrawArrays(GL_TRIANGLES,0,3);
        }
    }

    // Read only the incident ray, projection at infinity and disk coverage. The
    // expected terrain intersection is calculated independently in Java below.
    private static GlProgram rayProbe(Path shaders)throws Exception {
        String fragment="#version 330 core\nin vec2 texCoord;out vec4 Ray;\n"
            +"uniform sampler2D FrontTexture,BackTexture,LensTexture,MetaTexture;\n"
            +VerificationShaders.read(shaders,"field.glsl")+"\n"+VerificationShaders.read(shaders,"reconstruct.glsl")+"""
            void main() {
                vec3 dir=rayAt(texCoord);
                float opaque=distanceAt(texCoord,texture(OpaqueDepth,texCoord).r);
                vec2 bounds=sphere(Eye-Center,dir,bodyExtent());
                Ray=vec4(texCoord,-1.,1.);
                if(bounds.y<=0. || max(bounds.x,0.)>=opaque) return;
                Field f;
                if(relativistic()) {
                    ivec2 pixel=ivec2(gl_FragCoord.xy);
                    Ray.xy=texelFetch(LensTexture,pixel,0).xy;
                    Ray.z=texelFetch(MetaTexture,pixel,0).w;
                    f.front=texelFetch(FrontTexture,pixel,0);
                    f.back=texelFetch(BackTexture,pixel,0);
                } else {
                    float surface=distanceAt(texCoord,texture(SurfaceDepth,texCoord).r);
                    if(surface>=opaque-max(.002,opaque*.00001)) surface=opaque;
                    vec2 core=sphere(Eye-Center,dir,coreRadius());
                    bool captured=core.y>0. && max(0.,core.x)<opaque;
                    float weights;
                    reconstructRadiance(texCoord,opaque,surface,captured,f,weights);
                    if(weights<.01) f=integrate(texCoord);
                    vec3 sky=environmentLens(texCoord,dir,1000000.,bounds);
                    float pivot=max(dot(Center-Eye,dir),Radius);
                    if(!captured && sky.z>=0. && opaque>pivot) Ray.xyz=vec3(sky.xy,pivot);
                }
                Ray.w=f.front.a==1. && f.back.a==1. && all(lessThan(f.front.rgb+f.back.rgb,vec3(.0001)))?1.:0.;
            }
            """;
        return new GlProgram(VerificationShaders.read(shaders,"fullscreen.vsh"),fragment);
    }

    private static float[] rays(RenderStabilityGpuVerification.Pipeline p,GlProgram probe,GlTarget target) {
        target.bind();probe.use();p.material(probe,false);
        probe.texture("FrontTexture",3,p.solution.textures[0]);probe.texture("BackTexture",4,p.solution.textures[1]);
        probe.texture("LensTexture",5,p.solution.textures[2]);probe.texture("MetaTexture",6,p.solution.textures[3]);
        glBindVertexArray(RenderStabilityGpuVerification.vao);glDrawArrays(GL_TRIANGLES,0,3);
        return image(target,0);
    }

    private static void png(float[] data,int width,int height,Path file)throws Exception {
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            int start=(y*width+x)*4,color=0;
            for(int c=0;c<3;c++) color=color<<8 | Math.round(255*Math.min(1,Math.max(0,data[start+c])));
            result.setRGB(x,height-1-y,color);
        }
        ImageIO.write(result,"png",file.toFile());
    }

    private static int mixedContours(RenderStabilityGpuVerification.Pipeline p,float[] data) {
        int mixed=0,eligible=0;
        for(int y=0;y<p.height;y++) for(int x=0;x<p.width;x++) {
            double dx=(2*(x+.5)/p.width-1)/p.vp.m00(),dy=(2*(y+.5)/p.height-1)/p.vp.m11();
            double impact=p.distance*Math.sqrt((dx*dx+dy*dy)/(1+dx*dx+dy*dy));
            double extent=p.steps>=96?32*3.2/Math.sqrt(27):11*1.35+.12*2;
            if(impact<extent*.79 || impact>extent*.95 || y<30 || y>=p.height-30) continue;
            eligible++;
            float v=data[(y*p.width+x)*4];
            if(v>.04 && v<.96) mixed++;
        }
        require(eligible>5000,"missing outer support samples");
        return mixed;
    }

    private static void ridge(RenderStabilityGpuVerification.Pipeline p,GlProgram fixture,GlProgram probe,GlTarget target) {
        // The only possible intersections are with a plane at z=65 or with the sky.
        // Classify them independently using the captured input and projected sky direction.
        float z=65;
        fill(p,fixture,3,z,DRY);p.frame(1);
        float[] map=rays(p,probe,target);
        float[] host=image(p.scene,0),color=image(p.out,0);
        int valid=0,missed=0,movedRidge=0,offscreenHits=0,hiddenSky=0,shortenedSky=0;
        int marginX=(int)Math.ceil(6.*p.width/Math.min(p.width,p.height));
        int marginY=(int)Math.ceil(6.*p.height/Math.min(p.width,p.height));
        for(int y=0;y<p.height;y++) for(int x=0;x<p.width;x++) {
            int i=4*(y*p.width+x);
            if(map[i+2]<0 || map[i+3]==0) continue;
            double u=(x+.5)/p.width,v=(y+.5)/p.height;
            double dx=(2*u-1)/p.vp.m00(),dy=(2*v-1)/p.vp.m11();
            double pivotZ=map[i+2]/Math.sqrt(1+dx*dx+dy*dy);
            double t=1-pivotZ/z;
            int sx=(int)Math.floor(p.width*(u+t*(map[i]-u))),sy=(int)Math.floor(p.height*(v+t*(map[i+1]-v)));
            int skyX=(int)Math.floor(map[i]*p.width),skyY=(int)Math.floor(map[i+1]*p.height);
            if(sx<marginX || sx>=p.width-marginX || sy<marginY || sy>=p.height-marginY) continue;
            boolean skyVisible=skyX>=marginX && skyX<p.width-marginX && skyY>=marginY && skyY<p.height-marginY;
            boolean hit=host[(sy*p.width+sx)*4+1]>.99;
            if(!hit && skyVisible && host[(skyY*p.width+skyX)*4+1]>.99 && host[i+2]>.99 && Math.abs(map[i+1]-v)>.025) {
                require(color[i+2]>.98,"hidden sky imports ridge color");
                double fraction=(color[i]-host[i])/(map[i+1]-v);
                // The scene stores texel-centred v in FP16 and samples nearest.
                double rounding=.5/p.height+.00049;
                require(color[i]>=Math.min(v,map[i+1])-rounding && color[i]<=Math.max(v,map[i+1])+rounding,
                    "hidden sky leaves projected path at "+x+","+y+" sampled="+color[i]+" limits="+v+".."+map[i+1]);
                hiddenSky++;
                if(fraction>.1) shortenedSky++;
            }
            if(!hit && (!skyVisible || host[(skyY*p.width+skyX)*4+2]<.99)) continue;
            valid++;
            if(hit && host[i+2]>.99) movedRidge++;
            if(hit && !skyVisible) offscreenHits++;
            if(color[i+(hit?1:2)]<.98) {
                if(missed==0) System.out.printf(Locale.ROOT,
                    "First ridge mismatch dst=(%d,%d) plane=(%.5f,%.5f) sky=(%.4f,%.4f) expected land=%b rgb=(%.4f,%.4f,%.4f)%n",
                    x,y,p.width*(u+t*(map[i]-u)),p.height*(v+t*(map[i+1]-v)),map[i]*p.width,map[i+1]*p.height,hit,color[i],color[i+1],color[i+2]);
                missed++;
            }
        }
        System.out.printf(Locale.ROOT,"Analytic ridge: %d mismatches / %d, lensed land over original sky=%d, finite hits with unavailable sky endpoint=%d%n",missed,valid,movedRidge,offscreenHits);
        require(valid>10000 && movedRidge>100 && offscreenHits>10,"ridge coverage");
        require(missed==0,"resolved ridge or late sky sampled at the wrong depth");
        require(hiddenSky>100 && shortenedSky>hiddenSky*.85,"sky snaps to original gradient along hidden ridge");
    }

    private static void split(RenderStabilityGpuVerification.Pipeline p,GlProgram fixture) {
        fill(p,fixture,2,0,WATER);
        p.displaySpace=false;p.split=false;p.frame(1);
        float[] full=image(p.out,0);
        p.front.bind();glDisable(GL_SCISSOR_TEST);glClearBufferfv(GL_COLOR,0,new float[]{0,0,0,1});
        p.split=true;p.frame(1);
        float[] front=image(p.front,0),back=image(p.out,0);
        double error=0;
        for(int i=0;i<full.length;i+=4) for(int c=0;c<3;c++)
            error=Math.max(error,Math.abs(full[i+c]-front[i+c]-front[i+3]*back[i+c])/Math.max(1,Math.abs(full[i+c])));
        System.out.printf(Locale.ROOT,"HDR front/back recomposition max relative error %.5f%n",error);
        require(error<.002,"HDR split mismatch");
        p.split=false;p.displaySpace=true;
    }

    private static void slopingGround(RenderStabilityGpuVerification.Pipeline p,GlProgram fixture,GlProgram probe,GlTarget target) {
        fill(p,fixture,4,0,DRY);p.frame(1);
        float[] map=rays(p,probe,target);
        float[] host=image(p.scene,0),color=image(p.out,0);
        int valid=0,missed=0;
        for(int y=0;y<p.height;y++) for(int x=0;x<p.width;x++) {
            int i=(y*p.width+x)*4;
            if(map[i+2]<0 || map[i+3]==0) continue;
            double u=(x+.5)/p.width,v=(y+.5)/p.height,du=map[i]-u,dv=map[i+1]-v;
            double dx=(2*u-1)/p.vp.m00(),dy=(2*v-1)/p.vp.m11();
            double pivotZ=map[i+2]/Math.sqrt(1+dx*dx+dy*dy);
            // Intersect z=pivotZ/(1-t) with the ground z=9/(.66-v-t*dv).
            double t=(9-pivotZ*(.66-v))/(9-pivotZ*dv);
            double ridgeT=1-pivotZ/80;
            if(!(t>=0 && t<ridgeT)) continue;
            int sx=(int)Math.floor(p.width*(u+t*du)),sy=(int)Math.floor(p.height*(v+t*dv));
            if(sx<14 || sx>=p.width-14 || sy<14 || sy>=p.height-14) continue;
            boolean interior=true;
            for(int oy=-1;oy<=1;oy++) for(int ox=-1;ox<=1;ox++)
                if(host[((sy+oy)*p.width+sx+ox)*4+1]<.99) interior=false;
            if(!interior) continue;
            valid++;
            if(color[i+1]<.98) missed++;
        }
        System.out.printf(Locale.ROOT,"Analytic sloping ground before ridge: %d missing / %d interior hits%n",missed,valid);
        require(valid>1000 && missed==0,"sloping terrain loses verified interior hits");
    }

    static void run(RenderStabilityGpuVerification.Pipeline old,RenderStabilityGpuVerification.Pipeline current,
                    Path shaders,Path output,boolean measure,boolean unchanged)throws Exception {
        try(var fixture=new GlProgram(VerificationShaders.read(shaders,"fullscreen.vsh"),FIXTURE);
            var probe=rayProbe(shaders);var target=new GlTarget(GL_RGBA32F)) {
            target.resize(current.width,current.height);
            ridge(current,fixture,probe,target);
            slopingGround(current,fixture,probe,target);
            split(current,fixture);
            float[][] baselineLight=new float[2][];
            float[] baselineWet=null;
            for(var p:new RenderStabilityGpuVerification.Pipeline[]{old,current}) {
                boolean candidate=p==current;
                String label=candidate?"after":"before";
                fill(p,fixture,1,0,DRY);p.frame(1);
                float[] stripes=image(p.out,0);
                int doubled=mixedContours(p,stripes);
                png(stripes,p.width,p.height,output.resolve(label+"-stripes.png"));
                fill(p,fixture,2,0,DRY);p.frame(1);float[] dry=image(p.out,0),dryRays=rays(p,probe,target);
                // Separately vary layer depth and layer color: reduced-resolution
                // disk reconstruction itself uses the two depths to choose taps.
                fill(p,fixture,2,0,CLEAR_WATER);p.frame(1);float[] clear=image(p.out,0);
                fill(p,fixture,2,0,WATER);p.frame(1);
                float[] wet=image(p.out,0),host=image(p.scene,0),trans=image(p.surface,0),map=rays(p,probe,target);
                png(wet,p.width,p.height,output.resolve(label+".png"));
                float[] front=image(p.solution,0),back=image(p.solution,1);
                if(!candidate) baselineWet=wet;
                else if(unchanged) require(Arrays.equals(wet,baselineWet),"HIGH terrain/water composite changed");
                if(measure) {
                    if(candidate) require(Arrays.equals(front,baselineLight[0]) && Arrays.equals(back,baselineLight[1]),
                        "foreground and background disk radiation/transmittance changed");
                    else { baselineLight[0]=front;baselineLight[1]=back; }
                }
                int ghosts=0,dropouts=0,wall=0,copiedWall=0,water=0;
                double waterError=0,wallError=0;
                for(int i=0;i<wet.length;i+=4) {
                    boolean wallPixel=host[i]>.99 && host[i+1]==0 && host[i+2]>.99;
                    if(wallPixel) {
                        wall++;
                        for(int c=0;c<3;c++) wallError=Math.max(wallError,Math.abs(wet[i+c]-host[i+c]));
                    } else if(wet[i]>.99 && wet[i+1]<.001 && wet[i+2]>.99) copiedWall++;
                    if(trans[i]==1) {
                        if(colorDifference(wet,clear,i)>.005) ghosts++;
                        if(dryRays[i+3]==1 && map[i+3]==1 && colorDifference(clear,dry,i)>.005) dropouts++;
                    } else {
                        int x=i/4%p.width;
                        if(x>p.width*.29 && x<p.width*.71 && map[i+3]==1.) {
                            water++;
                            for(int c=0;c<3;c++)
                                waterError=Math.max(waterError,Math.abs(wet[i+c]-(clear[i+c]*trans[i+c]+WATER_RESIDUAL[c])));
                        }
                    }
                }
                // Read encoded source coordinates on a plane moving through the old support boundary.
                int px=p.steps>=96?422:392,py=225;
                float[] sample=new float[4];
                float original=(px+.5f)/p.width,previous=original,near=0,far=0,sky=0,maxJump=0;
                for(int z=20;z<=100;z+=2) {
                    fill(p,fixture,0,z,DRY);p.frame(1);
                    p.out.bind();glReadBuffer(GL_COLOR_ATTACHMENT0);glReadPixels(px,py,1,1,GL_RGBA,GL_FLOAT,sample);
                    float u=sample[0];
                    maxJump=Math.max(maxJump,Math.abs(u-previous)*p.width);previous=u;
                    if(z==20) near=u;
                    if(z==100) far=u;
                }
                fill(p,fixture,0,0,DRY);p.frame(1);
                p.out.bind();glReadBuffer(GL_COLOR_ATTACHMENT0);glReadPixels(px,py,1,1,GL_RGBA,GL_FLOAT,sample);sky=sample[0];
                System.out.printf(Locale.ROOT,"%s: mixed contours=%d, copied water colors=%d, missing background under water sources=%d, "+
                    "wall=%d copies=%d, water residual error=%.5f (%d), plane max jump=%.2fpx, near/far/sky offset=%.2f/%.2f/%.2fpx%n",
                    label,doubled,ghosts,dropouts,wall,copiedWall,waterError,water,maxJump,(near-original)*p.width,(far-original)*p.width,(sky-original)*p.width);
                if(candidate) {
                    require(doubled==0,"outer support draws two different ridge colors");
                    require(ghosts==0 && dropouts==0 && water>100 && waterError<.004,"water must affect only its own layer, exactly once");
                    require(wall>100 && wallError==0 && copiedWall==0,"foreground wall changed or was imported elsewhere");
                    require(Math.abs(near-original)*p.width<.6 && maxJump<4,"binary switch as terrain crosses support");
                    require(Math.abs(far-original)*p.width>4 && Math.abs(sky-original)>Math.abs(far-original)*1.2,
                        "finite scenery and sky must have distinct visible deflections");
                }
            }
            if(measure) {
                for(var p:new RenderStabilityGpuVerification.Pipeline[]{old,current}) {
                    p.glowStrength=.7f;
                    p.resize(1920,1080);
                    fill(p,fixture,2,0,WATER);
                }
                double[] ms=RenderStabilityGpuVerification.paired(()->old.frame(1),()->current.frame(1));
                String quality=current.steps==24?"LOW":current.steps==48?"BALANCED":"HIGH";
                System.out.printf(Locale.ROOT,"%s 1080p ridge/river, full subject with bloom, paired GPU medians: %.3f -> %.3f ms%n",quality,ms[0],ms[1]);
            }
        }
    }
}
