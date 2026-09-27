#version 330
vec2 matrix_row0(mat2 m, int i) { return vec2( m[0][i], m[1][i] ); }
vec2 matrix_row0(mat2 m, float i_float) { int i=int(i_float); return vec2( m[0][i], m[1][i] ); }
vec2 matrix_row0(mat2x3 m, int i) { return vec2( m[0][i], m[1][i]); }
vec2 matrix_row0(mat2x3 m, float i_float) { int i=int(i_float); return vec2( m[0][i], m[1][i]); }
vec2 matrix_row0(mat2x4 m, int i) { return vec2( m[0][i], m[1][i]); }
vec2 matrix_row0(mat2x4 m, float i_float) { int i=int(i_float); return vec2( m[0][i], m[1][i]); }
vec3 matrix_row0(mat3 m, int i) { return vec3( m[0][i], m[1][i], m[2][i] ); }
vec3 matrix_row0(mat3 m, float i_float) { int i=int(i_float); return vec3( m[0][i], m[1][i], m[2][i] ); }
vec3 matrix_row0(mat3x2 m, int i) { return vec3( m[0][i], m[1][i], m[2][i] ); }
vec3 matrix_row0(mat3x2 m, float i_float) { int i=int(i_float); return vec3( m[0][i], m[1][i], m[2][i] ); }
vec3 matrix_row0(mat3x4 m, int i) { return vec3( m[0][i], m[1][i], m[2][i] ); }
vec3 matrix_row0(mat3x4 m, float i_float) { int i=int(i_float); return vec3( m[0][i], m[1][i], m[2][i] ); }
vec4 matrix_row0(mat4 m, int i) { return vec4( m[0][i], m[1][i], m[2][i], m[3][i] ); }
vec4 matrix_row0(mat4 m, float i_float) { int i=int(i_float); return vec4( m[0][i], m[1][i], m[2][i], m[3][i] ); }
vec4 matrix_row0(mat4x3 m, int i) { return vec4( m[0][i], m[1][i], m[2][i], m[3][i] ); }
vec4 matrix_row0(mat4x3 m, float i_float) { int i=int(i_float); return vec4( m[0][i], m[1][i], m[2][i], m[3][i] ); }
vec4 matrix_row0(mat4x2 m, int i) { return vec4( m[0][i], m[1][i], m[2][i], m[3][i] ); }
vec4 matrix_row0(mat4x2 m, float i_float) { int i=int(i_float); return vec4( m[0][i], m[1][i], m[2][i], m[3][i] ); }
float mult0(int i_x, int i_y) { float x=float(i_x); float y=float(i_y); if (x == 0.0 || y == 0.0) { return 0.0; } else { return (x * y); } }
float mult0(int i_x, float y) { float x=float(i_x); if (x == 0.0 || y == 0.0) { return 0.0; } else { return (x * y); } }
float mult0(float x, int i_y) { float y=float(i_y); if (x == 0.0 || y == 0.0) { return 0.0; } else { return (x * y); } }
float mult0(float x, float y) { if (x == 0.0 || y == 0.0) { return 0.0; } else { return (x * y); } }
vec2 mult0(vec2 x, vec2 y) { return vec2(mult0(x.x, y.x), mult0(x.y, y.y)); }
vec3 mult0(vec3 x, vec3 y) { return vec3(mult0(x.x, y.x), mult0(x.y, y.y), mult0(x.z, y.z)); }
vec4 mult0(vec4 x, vec4 y) { return vec4(mult0(x.x, y.x), mult0(x.y, y.y), mult0(x.z, y.z), mult0(x.w, y.w)); }
mat2 mult0(mat2 x, mat2 y) { return x * y; }
mat3 mult0(mat3 x, mat3 y) { return x * y; }
mat4 mult0(mat4 x, mat4 y) { return x * y; }
vec2  m_scalar_swizzle20(float x) { return  vec2(x, x); }
ivec2 m_scalar_swizzle20(int   x) { return ivec2(x, x); }
vec3  m_scalar_swizzle30(float x) { return  vec3(x, x, x); }
ivec3 m_scalar_swizzle30(int   x) { return ivec3(x, x, x); }
vec4  m_scalar_swizzle40(float x) { return  vec4(x, x, x, x); }
ivec4 m_scalar_swizzle40(int   x) { return ivec4(x, x, x, x); }
uvec2 m_scalar_swizzle20(uint  x) { return uvec2(x, x); }
uvec3 m_scalar_swizzle30(uint  x) { return uvec3(x, x, x); }
uvec4 m_scalar_swizzle40(uint  x) { return uvec4(x, x, x, x); }
vec2 bvecTernary0(bvec2 cond, vec2 trueExpr, vec2 falseExpr) { vec2 ret; ret.x = cond.x ? trueExpr.x : falseExpr.x; ret.y = cond.y ? trueExpr.y : falseExpr.y; return ret; }
vec3 bvecTernary0(bvec3 cond, vec3 trueExpr, vec3 falseExpr) { vec3 ret; ret.x = cond.x ? trueExpr.x : falseExpr.x; ret.y = cond.y ? trueExpr.y : falseExpr.y; ret.z = cond.z ? trueExpr.z : falseExpr.z; return ret; }
vec4 bvecTernary0(bvec4 cond, vec4 trueExpr, vec4 falseExpr) { vec4 ret; ret.x = cond.x ? trueExpr.x : falseExpr.x; ret.y = cond.y ? trueExpr.y : falseExpr.y; ret.z = cond.z ? trueExpr.z : falseExpr.z; ret.w = cond.w ? trueExpr.w : falseExpr.w; return ret; }
mat3x2 mat3x2_from_vec3_vec3(vec3 a, vec3 b) { return mat3x2(a.x,b.x,a.y,b.y,a.z,b.z); }
in vec4 frag_COLOR;
in vec2 frag_TEXCOORD0;
in vec2 frag_TEXCOORD1;
out vec4 rast_FragData[1];
uniform sampler2D sampler_pw_noise_lq;
uniform sampler2D sampler_noise_lq;
uniform sampler2D sampler_noise_hq;
uniform sampler2D sampler_main;
uniform sampler2D sampler_blur1;
uniform vec4 texsize_noise_lq;
uniform vec4 texsize_noise_hq;
uniform vec4 texsize_main;
uniform vec4 rand_frame;
uniform vec4 rand_preset;
uniform vec4 _c0;
uniform vec4 _c1, _c2, _c3, _c4;
uniform vec4 _c5;
uniform vec4 _c6;
uniform vec4 _c7;
uniform vec4 _c8;
uniform vec4 _c9;
uniform vec4 _c10;
uniform vec4 _c11;
uniform vec4 _c12;
uniform vec4 _c13;
uniform vec4 _qa;
uniform vec4 _qb;
uniform vec4 _qc;
uniform vec4 _qd;
uniform vec4 _qe;
uniform vec4 _qf;
uniform vec4 _qg;
uniform vec4 _qh;
uniform mat3x4 rot_s1;
uniform mat3x4 rot_s2;
uniform mat3x4 rot_s3;
uniform mat3x4 rot_s4;
uniform mat3x4 rot_d1;
uniform mat3x4 rot_d2;
uniform mat3x4 rot_d3;
uniform mat3x4 rot_d4;
uniform mat3x4 rot_f1;
uniform mat3x4 rot_f2;
uniform mat3x4 rot_f3;
uniform mat3x4 rot_f4;
uniform mat3x4 rot_vf1;
uniform mat3x4 rot_vf2;
uniform mat3x4 rot_vf3;
uniform mat3x4 rot_vf4;
uniform mat3x4 rot_uf1;
uniform mat3x4 rot_uf2;
uniform mat3x4 rot_uf3;
uniform mat3x4 rot_uf4;
uniform mat3x4 rot_rand1;
uniform mat3x4 rot_rand2;
uniform mat3x4 rot_rand3;
uniform mat3x4 rot_rand4;
float quality, depth, z0, scale;
vec3 neu, rsl, rsl0, rsl00, screen3;
vec2 uv0, uv1, uv2, uv4, uv5, rsk, dz1;
float lprof, noise, cloud, gmask, dx, dy, test, tmp;
float lav_gnd;
vec3 mov;
vec3 t;
vec3 s;
vec3 ts;
vec3 pos;
vec3 project(float gnd) {
    float z;
    (z = (float ((-1)) / dot((cross(ts, (-t))),(screen3))));
    (z = (z - gnd));
    (gmask = float (mult0(int ((z >= float ((-6)))),int ((z <= float (6))))));
    return vec3(mult0(mult0(mult0(((mat3x2_from_vec3_vec3(ts, t))*(cross(pos, screen3))),vec2 (z)),vec2 (gmask)),(_c0).yx), (-z));
}
vec3 GetBlur0(vec2 uvi) {
    vec3 tmp;
    (tmp = (((((texture(sampler_main, uvi)).xyz + (texture(sampler_main, (uvi + mult0((_c7).zw,vec2(1, 0))))).xyz) + (texture(sampler_main, (uvi + mult0((_c7).zw,vec2((-1), 0))))).xyz) + (texture(sampler_main, (uvi + mult0((_c7).zw,vec2(0, 1))))).xyz) + (texture(sampler_main, (uvi + mult0((_c7).zw,vec2(0, (-1)))))).xyz));
    return mix(mult0(tmp,vec3 (float(0.2))), (mult0((texture(sampler_blur1, uvi)).xyz,vec3 ((_c5).x)) + vec3 ((_c5).y)), vec3 (1));
}
void PS(vec4 _vDiffuse, vec2 _uv, vec2 _rad_ang, out vec4 _return_value) {
    vec3 ret = vec3( 0 );
    (screen3 = vec3(mult0(((_uv).xy - vec2(float(0.5), float(0.5))),(_c0).xy), 1));
    (lav_gnd = (-float(0.022)));
    for (int m = int( 1 ); (float (m) <= quality); (m++)) {
        (rsl = (project(lav_gnd) + vec3(mov)));
        (lprof = mult0(floor((mult0(z0,float (1)) - mult0(mult0(float (2),z0),((texture(sampler_main, (rsl).xy)).xyz).r))),scale));
        (lav_gnd += lprof);
    }
    (rsl = (project(lav_gnd) + mov));
    float streetlevel = float( clamp((mult0(float ((-10)),depth) - mult0(float (12),lav_gnd)), 0.0, 1.0) );
    float cmask = float( (streetlevel > float(0.05)) );
    vec3 backnoise = vec3( mix(texture(sampler_noise_hq, ((rsl).xy / vec2 (6))), vec4 (float(0.5)), vec4 (float(0.2))) );
    vec3 lights = vec3( mult0(vec3 ((float(0.1) / length(cos((mult0((rsl).xy,vec2 (26)) + mult0((rand_preset).xy,vec2 (float(1.1)))))))),backnoise) );
    float d = float( float(0.0008) );
    (dy = float (((texture(sampler_main, ((rsl).xy + vec2 (d)))).xyz - (texture(sampler_main, ((rsl).xy - vec2 (d)))).xyz)));
    (dy += float ((mult0(((texture(sampler_main, vec2 ((mult0(rsl,vec3 (3)) + vec3 (d))))).xyz - (texture(sampler_main, vec2 ((mult0(rsl,vec3 (3)) - vec3 (d))))).xyz),vec3 (clamp(((lav_gnd / depth) + float (1)), 0.0, 1.0))) / vec3 (2))));
    (rsl00 = project((-depth)));
    (rsl0 = (rsl00 + mov));
    (ret = mult0(mult0(mult0(vec3 (clamp(dy, 0.0, 1.0)),lights),vec3 ((float(0.2) + (texture(sampler_noise_hq, mult0((rsl0).xy,vec2 (float(0.06))))).r))),vec3 (14)));
    (ret += mult0(mult0(lights,vec3 (clamp(mult0(mult0(float ((-6)),lav_gnd),((rand_preset).x + float(0.2))), 0.0, 1.0))),vec3 (3)));
    (ret *= vec3 ((float (1) + mult0(mult0(float (12),(GetBlur0(fract((rsl0).xy))).b),clamp(mult0(streetlevel,float (2)), 0.0, 1.0)))));
    vec3 car = vec3( ((texture(sampler_main, fract((rsl0).xy))).xyz).b );
    if ((((texture(sampler_main, (rsl0).xy)).xyz).r > float(0.1))) {
        (car *= vec3 (mult0(texture(sampler_noise_lq, (rsl0).xy),vec4 (2))));
    }
    (ret += mult0(car,vec3 (cmask)));
    (ret = mix(ret, vec3(float(0.2), float(0.1), 0), vec3 (clamp(pow(abs(((rsl0).z / float (3))),float (2)), 0.0, 1.0))));
    float flash = float( (float(0.02) / length(sin(((mult0(vec2 (6),(rsl0).xy) + vec2 (mult0(float (2),dy))) + mult0(vec2 (3),(rand_frame).zx))))) );
    (ret += mult0(mult0(vec3 (mult0(mult0(flash,clamp((float(0.2) - mult0(float (4),lav_gnd)), 0.0, 1.0)),(_qa).w)),vec3(float(0.2), float(0.5), 1)),vec3 ((float (1) - ((_uv).xy).y))));
    (_return_value = vec4((ret).xyz, float(1)));
}
void main() {
    vec4 _vDiffuse;
    _vDiffuse = frag_COLOR;
    vec2 _uv;
    _uv = frag_TEXCOORD0;
    vec2 _rad_ang;
    _rad_ang = frag_TEXCOORD1;
    vec4 _return_value;
    quality = float( 5 );
    depth = float( (_qh).y );
    z0 = float( 4 );
    scale = float( (mult0((float(1) / quality),(-depth)) / z0) );
    lav_gnd = float( 0 );
    mov = vec3( vec3((_qb).x, (_qb).y, (_qb).z) );
    t = vec3( vec3((_qd).y, (_qd).z, (_qd).w) );
    s = vec3( vec3((_qe).x, (_qe).y, (_qe).z) );
    ts = vec3( (-cross(s, t)) );
    pos = vec3( vec3((_qc).z, (_qc).w, (_qd).x) );
    PS(_vDiffuse, _uv, _rad_ang, _return_value);
    rast_FragData[0] = _return_value;
}
