#include "TimeKeeper.hpp"

#include <algorithm>
#include <random>

namespace libprojectM {

TimeKeeper::TimeKeeper(double presetDuration, double smoothDuration, double hardcutDuration, double easterEgg)
    : m_easterEgg(easterEgg)
    , m_presetDuration(presetDuration)
    , m_softCutDuration(smoothDuration)
    , m_hardCutDuration(hardcutDuration)
{
    UpdateTimers();
}

void TimeKeeper::SetFrameTime(double secondsSinceStart)
{
    m_userSpecifiedTime = secondsSinceStart;
}

double TimeKeeper::GetFrameTime() const
{
    return m_currentTime;
}

void TimeKeeper::UpdateTimers()
{
    double currentFrameTime{m_userSpecifiedTime};

    if (m_userSpecifiedTime < 0.0)
    {
        auto currentTime = std::chrono::high_resolution_clock::now();
        currentFrameTime = std::chrono::duration<double>(currentTime - m_startTime).count();
    }

    // Android TV: Clamp extreme time deltas that could cause instability
    double timeDelta = currentFrameTime - m_currentTime;
    if (timeDelta < 0.0 || timeDelta > 1.0) // More than 1 second indicates something wrong
    {
        timeDelta = 0.016667; // Default to 60fps timing
    }
    
    m_secondsSinceLastFrame = timeDelta;
    m_currentTime = currentFrameTime;
    m_presetFrameA++;
    m_presetFrameB++;
}

void TimeKeeper::StartPreset()
{
    m_isSmoothing = false;
    m_presetTimeA = m_currentTime;
    m_presetFrameA = 1;
    m_presetDurationA = sampledPresetDuration();
}

void TimeKeeper::StartSmoothing()
{
    m_isSmoothing = true;
    m_presetTimeB = m_currentTime;
    m_presetFrameB = 1;
    m_presetDurationB = sampledPresetDuration();
}

void TimeKeeper::EndSmoothing()
{
    m_isSmoothing = false;
    m_presetTimeA = m_presetTimeB;
    m_presetFrameA = m_presetFrameB;
    m_presetDurationA = m_presetDurationB;
}

bool TimeKeeper::CanHardCut()
{
    return (m_currentTime - m_presetTimeA) > m_hardCutDuration;
}

double TimeKeeper::SmoothRatio()
{
    return (m_currentTime - m_presetTimeB) / m_softCutDuration;
}

bool TimeKeeper::IsSmoothing()
{
    return m_isSmoothing;
}

double TimeKeeper::GetRunningTime()
{
    return m_currentTime;
}

double TimeKeeper::PresetProgressA()
{
    if (m_isSmoothing)
    {
        return 1.0;
    }

    return std::min((m_currentTime - m_presetTimeA) / m_presetDurationA, 1.0);
}

double TimeKeeper::PresetProgressB()
{
    return std::min((m_currentTime - m_presetTimeB) / m_presetDurationB, 1.0);
}

int TimeKeeper::PresetFrameB()
{
    return m_presetFrameB;
}

int TimeKeeper::PresetFrameA()
{
    return m_presetFrameA;
}

double TimeKeeper::PresetTimeB()
{
    return m_presetTimeB;
}

double TimeKeeper::PresetTimeA()
{
    return m_presetTimeA;
}

double TimeKeeper::sampledPresetDuration()
{
    if (m_easterEgg < 0.001)
    {
        return m_presetDuration;
    }

    // Android TV: Clamp easter egg parameter to prevent extreme durations
    double clampedEasterEgg = std::max(0.0, std::min(m_easterEgg, m_presetDuration * 0.5));
    
    std::normal_distribution<double> gaussianDistribution(m_presetDuration, clampedEasterEgg);
    double duration = gaussianDistribution(m_randomGenerator);
    
    // Android TV: Clamp duration to reasonable bounds (1s to 10x preset duration)
    return std::max(1.0, std::min(duration, m_presetDuration * 10.0));
}

} // namespace libprojectM
