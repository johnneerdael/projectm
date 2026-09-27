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
in vec4 frag_COLOR;
in vec4 frag_TEXCOORD0;
in vec2 frag_TEXCOORD1;
out vec4 rast_FragData[2];
uniform sampler2D sampler_pw_noise_lq;
uniform sampler2D sampler_noise_lq;
uniform sampler2D sampler_main;
uniform sampler2D sampler_blur1;
uniform vec4 texsize_noise_lq;
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
float streetx, streety, streetr, street;
vec2 uv0, uv1, uv2, uv3;
void PS(vec4 _vDiffuse, vec4 _uv, vec2 _rad_ang, out vec4 _return_value, out vec4 _mv_tex_coords) {
    vec3 ret = vec3( 0 );
    ((_mv_tex_coords).xy = (_uv).xy);
    (uv1 = ((_uv).xy - vec2 (float(0.5))));
    (uv2 = (sin(mult0(mult0((_uv).xy,vec2 (float(3.1416))),vec2 (2))) / vec2 (2)));
    float p1 = float( 100 );
    float p2 = float( 2 );
    (streetx = (float (1) - min(clamp((mult0(p1,abs((uv2).x)) - p2), 0.0, 1.0), clamp((mult0(mult0(float (2),p1),abs((abs((uv1).x) - float(0.25)))) - p2), 0.0, 1.0))));
    (streety = (float (1) - min(clamp((mult0(p1,abs((uv2).y)) - p2), 0.0, 1.0), clamp((mult0(mult0(float (2),p1),abs((abs((uv1).y) - float(0.25)))) - p2), 0.0, 1.0))));
    (streetr = (float (1) - clamp((mult0(p1,abs((mult0(((_rad_ang).x / (_c0).y),float(1.07)) - float(0.5)))) - mult0(p2,float(1.75))), 0.0, 1.0)));
    (street = max(streetx, streety));
    (street = max(street, streetr));
    vec3 orig = vec3( float(0.08) );
    (uv2 = mult0((((_uv).xy - vec2 (float(0.3)))).yx,vec2 (2)));
    vec3 copy = vec3( mix((texture(sampler_main, uv2)).xyz, (mult0((texture(sampler_blur1, uv2)).xyz,vec3 ((_c5).x)) + vec3 ((_c5).y)), vec3 (0)) );
    ((ret).r = float (mix(orig, copy, vec3 (float(0.5)))));
    (uv3 = vec2(mult0(((_rad_ang).x / (_c0).y),float(1.07)), mult0(((_rad_ang).y / float (4)),float (2))));
    ((uv3).y += mult0(mult0(float (((int(mult0((uv3).x,float (64))) % 8) - 4)),(-float(0.024))),(_c2).x));
    ((ret).b = mult0(float (mult0(int ((mult0(float (1),(texture(sampler_pw_noise_lq, uv3)).r) > float(0.95))),int ((streetr > float(0.99))))),float(0.8)));
    (uv3 = mult0(mult0(mult0((_uv).zw,(_c7).xy),(texsize_noise_lq).zw),vec2(float(0.5), 1)));
    ((uv3).x += mult0(mult0(float (((int(mult0(((_uv).zw).y,(texsize_noise_lq).y)) % 8) - 4)),(_c2).x),float(0.015)));
    ((ret).b += float (mult0(int ((mult0(float (1),(texture(sampler_pw_noise_lq, uv3)).r) > float(0.94))),int ((streety > float(0.99))))));
    (uv3 = mult0(mult0(mult0((_uv).zw,(_c7).xy),(texsize_noise_lq).zw),vec2(1, float(0.5))));
    ((uv3).y += mult0(mult0(float (((int(mult0(((_uv).zw).x,(texsize_noise_lq).x)) % 8) - 4)),(_c2).x),float(0.023)));
    ((ret).b += float (mult0(int ((mult0(float (1),(texture(sampler_pw_noise_lq, uv3)).r) > float(0.94))),int ((streetx > float(0.99))))));
    ((ret).b += mult0(float (mult0(int ((fract((mult0(float (32),(ret).r) + mult0((_c2).x,float(0.002)))) > float(0.95))),int ((!bool (street))))),float(0.7)));
    ((ret).b += mult0(street,float(0.15)));
    ((ret).b = max(mult0((ret).b,float (1)), mult0(mult0(((texture(sampler_main, (_uv).xy)).xyz).b,float(0.75)),float ((((texture(sampler_main, (_uv).xy)).xyz).b > float (0))))));
    ((ret).r = mult0((ret).r,(float (1) - street)));
    (_return_value = vec4((ret).xyz, float(1)));
}
void main() {
    vec4 _vDiffuse;
    _vDiffuse = frag_COLOR;
    vec4 _uv;
    _uv = frag_TEXCOORD0;
    vec2 _rad_ang;
    _rad_ang = frag_TEXCOORD1;
    vec4 _return_value;
    vec4 _mv_tex_coords;
    PS(_vDiffuse, _uv, _rad_ang, _return_value, _mv_tex_coords);
    rast_FragData[0] = _return_value;
    rast_FragData[1] = _mv_tex_coords;
}
