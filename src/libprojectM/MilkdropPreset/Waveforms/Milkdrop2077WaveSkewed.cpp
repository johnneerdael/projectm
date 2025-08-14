#include "Waveforms/Milkdrop2077WaveSkewed.hpp"

#include "PerFrameContext.hpp"

#include <algorithm>
#include <cmath>

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {

void Milkdrop2077WaveSkewed::GenerateVertices(const PresetState& presetState,
                                              const PerFrameContext& presetPerFrameContext)
{
    // Android TV: Conservative vertex limit for skewed patterns
    m_samples = std::min(Audio::WaveformSamples / 2, 48);

    m_wave1Vertices.resize(m_samples);

    // Android TV: Safe alpha calculation with bounds checking
    float alpha = 0.0f;
    if (presetPerFrameContext.wave_a)
    {
        alpha = static_cast<float>(*presetPerFrameContext.wave_a) * 1.25f;
        if (presetState.modWaveAlphaByvolume)
        {
            alpha *= std::max(0.0f, std::min(2.0f, presetState.audioData.vol)); // Clamp volume
        }
    }
    alpha = std::max(0.0f, std::min(1.0f, alpha));

    for (size_t i = 0; i < static_cast<size_t>(m_samples); i++)
    {
        // Android TV: Safe array access and parameter clamping
        int dataIndex = std::min(static_cast<int>(i), Audio::WaveformSamples - 1);
        int dataIndex32 = std::min(static_cast<int>(i) + 32, Audio::WaveformSamples - 1);
        
        float clampedPcmDataR = std::max(-1.0f, std::min(1.0f, m_pcmDataR[dataIndex]));
        float clampedPcmDataL = std::max(-1.0f, std::min(1.0f, m_pcmDataL[dataIndex32]));
        float clampedMysteryParam = std::max(-0.5f, std::min(0.5f, m_mysteryWaveParam));
        float clampedTime = std::fmod(presetState.renderContext.time, 60.0f);
        
        float rad = 0.63f + 0.23f * clampedPcmDataR + clampedMysteryParam;
        float ang = clampedPcmDataL * 0.9f + clampedTime * 3.3f;
        
        // Android TV: Clamp radius and coordinate calculations
        rad = std::max(0.1f, std::min(2.0f, rad));
        m_wave1Vertices[i].x = std::max(-2.0f, std::min(2.0f, rad * cosf(ang + alpha) * m_aspectY + m_waveX));
        m_wave1Vertices[i].y = std::max(-2.0f, std::min(2.0f, rad * sinf(ang) * m_aspectX + m_waveY));
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
