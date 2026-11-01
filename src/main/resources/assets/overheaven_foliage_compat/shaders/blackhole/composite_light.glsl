uniform int BloomBounded;
uniform ivec4 BloomBounds;
vec3 bloomAt(vec2 uv,float limit) {
    if(Glow<=0.0) return vec3(0.0);
    vec2 size=vec2(textureSize(Bloom,0)), coordinate=uv*size-0.5;
    ivec2 base=ivec2(floor(coordinate)); vec2 fraction=fract(coordinate);
    vec3 light=vec3(0.0);
    for(int offsetY=0;offsetY<2;offsetY++) for(int offsetX=0;offsetX<2;offsetX++) {
        ivec2 tap=clamp(base+ivec2(offsetX,offsetY),ivec2(0),ivec2(size)-1);
#ifndef UNBOUNDED_TARGET
        if(BloomBounded!=0 && (any(lessThan(tap,BloomBounds.xy)) || any(greaterThanEqual(tap,BloomBounds.zw)))) continue;
#endif
        vec4 sampleValue=texelFetch(Bloom,tap,0);
        float weight=(offsetX==0?1.0-fraction.x:fraction.x)*(offsetY==0?1.0-fraction.y:fraction.y);
        if(sampleValue.a<limit && sampleValue.a<65500.0) light+=sampleValue.rgb*weight;
    }
    return light*Glow*mix(1.0,2.2,clamp(FinaleAmount,0.0,1.0));
}
