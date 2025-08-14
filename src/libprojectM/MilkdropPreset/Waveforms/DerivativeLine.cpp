#include "DerivativeLine.hpp"

#include "PresetState.hpp"

#include <cassert>

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {

bool DerivativeLine::UsesNormalizedMysteryParam()
{
    return true;
}

void DerivativeLine::GenerateVertices(const PresetState& presetState, const PerFrameContext&)
{
    // Android TV: Conservative vertex limit
    m_samples = std::min(Audio::WaveformSamples, 128);

    // Android TV: Further reduce for low-res displays
    if (m_samples > presetState.renderContext.viewportSizeX / 3)
    {
        m_samples = std::max(32, presetState.renderContext.viewportSizeX / 6); // More conservative
    }

    m_wave1Vertices.resize(m_samples);

    int const sampleOffset = std::max(0, (Audio::WaveformSamples - m_samples) / 2);

    // Android TV: Clamp mystery param to prevent extreme values
    float clampedMysteryParam = std::max(-1.0f, std::min(1.0f, m_mysteryWaveParam));
    const float w1 = 0.45f + 0.5f * (clampedMysteryParam * 0.5f + 0.5f);
    const float w2 = 1.0f - w1;

    const float inverseSamples = 1.0f / static_cast<float>(std::max(1, m_samples));

    for (int i = 0; i < m_samples; i++)
    {
        // Android TV: Safe array access with bounds checking
        int leftIndex = std::min(i + sampleOffset, Audio::WaveformSamples - 1);
        int rightIndex = std::min(i + 25 + sampleOffset, Audio::WaveformSamples - 1);
        
        m_wave1Vertices[i].x = -1.0f + 2.0f * (static_cast<float>(i) * inverseSamples) + m_waveX;
        m_wave1Vertices[i].y = std::max(-1.0f, std::min(1.0f, m_pcmDataL[leftIndex])) * 0.47f + m_waveY;
        m_wave1Vertices[i].x += std::max(-1.0f, std::min(1.0f, m_pcmDataR[rightIndex])) * 0.44f;

        // Momentum - Android TV: More conservative smoothing
        if (i > 1)
        {
            float clampedW1 = std::max(0.0f, std::min(1.0f, w1));
            float clampedW2 = std::max(0.0f, std::min(1.0f, w2));
            
            m_wave1Vertices[i].x = m_wave1Vertices[i].x * clampedW2 + clampedW1 * (m_wave1Vertices[i - 1].x * 2.0f - m_wave1Vertices[i - 2].x);
            m_wave1Vertices[i].y = m_wave1Vertices[i].y * clampedW2 + clampedW1 * (m_wave1Vertices[i - 1].y * 2.0f - m_wave1Vertices[i - 2].y);
            
            // Android TV: Clamp final coordinates
            m_wave1Vertices[i].x = std::max(-2.0f, std::min(2.0f, m_wave1Vertices[i].x));
            m_wave1Vertices[i].y = std::max(-2.0f, std::min(2.0f, m_wave1Vertices[i].y));
        }
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
