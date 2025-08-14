#include "CenteredSpiro.hpp"

#include <Audio/AudioConstants.hpp>

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {

void CenteredSpiro::GenerateVertices(const PresetState&, const PerFrameContext&)
{
    // Alpha calculation is handled in MaximizeColors().
    // Android TV: Limit vertex count for performance
    m_samples = std::min(Audio::WaveformSamples, 128);

    m_wave1Vertices.resize(m_samples);

    // Android TV: Safe array access with bounds checking
    for (int i = 0; i < m_samples; i++)
    {
        int rIndex = std::min(i, Audio::WaveformSamples - 1);
        int lIndex = std::min(i + 32, Audio::WaveformSamples - 1);
        
        m_wave1Vertices[i].x = m_pcmDataR[rIndex] * m_aspectY + m_waveX;
        m_wave1Vertices[i].y = m_pcmDataL[lIndex] * m_aspectX + m_waveY;
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
