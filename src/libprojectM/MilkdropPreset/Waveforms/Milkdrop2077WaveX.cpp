#include "Waveforms/Milkdrop2077WaveX.hpp"

#include "PresetState.hpp"

namespace libprojectM {
namespace MilkdropPreset {
namespace Waveforms {


void Milkdrop2077WaveX::GenerateVertices(const PresetState& presetState, const PerFrameContext&)
{
    // Android TV: Conservative vertex limit for dual-waveform patterns
    m_samples = std::min(Audio::WaveformSamples / 2, 48);

    // Android TV: More conservative viewport scaling
    if (m_samples > presetState.renderContext.viewportSizeX / 6)
    {
        m_samples = std::max(24, presetState.renderContext.viewportSizeX / 8);
    }

    m_wave1Vertices.resize(m_samples);
    m_wave2Vertices.resize(m_samples);

    // Android TV: Clamp mystery parameter to prevent extreme edge calculations
    float clampedMysteryParam = std::max(-1.0f, std::min(1.0f, m_mysteryWaveParam));
    ClipWaveformEdges(-0.75f + clampedMysteryParam * 3.15f);

    for (int i = 0; i < m_samples; i++)
    {
        // Android TV: Safe array access with bounds checking
        int dataIndex = std::min(i + m_sampleOffset, Audio::WaveformSamples - 1);
        float clampedPcmDataL = std::max(-0.5f, std::min(0.5f, m_pcmDataL[dataIndex])); // More conservative clamping
        
        m_wave1Vertices[i].x = std::max(-2.0f, std::min(2.0f, m_edgeX + m_distanceX * static_cast<float>(i) + m_perpetualDX * 0.35f * clampedPcmDataL));
        m_wave1Vertices[i].y = std::max(-2.0f, std::min(2.0f, m_edgeY + m_distanceY * static_cast<float>(i) + m_perpetualDY * 0.35f * clampedPcmDataL));
    }

    ClipWaveformEdges(0.75f + clampedMysteryParam * 3.15f);

    for (int i = 0; i < m_samples; i++)
    {
        // Android TV: Safe array access for right channel
        int dataIndex = std::min(i + m_sampleOffset, Audio::WaveformSamples - 1);
        float clampedPcmDataR = std::max(-0.5f, std::min(0.5f, m_pcmDataR[dataIndex]));
        
        m_wave2Vertices[i].x = std::max(-2.0f, std::min(2.0f, m_edgeX + m_distanceX * static_cast<float>(i) + m_perpetualDX * 0.35f * clampedPcmDataR));
        m_wave2Vertices[i].y = std::max(-2.0f, std::min(2.0f, m_edgeY + m_distanceY * static_cast<float>(i) + m_perpetualDY * 0.35f * clampedPcmDataR));
    }
}

} // namespace Waveforms
} // namespace MilkdropPreset
} // namespace libprojectM
