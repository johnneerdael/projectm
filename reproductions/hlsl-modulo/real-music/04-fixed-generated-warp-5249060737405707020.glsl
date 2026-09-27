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
mat2 mat2_from_float_int_int_float(float a, int b, int c, float d) { return mat2(a,c,b,d); }
in vec4 frag_COLOR;
in vec4 frag_TEXCOORD0;
in vec2 frag_TEXCOORD1;
out vec4 rast_FragData[2];
uniform sampler3D sampler_noisevol_hq;
uniform sampler2D sampler_main;
uniform sampler2D sampler_blur3;
uniform sampler2D sampler_blur2;
uniform sampler2D sampler_blur1;
uniform vec4 texsize_noisevol_hq;
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
vec3 dx, dy, ret1;
vec2 zz, uv1, uv2;
float n1, n2, n3, n4, zv, noise, k1;
vec2 sunpos;
vec3 suncol;
mat2 ofs;
vec3 cloud(vec2 uv_in) {
    return mult0(vec3 (clamp(((+float(0.0005)) / abs((length((uv_in - sunpos)) - ((_qc).y / (_qf).y)))), 0.0, 1.0)),suncol);
}
void PS(vec4 _vDiffuse, vec4 _uv, vec2 _rad_ang, out vec4 _return_value, out vec4 _mv_tex_coords) {
    vec3 ret = vec3( 0 );
    ((_mv_tex_coords).xy = (_uv).xy);
    (uv1 = ((_uv).xy - vec2 (float(0.5))));
    (dx = vec3 ((texture(sampler_main, ((_uv).xy + matrix_row0(ofs,0))) - texture(sampler_main, ((_uv).xy - matrix_row0(ofs,0))))));
    (dy = vec3 ((texture(sampler_main, ((_uv).xy + matrix_row0(ofs,1))) - texture(sampler_main, ((_uv).xy - matrix_row0(ofs,1))))));
    (zz = vec2((dx).b, (dy).b));
    (zv = mult0((_c2).x,float(0.1)));
    (uv2 = (mult0(vec2 ((_qc).z),uv1) + vec2 (mult0(float(0.04),(_c2).x))));
    (n1 = (texture(sampler_noisevol_hq, vec3((uv2).x, (uv2).y, zv))).b);
    (n2 = (texture(sampler_noisevol_hq, vec3(mult0((uv2).x,float (2)), mult0((uv2).y,float (2)), mult0(zv,float (2))))).b);
    (n3 = (texture(sampler_noisevol_hq, vec3(mult0((uv2).x,float (4)), mult0((uv2).y,float (4)), mult0(zv,float (3))))).b);
    (n4 = (texture(sampler_noisevol_hq, vec3(mult0((uv2).x,float (8)), mult0((uv2).y,float (8)), mult0(zv,float (7))))).b);
    (noise = ((((n1 + (n2 / float (2))) + (n3 / float (4))) + (n4 / float (8))) - float(0.5)));
    (noise = pow(abs(noise),(_qd).x));
    (uv2 = (mult0(uv1,vec2 ((float (1) + mult0(float(0.5),(_qa).y)))) + vec2 (mult0(noise,float(0.1)))));
    float blur = float( ((mult0((texture(sampler_blur3, ((uv1 / vec2 (4)) + vec2 (float(0.5))))).xyz,vec3 ((_c6).x)) + vec3 ((_c6).y)) + vec3 (float(0.2))) );
    (ret1 = cloud(uv2));
    (ret1 = (((((ret1 / vec3 (blur)) + vec3 (mult0(texture(sampler_main, ((uv1 + vec2 (float(0.5))) + mult0(vec2 ((_qb).y),zz))),vec4 (float(0.98))))) - vec3 (float(0.01))) - vec3 (mult0(blur,float(0.1)))) - vec3 (mult0(float(0),length(zz)))));
    float go = float( (_qc).w );
    (ret = (mult0(vec3 (go),ret1) + mult0(vec3 ((float (1) - go)),(texture(sampler_main, (_uv).xy)).xyz)));
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
    sunpos = vec2( mult0(vec2((_qd).z, (_qd).w),vec2 ((_qe).z)) );
    suncol = vec3( (vec4 (float(0.5)) + normalize(_c8)) );
    ofs = mat2( mult0(mat2_from_float_int_int_float((_c7).z, 0, 0, (_c7).w),mat2 (2)) );
    PS(_vDiffuse, _uv, _rad_ang, _return_value, _mv_tex_coords);
    rast_FragData[0] = _return_value;
    rast_FragData[1] = _mv_tex_coords;
}
