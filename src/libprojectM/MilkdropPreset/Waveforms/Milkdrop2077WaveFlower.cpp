#include "Waveforms/Milkdrop2077WaveFlower.hpp"

#include "PerFrameContext.hpp"

#include <cmath>

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {

bool Milkdrop2077WaveFlower::IsLoop()
{
    return true;
}

void Milkdrop2077WaveFlower::GenerateVertices(const PresetState& presetState,
                                              const PerFrameContext&)
{
    // Android TV: Conservative vertex limit for complex flower patterns
    m_samples = std::min(Audio::WaveformSamples / 2, 32);

    m_wave1Vertices.resize(m_samples + 1);

    int const sampleOffset = std::max(0, (Audio::WaveformSamples - m_samples) / 2);

    float const invertedSamplesMinusOne = 1.0f / static_cast<float>(std::max(1, m_samples - 1));
    float const tenthSamples = static_cast<float>(m_samples) * 0.1f;

    for (int sample = 0; sample < m_samples; sample++)
    {
        // Android TV: Safe array access and parameter clamping
        int dataIndex = std::min(sample + sampleOffset, Audio::WaveformSamples - 1);
        float clampedPcmData = std::max(-1.0f, std::min(1.0f, m_pcmDataR[dataIndex]));
        float clampedMysteryParam = std::max(-0.5f, std::min(0.5f, m_mysteryWaveParam));
        
        float radius = 0.7f + 0.7f * clampedPcmData + clampedMysteryParam;
        float angle = static_cast<float>(sample) * invertedSamplesMinusOne * 6.28f + presetState.renderContext.time * 0.2f;
        
        // Android TV: Simplified flower calculation to avoid division by potentially small radius
        if (static_cast<float>(sample) < tenthSamples && radius > 0.1f)
        {
            float mix = static_cast<float>(sample) / std::max(1.0f, tenthSamples);
            // Flower pattern - simplified calculation
            mix = 0.7f - 0.7f * cosf(mix * 3.1416f);
            
            // Android TV: Safe array access for radius2
            int radius2Index = std::max(0, std::min(sample + m_samples - sampleOffset, Audio::WaveformSamples - 1));
            float clampedPcmData2 = std::max(-1.0f, std::min(1.0f, m_pcmDataR[radius2Index]));
            float const radius2 = 0.7f + 0.7f * clampedPcmData2 + clampedMysteryParam;
            radius = radius2 * (1.0f - mix) + radius * mix * 0.25f;
        }

        // Android TV: Clamp radius and simplify complex trigonometric operations
        radius = std::max(0.1f, std::min(1.5f, radius));
        float clampedAngle = std::fmod(angle * 3.1416f, 6.28f); // Prevent extreme angle values
        float clampedTime = std::fmod(presetState.renderContext.time, 60.0f); // Prevent time overflow
        
        m_wave1Vertices[sample].x = std::max(-2.0f, std::min(2.0f, radius * cosf(clampedAngle) * m_aspectY / 1.5f + m_waveX * cosf(3.1416f)));
        m_wave1Vertices[sample].y = std::max(-2.0f, std::min(2.0f, radius * sinf(clampedAngle - clampedTime / 3.0f) * m_aspectX / 1.5f + m_waveY * cosf(3.1416f)));
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
