#include "Waveforms/Circle.hpp"

#include "PerFrameContext.hpp"

#include <cmath>

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {

auto Circle::IsLoop() -> bool
{
    return true;
}

auto Circle::UsesNormalizedMysteryParam() -> bool
{
    return true;
}

void Circle::GenerateVertices(const PresetState& presetState,
                              const PerFrameContext&)
{
    // Android TV: Conservative vertex limit for circular waveforms
    m_samples = std::min(Audio::WaveformSamples / 2, 64);

    m_wave1Vertices.resize(m_samples);

    const int sampleOffset{std::max(0, (Audio::WaveformSamples - m_samples) / 2)};

    const float inverseSamplesMinusOne{1.0f / static_cast<float>(std::max(1, m_samples))};

    for (int i = 0; i < m_samples; i++)
    {
        // Android TV: Clamp radius to prevent extreme values
        float radius = 0.5f + 0.4f * std::max(-1.0f, std::min(1.0f, m_pcmDataR[std::min(i + sampleOffset, Audio::WaveformSamples - 1)])) + std::max(-0.5f, std::min(0.5f, m_mysteryWaveParam));
        float const angle = static_cast<float>(i) * inverseSamplesMinusOne * 6.28f + presetState.renderContext.time * 0.2f;
        
        if (i < m_samples / 10)
        {
            float mix = static_cast<float>(i) / (static_cast<float>(m_samples) * 0.1f);
            mix = 0.5f - 0.5f * cosf(mix * 3.1416f);
            // Android TV: Safe array access
            int radius2Index = std::min(i + m_samples + sampleOffset, Audio::WaveformSamples - 1);
            float const radius2 = 0.5f + 0.4f * std::max(-1.0f, std::min(1.0f, m_pcmDataR[radius2Index])) + std::max(-0.5f, std::min(0.5f, m_mysteryWaveParam));
            radius = radius2 * (1.0f - mix) + radius * (mix);
        }

        // Android TV: Clamp coordinates to screen bounds
        m_wave1Vertices[i].x = std::max(-2.0f, std::min(2.0f, radius * cosf(angle) * m_aspectY + m_waveX));
        m_wave1Vertices[i].y = std::max(-2.0f, std::min(2.0f, radius * sinf(angle) * m_aspectX + m_waveY));
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
