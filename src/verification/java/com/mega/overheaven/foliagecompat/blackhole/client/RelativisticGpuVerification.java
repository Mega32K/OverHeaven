package com.mega.overheaven.foliagecompat.blackhole.client;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.glfw.GLFW.*;
import static com.mega.overheaven.foliagecompat.blackhole.client.RenderStabilityGpuVerification.image;
import org.lwjgl.opengl.GL;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Analytic GR anchors plus production GLSL/host-depth checks; never launches Minecraft. */
public final class RelativisticGpuVerification {
    private static int checks,vao;
    private static Path source,output;
    private static void check(boolean ok,String reason) { if(!ok) throw new AssertionError(reason);checks++; }
    private static void draw() { glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,3); }
    private static final class Pipeline extends RenderStabilityGpuVerification.Pipeline {
        boolean high=true,dark,uniform;float spin=.6f,rotation=.25f,tilt=30,lensing=1,radius=3.2f,inner=3.8f,outer=11,thickness=.12f;
        final int constantDisk;
        final Path directory;
        Boolean shaderHigh;
        Pipeline(Path path)throws Exception {
            super(false,path);directory=path;constantDisk=glGenTextures();glBindTexture(GL_TEXTURE_2D,constantDisk);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,1,1,0,GL_RGBA,GL_FLOAT,new float[]{.7f,.7f,.7f,1});
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        }
        void quality(int value) {
            steps=value;high=value>=96;scale=high?1:value<=24?.375f:.5f;resize(width,height);
            if(Files.exists(directory.resolve("kerr.glsl")) && (shaderHigh==null || shaderHigh!=high)) {
                for(var p:new GlProgram[]{solve,compose,blur,frontShader,composeFull,blurFull,frontFull})p.close();
                try {
                    solve=variant("integrate",false);compose=variant("composite",false);blur=variant("bloom",false);frontShader=variant("subject_front",false);
                    composeFull=variant("composite",true);blurFull=variant("bloom",true);frontFull=variant("subject_front",true);
                } catch(Exception e) {throw new IllegalStateException(e);}
                shaderHigh=high;
            }
        }
        GlProgram variant(String name,boolean full)throws Exception {
            String f=VerificationShaders.read(directory,name+".fsh");
            return new GlProgram(VerificationShaders.read(directory,"fullscreen.vsh"),GlProgram.qualitySource(full?GlProgram.fullTargetSource(f):f,high));
        }
        @Override void inputs() { super.inputs();if(dark)for(var t:new GlTarget[]{opaque,scene}) {t.bind();glClearBufferfv(GL_COLOR,0,new float[]{.004f,.005f,.008f,1});} }
        @Override void material(GlProgram p,boolean solving) {
            super.material(p,solving);p.integer("Relativistic",high?1:0);
            p.scalar("Spin",spin);
            if(finaleTicks>=0 && timeline.frame(finaleTicks).phase()==com.mega.overheaven.foliagecompat.finale.FinaleTimeline.Phase.RETURN)
                p.scalar("FinaleAmount",1-com.mega.overheaven.foliagecompat.finale.FinaleTimeline.smooth(0,1,timeline.frame(finaleTicks).progress()));
            double angle=Math.toRadians(tilt);
            p.vector("DiskNormal",0,(float)Math.cos(angle),(float)Math.sin(angle));p.vector("DiskForward",0,(float)-Math.sin(angle),(float)Math.cos(angle));
            p.scalar("Rotation",rotation);p.scalar("Radius",radius);p.scalar("Lensing",lensing);p.scalar("Inner",inner);p.scalar("Outer",outer);p.scalar("Thickness",thickness);
            advection.evaluate(time,rotation,.12,outer-inner,12);advection.apply(p);
            if(uniform)p.texture("DiskTexture",10,constantDisk);
        }
        @Override void bounds() {
            super.bounds();
            if(high) {
                BlackHoleScreenBounds b=new BlackHoleScreenBounds();
                double extent=Math.max(outer*1.35+thickness*2,32*radius*lensing/Math.sqrt(27));
                if(finaleTicks>=0) {
                    var state=new com.mega.overheaven.foliagecompat.finale.CausalTransition.State();
                    com.mega.overheaven.foliagecompat.finale.CausalTransition.evaluate(com.mega.overheaven.foliagecompat.finale.FinaleTimeline.defaults(),finaleTicks,-1,state);
                    extent*=Math.max(1,state.bodyScale);
                    extent=Math.max(extent,Math.max(32,waves.support));
                }
                check(b.project(vp,new org.joml.Vector4f(0,0,0,1),centerX,0,-distance,extent,width,height),"high bounds");
                rect.normalized(b.left/(float)width-2f/solution.width,b.bottom/(float)height-2f/solution.height,b.right/(float)width+2f/solution.width,b.top/(float)height+2f/solution.height,solution.width,solution.height);
                int pad=glowStrength>0?GlBloom.padding(steps):0;
                glow.set(Math.max(0,b.left-pad),Math.max(0,b.bottom-pad),Math.min(width,b.right+pad),Math.min(height,b.top+pad));
            }
        }
        @Override public void close() { super.close();glDeleteTextures(constantDisk); }
    }
    private static void analyticProbes(Pipeline p)throws Exception {
        String text="#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+VerificationShaders.read(source,"field.glsl")+"""
            uniform int Probe;uniform float ProbeMass,Observer,Impact,ImpactSpan,RaySign;
            void main() {
                if(Probe==0) {
                    float b=Impact+(texCoord.x-.5)*ImpactSpan;
                    float s=b*sqrt(1.-2./Observer)/Observer;
                    Field f;f.front=vec4(0);f.back=vec4(0);f.lens=vec4(0);f.meta=vec4(0);
                    Color=traceSchwarzschild(f,vec3(s,0,RaySign*sqrt(1.-s*s)),ProbeMass,false);
                } else if(Probe==1) {
                    float r=mix(6.,60.,texCoord.x);
                    Color=vec4(orbitalBeta(1.,r),frequencyShift(1.,r,10000.,0.),frequencyShift(1.,r,10000.,.7),frequencyShift(1.,r,10000.,-.7));
                } else if(Probe==2) Color=vec4(thermalRadiance(mix(1000.,24000.,texCoord.x)),1.);
                else {
                    Field f;f.front=vec4(0,0,0,1);f.back=f.front;f.lens=vec4(0,0,65504,65504);f.meta=vec4(1000000,1000000,0,-1);
                    traceSchwarzschild(f,vec3(0,0,1),.615840287,true);Color=f.front;
                }
            }
            """;
        try(var probe=new GlProgram(VerificationShaders.read(source,"fullscreen.vsh"),text);var target=new GlTarget(GL_RGBA32F)) {
            target.resize(512,1);probe.use();p.material(probe,false);probe.integer("Probe",0);probe.scalar("RaySign",-1);
            for(float observer:new float[]{8,100,100000}) {
                probe.scalar("ProbeMass",1);probe.scalar("Observer",observer);probe.vector("Center",0,0,-observer);
                probe.scalar("Impact",Math.sqrt(27));probe.scalar("ImpactSpan",.04);target.bind();draw();float[] a=image(target,0);
                for(int i=0;i<512;i++) {
                    double impact=Math.sqrt(27)+((i+.5)/512-.5)*.04;
                    check(a[i*4+3]==(impact<Math.sqrt(27)?1:0),"critical shadow b=3sqrt(3)M observer="+observer+" b="+impact+" status="+a[i*4+3]);
                    if(a[i*4+3]==0)check(Math.abs(a[i*4+1]*a[i*4+1]-1/(impact*impact))<1e-5,"geodesic energy invariant");
                }
            }
            for(float observer:new float[]{2.1f,2.5f}) {
                probe.scalar("Observer",observer);probe.vector("Center",0,0,-observer);
                for(int sign:new int[]{-1,1}) {
                    probe.scalar("RaySign",sign);draw();float[] near=image(target,0);
                    for(int i=0;i<512;i++) {
                        boolean captured=sign<0 || i>=256;
                        check(near[i*4+3]==(captured?1:0),"inside photon sphere escape cone r="+observer+" sign="+sign+" index="+i);
                    }
                }
            }
            probe.scalar("RaySign",-1);
            probe.scalar("Observer",100000);probe.vector("Center",0,0,-100000);probe.scalar("Impact",100);probe.scalar("ImpactSpan",0);draw();float[] weak=image(target,0);
            double bending=weak[2]-(Math.PI-Math.asin(100*Math.sqrt(1-2./100000)/100000));
            double expected=4./100+15*Math.PI/(4*10000);
            check(Math.abs(bending-expected)<.00012,"weak field 4M/b plus second order: "+bending);
            for(float mass:new float[]{.01f,10,100}) {
                probe.scalar("ProbeMass",mass);probe.vector("Center",0,0,-100000*mass);draw();float[] scaled=image(target,0);
                check(Math.abs(scaled[2]-weak[2])<1e-5 && scaled[3]==weak[3],"mass/length scaling");
            }
            probe.integer("Probe",1);draw();float[] shifts=image(target,0);
            for(int i=0;i<512;i++) {
                double r=6+54*(i+.5)/512;
                check(Math.abs(shifts[i*4]-Math.sqrt(1/(r-2)))<1e-6,"Keplerian local velocity");
                check(Math.abs(shifts[i*4+1]-Math.sqrt((1-3/r)/(1-2./10000)))<1e-6,"transverse plus gravitational redshift");
                check(shifts[i*4+2]>shifts[i*4+1] && shifts[i*4+1]>shifts[i*4+3],"approaching/receding shift order");
            }
            probe.integer("Probe",2);draw();float[] thermal=image(target,0);
            for(float v:thermal)check(Float.isFinite(v)&&v>=0,"finite positive Planck bands");
            check(thermal[0]>thermal[2] && thermal[510*4]<thermal[510*4+2],"thermal red to blue progression");
            probe.integer("Probe",3);probe.vector("Center",0,0,-4);probe.vector("DiskNormal",0,1,0);probe.vector("DiskRight",1,0,0);probe.vector("DiskForward",0,0,1);draw();float[] outward=image(target,0);
            check(outward[0]+outward[1]+outward[2]>.001 && outward[3]<1,"near radial outward escape integrates disk before infinity");
            System.out.printf(Locale.ROOT,"PASS critical shadow, mass scaling, null invariant, weak deflection %.7f rad, Kepler/gravity/Doppler, thermal bands%n",bending);
        }
    }
    private static void kerrProbes(Pipeline p)throws Exception {
        String text="#version 330 core\nin vec2 texCoord;out vec4 Color;\n"+VerificationShaders.read(source,"field.glsl")+"""
            uniform int Probe;uniform float Observer,Impact,ImpactSpan,Delay;
            void main() {
                if(Probe==0) {
                    float a=-Spin,r=Observer,delta=r*r-2.*r+a*a;
                    float area=pow(r*r+a*a,2.)-a*a*delta;
                    float lapse=sqrt(r*r*delta/area),omega=2.*a*r/area,lever=sqrt(area)/r;
                    float l=Impact+(texCoord.x-.5)*ImpactSpan;
                    float az=l*lapse/(lever*(1.-omega*l));
                    Field f;f.front=vec4(0);f.back=vec4(0);f.lens=vec4(0);f.meta=vec4(0);
                    Color=traceKerr(f,vec3(-az,0,-sqrt(max(0.,1.-az*az))),1.,false);
                } else if(Probe==1) {
                    KerrState s;KerrConstants c;
                    kerrInitial(vec3(0,Observer,0),vec3(0,-1,0),0.,s,c);
                    float target=1./mix(6.,30.,texCoord.x);
                    for(int i=0;i<256;i++) {
                        float h=min(.2*s.radial.x,target-s.radial.x);if(h<=0.)break;s=kerrStep(s,c,h);
                    }
                    float r=1./s.radial.x,time=Observer-r+s.extra;
                    float exact=Observer-r+2.*log((Observer-2.)/(r-2.));
                    Color=vec4(time,exact,s.extra,1.);
                } else if(Probe==2) {
                    float a=mix(-.95,.95,texCoord.x);Color=vec4(kerrIsco(a),kerrHorizon(a),kerrOrbit(6.,a,1.),1.);
                } else if(Probe==3) {
                    vec3 m=highMaterial(vec2(6.*cos(texCoord.x*6.2831853),6.*sin(texCoord.x*6.2831853)),.01,Delay,.615840287);
                    Color=vec4(m,1.);
                } else if(Probe==5) {
                    float r=mix(6.,60.,texCoord.x);KerrConstants c;c.a=0.;c.e=1.;c.l=0.;
                    Color=vec4(1./kerrEmitterEnergy(r,c),sqrt(1.-3./r),0.,1.);
                } else if(Probe==6) {
                    float theta=texCoord.x<.004?0.:texCoord.x>.996?3.14159265359:texCoord.x*3.14159265359;
                    vec3 origin=20.*vec3(sin(theta),0,cos(theta));KerrState s;KerrConstants c;
                    kerrInitial(origin,normalize(-origin+vec3(0,3,0)),-Spin,s,c);vec3 error=vec3(0.);
                    for(int i=0;i<256;i++) {
                        float h=min(.06/max(length(s.w),.05),.035/max(abs(s.radial.y),.01));
                        if(s.radial.y>0.)h=min(h,min(.25*s.radial.x/max(s.radial.y,.001),.6*(1./kerrHorizon(c.a)-s.radial.x)/max(s.radial.y,.001)));
                        s=kerrStep(s,c,h);s.n=normalize(s.n);s.w-=s.n*dot(s.n,s.w);
                        float l=cross(s.n,s.w).z,q=dot(s.w,s.w)-l*l-c.a*c.a*s.n.z*s.n.z,u=s.radial.x;
                        error=max(error,vec3(abs(l-c.l),abs(q-c.q),abs(s.radial.y*s.radial.y-(1.+u*u*(c.c+u*(2.*c.k-c.a*c.a*c.q*u))))));
                        if(u<=0. || u>=1./(kerrHorizon(c.a)*1.001))break;
                    }
                    Color=vec4(error,1.);
                } else {
                    // An exactly spherical zero-spin photon orbit must survive >4 turns.
                    Field f;f.front=vec4(0);f.back=vec4(0);f.lens=vec4(0);f.meta=vec4(0);
                    Color=traceKerr(f,vec3(1,0,0),1.,false);
                }
            }
            """;
        try(var probe=new GlProgram(VerificationShaders.read(source,"fullscreen.vsh"),text);var target=new GlTarget(GL_RGBA32F)) {
            target.resize(256,1);probe.use();p.material(probe,false);
            probe.vector("DiskRight",1,0,0);probe.vector("DiskForward",0,0,1);probe.vector("DiskNormal",0,1,0);
            probe.scalar("Observer",1000);probe.vector("Center",0,0,-1000);probe.integer("Probe",0);probe.scalar("ImpactSpan",.02);
            for(double spin:new double[]{-.95,-.6,0,.6,.95})for(int side:new int[]{-1,1}) {
                double a=-spin,r=2*(1+Math.cos(2./3*Math.acos(side*a)));
                double critical=a==0?side*Math.sqrt(27):-(r*r*r-3*r*r+a*a*r+a*a)/(a*(r-1));
                probe.scalar("Spin",spin);probe.scalar("Impact",critical);target.bind();draw();float[] rays=image(target,0);
                double maxError=0,maxAngle=0;
                for(int i=0;i<256;i++) {
                    double l=critical+((i+.5)/256-.5)*.02;
                    boolean captured=Math.abs(l)<Math.abs(critical);
                    check(rays[i*4]==(captured?1:0),"Kerr equatorial critical boundary spin="+spin+" L="+l+" status="+rays[i*4]);
                    maxError=Math.max(maxError,rays[i*4+2]);
                    if(rays[i*4]==0)maxAngle=Math.max(maxAngle,rays[i*4+1]);
                }
                check(maxError<.002,"Kerr radial invariant spin="+spin+" error="+maxError);
                if(spin==0)check(maxAngle>4*Math.PI,"escaping higher-order rays traverse >2 turns");
            }
            probe.scalar("Spin",0);probe.scalar("Observer",100000);probe.vector("Center",0,0,-100000);probe.scalar("Impact",100);probe.scalar("ImpactSpan",0);draw();float[] weak=image(target,0);
            double bend=weak[1]-(Math.PI-Math.asin(100*Math.sqrt(1-2./100000)/100000));
            check(weak[0]==0 && Math.abs(bend-(4./100+15*Math.PI/40000))<.00015,"Kerr a=0 weak deflection "+bend);
            probe.integer("Probe",1);probe.scalar("Spin",0);probe.scalar("Observer",1000);draw();float[] times=image(target,0);
            for(int i=0;i<256;i++)check(Math.abs(times[i*4]-times[i*4+1])<.003,"Schwarzschild radial Shapiro delay "+i+" error="+(times[i*4]-times[i*4+1]));
            probe.integer("Probe",2);draw();float[] values=image(target,0);
            for(int i=0;i<256;i++) {
                double a=-.95+1.9*(i+.5)/256,r=values[i*4];
                check(Math.abs(r*r-6*r+8*a*Math.sqrt(r)-3*a*a)<.00005,"Kerr ISCO polynomial");
                check(Math.abs(values[i*4+1]-(1+Math.sqrt(1-a*a)))<.000002,"Kerr horizon");
                check(Math.abs(values[i*4+2]-1/(Math.pow(6,1.5)+a))<.0000001,"Kerr angular velocity");
            }
            probe.integer("Probe",4);probe.vector("Center",0,0,-3);draw();float[] orbit=image(target,0);
            check(orbit[0]==2 && orbit[1]>8*Math.PI && orbit[2]<.00001,"higher-order photon orbit budget "+Arrays.toString(Arrays.copyOf(orbit,4)));
            probe.integer("Probe",5);draw();float[] energies=image(target,0);
            for(int i=0;i<256;i++)check(Math.abs(energies[i*4]-energies[i*4+1])<.000001,"zero-spin emitter frequency limit");
            probe.integer("Probe",6);
            for(float spin:new float[]{-.95f,0,.6f,.95f}) {
                probe.scalar("Spin",spin);draw();float[] angular=image(target,0);
                for(int i=0;i<angular.length;i++)check(Float.isFinite(angular[i]) && (i%4==3 || angular[i]<.0005),"Kerr pole/oblique constants spin="+spin+" channel="+i%4+" error="+angular[i]);
            }
            probe.scalar("Spin",.6);
            probe.integer("Probe",3);probe.scalar("Delay",.75);
            for(double boundary:new double[]{0,4,8,65536*8.,1e12}) {
                double t=boundary+.75;
                p.advection.evaluate(t-.0001,.25,.12,7.2,12);p.advection.apply(probe);draw();float[] before=image(target,0);
                p.advection.evaluate(t+.0001,.25,.12,7.2,12);p.advection.apply(probe);draw();float[] after=image(target,0);
                double difference=maxDifference(before,after);
                System.out.printf(Locale.ROOT,"CLOCK boundary=%.2f dt=.0002 maxDelta=%.8f%n",t,difference);
                // Linear texture filtering has finite subtexel weights; a few
                // millipercent changes are legitimate even at this tiny dt.
                check(difference<.02,"retarded cohort continuity at "+t+" delta="+difference);
            }
            p.advection.evaluate(12,.25,.12,7.2,12);p.advection.apply(probe);draw();float[] delayed=image(target,0);
            probe.scalar("Delay",0);p.advection.evaluate(11.25,.25,.12,7.2,12);p.advection.apply(probe);draw();
            check(maxDifference(delayed,image(target,0))<.00002,"retarded material equals earlier synchronized clock");
            System.out.println("PASS Kerr +/- spin capture, null invariant, ISCO, orbital velocity, Shapiro time, >4 photon turns, retarded cohort clock");
        }
    }
    private static void png(GlTarget target,String name)throws Exception {
        float[] pixels=image(target,0);BufferedImage result=new BufferedImage(target.width,target.height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<target.height;y++)for(int x=0;x<target.width;x++) {
            int rgb=0;for(int c=0;c<3;c++){double v=Math.max(0,pixels[(y*target.width+x)*4+c]);v=v/(1+v);rgb=rgb<<8|(int)Math.round(Math.pow(v,1/2.2)*255);}
            result.setRGB(x,target.height-1-y,rgb);
        }
        ImageIO.write(result,"png",output.resolve(name+".png").toFile());
    }
    private static double maxDifference(float[] a,float[] b) { double error=0;for(int i=0;i<a.length;i++){check(Float.isFinite(a[i])&&Float.isFinite(b[i]),"finite frame");error=Math.max(error,Math.abs(a[i]-b[i]));}return error; }
    private static double sideEnergy(float[] image,int width,boolean left) {double e=0;for(int i=0;i<image.length;i+=4)if(((i/4)%width<width/2)==left)e+=image[i]+image[i+1]+image[i+2];return e;}
    private static void pipelines(Pipeline p,Pipeline old)throws Exception {
        for(int quality:new int[]{24,48}) {
            for(var q:new Pipeline[]{p,old}) {q.quality(quality);q.resize(320,180);q.split=false;q.frame(1);}
            check(maxDifference(image(p.out,0),image(old.out,0))<.001,"low/balanced pixel regression "+quality);
        }
        p.quality(96);p.resize(640,360);p.constantDepth=true;p.dark=true;p.uniform=true;p.glowStrength=0;p.inputs();
        p.rotation=.25f;p.frame(1);float[] positive=image(p.out,0);png(p.out,"high-positive");
        p.rotation=-.25f;p.frame(1);float[] negative=image(p.out,0);png(p.out,"high-negative");
        double a=sideEnergy(positive,p.width,true)/sideEnergy(positive,p.width,false),b=sideEnergy(negative,p.width,true)/sideEnergy(negative,p.width,false);
        check((a-1)*(b-1)<0 && Math.abs(a-1)>.15 && Math.abs(b-1)>.15,"beaming flips with gas direction: "+a+" / "+b);
        p.rotation=.25f;p.frame(1);float[] frozen=image(p.out,0);p.frame(1);check(Arrays.equals(frozen,image(p.out,0)),"pause deterministic");
        float[] meta=image(p.solution,3),front=image(p.solution,0);int dark=0,foreground=0;
        for(int i=0;i<meta.length;i+=4)if(meta[i+2]==1){if(front[i]+front[i+1]+front[i+2]>.0001)foreground++;else dark++;}
        check(dark>0 && foreground>0,"captured rays retain foreground disk "+dark+" / "+foreground);
        p.uniform=false;p.glowStrength=.7f;p.resize(960,540);
        for(float tilt:new float[]{5,30,90}) {p.tilt=tilt;p.frame(1);png(p.out,"high-tilt-"+(int)tilt);}
        p.tilt=30;p.constantDepth=false;p.dark=false;p.split=true;
        for(int shape=0;shape<=3;shape++) {
            p.shape=shape;p.inputs();p.frame(1);float[] cache=image(p.front,0);p.compose(p.reference,2);
            check(maxDifference(cache,image(p.reference,0))<.003,"front-cache split equivalence "+shape);
            if(shape==3)check(maxDifference(image(p.out,0),image(p.scene,0))<.001,"foreground wall blocks high hole");
        }
        p.distance=22;p.shape=3;p.inputs();p.frame(1);
        check(maxDifference(image(p.out,0),image(p.scene,0))<.001,"near foreground wall inside overlay support stays unwarped");
        p.distance=35;
        p.shape=0;p.split=false;p.constantDepth=true;p.inputs();
        for(float lens:new float[]{0,.1f,1,8}) {
            p.lensing=lens;p.frame(1);float[] data=image(p.out,0);for(float v:data)check(Float.isFinite(v),"finite lensing limit "+lens);
            if(lens==0){float[] z=image(p.solution,3);for(int i=2;i<z.length;i+=4)check(z[i]==0,"zero mass has no horizon");}
        }
        p.lensing=1;
        p.resize(320,180);p.glowStrength=8;
        for(float distance:new float[]{0,1,1.2318f,2,5}) {
            p.distance=distance;p.frame(1);float[] data=image(p.out,0);
            for(float v:data)check(Float.isFinite(v),"finite horizon/observer limit "+distance);
        }
        p.distance=35;p.glowStrength=.7f;
        for(var q:new Pipeline[]{p,old}) {q.quality(96);q.resize(320,180);q.constantDepth=true;q.split=false;q.glowStrength=.7f;q.inputs();}
        p.finaleTicks=-1;p.frame(1);float[] ordinaryHigh=image(p.out,0);
        p.finaleTicks=0;p.frame(1);check(maxDifference(ordinaryHigh,image(p.out,0))<.0001,"show begins with identical high black hole");
        for(double time:new double[]{0,2,4,4.4,4.45,4.5,4.55,8,41,100,119}) {
            p.finaleTicks=old.finaleTicks=time*20;p.frame(1);old.frame(1);
            double difference=maxDifference(image(p.out,0),image(old.out,0));
            if(time<=4.4 || time==119)check(difference>.01,"high active during black-hole show phase "+time);
            else if(time>=4.55 && time<119) {
                check(maxDifference(image(p.solution,0),image(old.solution,0))<.001,"revealed singularity source preserved "+time);
                check(maxDifference(image(p.solution,1),image(old.solution,1))<.001,"revealed singularity back source preserved "+time);
            }
            if(time==0 || time==4 || time==4.45 || time==119)png(p.out,"high-show-"+time);
        }
        p.finaleTicks=-1;p.high=true;p.scale=1;p.resize(640,360);p.constantDepth=false;p.inputs();
        RenderStabilityGpuVerification.resourceChecks(p);
        System.out.printf(Locale.ROOT,"PASS low/balanced, beaming ratios %.3f/%.3f, capture/front light, split depth, limits, finale, GL lifecycle%n",a,b);
    }
    private static double timed(Pipeline p) {int query=glGenQueries();glBeginQuery(GL_TIME_ELAPSED,query);for(int i=0;i<8;i++)p.frame(1);glEndQuery(GL_TIME_ELAPSED);double ms=glGetQueryObjectui64(query,GL_QUERY_RESULT)/8e6;glDeleteQueries(query);return ms;}
    private static void performance(Pipeline p,Pipeline old) {
        for(int quality:new int[]{24,48}) {
            for(var q:new Pipeline[]{p,old}) {q.quality(quality);q.finaleTicks=-1;q.constantDepth=false;q.dark=false;q.glowStrength=.7f;q.distance=22;q.shape=0;q.split=false;q.resize(1920,1080);}
            for(int i=0;i<20;i++){old.frame(1);p.frame(1);}
            double[] a=new double[5],b=new double[5];for(int i=0;i<5;i++){a[i]=timed(old);b[i]=timed(p);}Arrays.sort(a);Arrays.sort(b);
            System.out.printf(Locale.ROOT,"BENCH preserved quality=%d 1080p old=%.3f new=%.3f ms%n",quality,a[2],b[2]);
        }
        for(var q:new Pipeline[]{p,old}) {q.quality(96);q.finaleTicks=-1;q.constantDepth=false;q.dark=false;q.glowStrength=.7f;q.resize(1920,1080);}
        for(int scenario=0;scenario<3;scenario++) {
            for(var q:new Pipeline[]{p,old}) {q.distance=scenario==0?120:22;q.shape=0;q.split=scenario==2;q.inputs();}
            for(int i=0;i<20;i++){old.frame(1);p.frame(1);}
            double[] a=new double[5],b=new double[5];for(int i=0;i<5;i++){a[i]=timed(old);b[i]=timed(p);}Arrays.sort(a);Arrays.sort(b);
            System.out.printf(Locale.ROOT,"BENCH high 1080p scenario=%d old=%.3f new=%.3f ms (synthetic subject, not game FPS)%n",scenario,a[2],b[2]);
        }
    }
    public static void main(String[] args)throws Exception {
        source=Path.of(args[0]);output=Path.of(args[2]);Files.createDirectories(output);
        check(glfwInit(),"GLFW");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"high geodesic verification",0,0);check(window!=0,"context");glfwMakeContextCurrent(window);GL.createCapabilities();vao=glGenVertexArrays();RenderStabilityGpuVerification.vao=vao;
        System.out.println("GPU: "+glGetString(GL_RENDERER));
        try(var p=new Pipeline(source);var old=new Pipeline(Path.of(args[1]))) {
            analyticProbes(p);kerrProbes(p);pipelines(p,old);if(args.length<4 || !args[3].equals("checks"))performance(p,old);
            check(glGetError()==GL_NO_ERROR,"no GL errors");
        } finally {glUseProgram(0);glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();}
        System.out.println("PASS "+checks+" checks. Independent GPU validation only; host integration requires game acceptance.");
    }
}
