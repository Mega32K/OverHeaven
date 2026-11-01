// Planck B_lambda integrated over 16 visible wavelengths, 390..765 nm.
// CIE 1931 analytic response, Wyman/Sloan/Shirley (JCGT 2013), equation 2:
// https://jcgt.org/published/0002/02/01/paper.pdf . Coefficients below include
// lambda^-5 and one constant normalization (Y=1 at 12000 K), NOT per-color
// normalization. Frequency shift is applied to temperature before integration.
const vec4 THERMAL_BANDS[16]=vec4[16](
    vec4(0.0543385982,0.000145200958,0.389924023,36891.7148),
    vec4(0.746311708,0.0038173078,4.10896234,34669.3223),
    vec4(1.9971178,0.0442656891,9.71713187,32699.4745),
    vec4(1.04112833,0.256363617,6.54388649,30941.4382),
    vec4(0.12832169,0.820545786,1.52253182,29362.7934),
    vec4(0.150949208,1.57765529,0.143393772,27937.4151),
    vec4(0.546373777,1.95280801,0.00623191914,26644.0162),
    vec4(1.15453275,1.64903783,0.000139401465,25465.0775),
    vec4(1.40360516,0.997633048,1.7590875e-06,24386.0488),
    vec4(0.980949043,0.450735235,1.35311985e-08,23394.746),
    vec4(0.393821712,0.15757824,6.77682696e-11,22480.8887),
    vec4(0.0907672729,0.0439477765,2.33785076e-13,21635.7425),
    vec4(0.0120030202,0.0100387078,5.83069156e-16,20851.8388),
    vec4(0.000910258506,0.00192149364,1.0961521e-18,20122.7535),
    vec4(3.95690669e-05,0.000314386555,1.61059228e-21,19442.9308),
    vec4(9.85569316e-07,4.47419271e-05,1.90870248e-24,18807.5409));
vec3 thermalRadiance(float temperature) {
    if(temperature<100.) return vec3(0.);
    vec3 xyz=vec3(0.);
    for(int i=0;i<16;i++) {
        vec4 band=THERMAL_BANDS[i];
        xyz+=band.xyz/(exp(min(80.,band.w/temperature))-1.);
    }
    return max(vec3(0.),vec3(dot(xyz,vec3(3.2406,-1.5372,-.4986)),
        dot(xyz,vec3(-.9689,1.8758,.0415)),dot(xyz,vec3(.0557,-.2040,1.0570))));
}
