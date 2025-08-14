#include "Waveforms/Milkdrop2077WaveStar.hpp"

#include "PerFrameContext.hpp"

#include <cmath>

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {

bool Milkdrop2077WaveStar::IsLoop()
{
    return true;
}

void Milkdrop2077WaveStar::GenerateVertices(const PresetState& presetState,
                                            const PerFrameContext&)
{
    // Android TV: Conservative vertex limit for star patterns
    m_samples = std::min(Audio::WaveformSamples / 2, 48);

    m_wave1Vertices.resize(m_samples + 1);

    int const sampleOffset = std::max(0, (Audio::WaveformSamples - m_samples) / 2);

    float const invertedSamplesMinusOne = 1.0f / static_cast<float>(std::max(1, m_samples - 1));
    float const tenthSamples = static_cast<float>(m_samples) * 0.1f;

    for (int sample = 0; sample < m_samples; sample++)
    {
        // Android TV: Safe array access and clamped parameters
        int dataIndex = std::min(sample + sampleOffset, Audio::WaveformSamples - 1);
        float clampedPcmData = std::max(-1.0f, std::min(1.0f, m_pcmDataR[dataIndex]));
        float clampedMysteryParam = std::max(-0.5f, std::min(0.5f, m_mysteryWaveParam));
        
        float radius = 0.7f + 0.4f * clampedPcmData + clampedMysteryParam;
        float const angle = static_cast<float>(sample) * invertedSamplesMinusOne * 6.28f + presetState.renderContext.time * 0.2f;
        
        // Android TV: Simplified radius calculation to avoid division by potentially small values
        if (static_cast<float>(sample) < tenthSamples && radius > 0.1f)
        {
            float mix = static_cast<float>(sample) / std::max(1.0f, tenthSamples);
            mix = 0.5f - 0.5f * cosf(mix * 3.1416f);
            
            // Android TV: Safe array access for radius2 calculation
            int radius2Index = std::max(0, std::min(sample + m_samples - sampleOffset, Audio::WaveformSamples - 1));
            float clampedPcmData2 = std::max(-1.0f, std::min(1.0f, m_pcmDataR[radius2Index]));
            float const radius2 = 0.5f + 0.4f * clampedPcmData2 + clampedMysteryParam;
            radius = radius2 * (1.0f - mix) + radius * mix;
        }
        
        // Android TV: Clamp radius and coordinates
        radius = std::max(0.1f, std::min(2.0f, radius));
        m_wave1Vertices[sample].x = std::max(-2.0f, std::min(2.0f, radius * cosf(angle) * m_aspectY + m_waveX));
        m_wave1Vertices[sample].y = std::max(-2.0f, std::min(2.0f, radius * sinf(angle) * m_aspectX + m_waveY));
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
