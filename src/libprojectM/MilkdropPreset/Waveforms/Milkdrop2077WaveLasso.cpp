#include "Waveforms/Milkdrop2077WaveLasso.hpp"

#include "PerFrameContext.hpp"

#include <cmath>

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {

void Milkdrop2077WaveLasso::GenerateVertices(const PresetState& presetState,
                                             const PerFrameContext&)
{
    // Android TV: Reduced vertex count for complex lasso calculations
    m_samples = std::min(Audio::WaveformSamples / 2, 32);

    m_wave1Vertices.resize(m_samples);

    for (int sample = 0; sample < m_samples; sample++)
    {
        // Android TV: Safe array access with bounds checking
        int dataIndex = std::min(sample + 32, Audio::WaveformSamples - 1);
        float clampedPcmData = std::max(-1.0f, std::min(1.0f, m_pcmDataL[dataIndex]));
        float clampedTime = std::fmod(presetState.renderContext.time, 60.0f); // Prevent time overflow
        
        float const angle = clampedPcmData * 1.57f + clampedTime * 2.0f;

        // Android TV: Simplified lasso calculation to prevent extreme coordinate values
        float cosTime = cosf(clampedTime);
        float sinTime = sinf(clampedTime);
        float angle2 = angle * 2.0f;
        float angle3Pi = angle * 3.14f;
        
        // Clamp intermediate calculations
        float tanValue = std::max(-10.0f, std::min(10.0f, tanf(clampedTime / std::max(0.1f, std::abs(angle)))));
        
        m_wave1Vertices[sample].x = std::max(-2.0f, std::min(2.0f, cosTime / 2.0f + cosf(angle2 + tanValue)));
        m_wave1Vertices[sample].y = std::max(-2.0f, std::min(2.0f, sinTime * 2.0f * sinf(angle3Pi) * m_aspectX / 2.8f + m_waveY));
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
