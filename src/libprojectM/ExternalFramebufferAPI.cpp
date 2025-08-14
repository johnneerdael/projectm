#include "ProjectM.hpp"
#include <GLES2/gl2.h>

namespace libprojectM {
extern bool g_respect_external_framebuffer;
}

extern "C" PROJECTM_EXPORT void projectm_set_respect_external_framebuffer(int enable) {
    libprojectM::g_respect_external_framebuffer = (enable != 0);
}
